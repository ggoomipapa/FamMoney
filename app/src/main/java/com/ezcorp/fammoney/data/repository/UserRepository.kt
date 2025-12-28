package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.Group
import com.ezcorp.fammoney.data.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val usersCollection = firestore.collection("users")
    private val groupsCollection = firestore.collection("groups")

    suspend fun createUser(user: User): Result<String> {
        return try {
            val docRef = usersCollection.add(user.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            usersCollection.document(user.id).set(user.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserByDeviceId(deviceId: String): User? {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("deviceId", deviceId)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { User.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getUserFlow(userId: String): Flow<User?> = callbackFlow {
        val listener = usersCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val user = snapshot?.data?.let { User.fromMap(snapshot.id, it) }
                trySend(user)
            }

        awaitClose { listener.remove() }
    }

    suspend fun updateSelectedBanks(userId: String, bankIds: List<String>): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("selectedBankIds", bankIds)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroup(group: Group): Result<String> {
        return try {
            val inviteCode = generateInviteCode()
            val groupWithCode = group.copy(inviteCode = inviteCode)
            val docRef = groupsCollection.add(groupWithCode.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupByInviteCode(inviteCode: String): Group? {
        return try {
            val snapshot = groupsCollection
                .whereEqualTo("inviteCode", inviteCode)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { Group.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getGroupFlow(groupId: String): Flow<Group?> = callbackFlow {
        val listener = groupsCollection.document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val group = snapshot?.data?.let { Group.fromMap(snapshot.id, it) }
                trySend(group)
            }

        awaitClose { listener.remove() }
    }

    fun getGroupMembersFlow(groupId: String): Flow<List<User>> = callbackFlow {
        val listener = usersCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val users = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { User.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(users)
            }

        awaitClose { listener.remove() }
    }

    suspend fun joinGroup(userId: String, groupId: String): Result<Unit> {
        return try {
            firestore.runBatch { batch ->
                batch.update(usersCollection.document(userId), "groupId", groupId)
                batch.update(
                    groupsCollection.document(groupId),
                    "memberIds",
                    FieldValue.arrayUnion(userId)
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserByAuthUid(authUid: String): User? {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("authUid", authUid)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { User.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateFcmToken(userId: String, token: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("fcmToken", token)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNotificationSettings(
        userId: String,
        notifyGroupOnTransaction: Boolean,
        receiveGroupNotifications: Boolean
    ): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update(
                    mapOf(
                        "notifyGroupOnTransaction" to notifyGroupOnTransaction,
                        "receiveGroupNotifications" to receiveGroupNotifications
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAuthInfo(
        userId: String,
        authUid: String,
        email: String,
        isAnonymous: Boolean
    ): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update(
                    mapOf(
                        "authUid" to authUid,
                        "email" to email,
                        "isAnonymous" to isAnonymous
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupMembersWithNotifications(groupId: String, excludeUserId: String): List<User> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("receiveGroupNotifications", true)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { doc -> doc.data?.let { User.fromMap(doc.id, it) } }
                .filter { it.id != excludeUserId && !it.fcmToken.isNullOrBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateChildIncomeEnabled(groupId: String, enabled: Boolean): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("childIncomeEnabled", enabled)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSharingScope(
        userId: String,
        shareFromDate: com.google.firebase.Timestamp?,
        hiddenTransactionIds: List<String>
    ): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update(
                    mapOf(
                        "shareFromDate" to shareFromDate,
                        "hiddenTransactionIds" to hiddenTransactionIds
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addHiddenTransaction(userId: String, transactionId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("hiddenTransactionIds", FieldValue.arrayUnion(transactionId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeHiddenTransaction(userId: String, transactionId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("hiddenTransactionIds", FieldValue.arrayRemove(transactionId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    // 가계부 이름 변경
    suspend fun updateGroupName(groupId: String, newName: String): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("name", newName)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 사용자 이름 변경
    suspend fun updateUserName(userId: String, newName: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("name", newName)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자 프로필 업데이트 (닉네임, 실명, 별칭)
     * 입금 알림 매칭을 위한 이름 정보 관리
     */
    suspend fun updateUserProfile(
        userId: String,
        name: String,
        realName: String,
        aliasNames: List<String>
    ): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update(
                    mapOf(
                        "name" to name,
                        "realName" to realName,
                        "aliasNames" to aliasNames
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자 실명만 업데이트
     */
    suspend fun updateUserRealName(userId: String, realName: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("realName", realName)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자 별칭 추가
     */
    suspend fun addUserAlias(userId: String, alias: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("aliasNames", FieldValue.arrayUnion(alias))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자 별칭 제거
     */
    suspend fun removeUserAlias(userId: String, alias: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("aliasNames", FieldValue.arrayRemove(alias))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 멤버 강퇴
    suspend fun removeMemberFromGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            firestore.runBatch { batch ->
                // 그룹에서 멤버 제거
                batch.update(
                    groupsCollection.document(groupId),
                    "memberIds",
                    FieldValue.arrayRemove(userId)
                )
                // 사용자의 groupId 제거
                batch.update(
                    usersCollection.document(userId),
                    mapOf(
                        "groupId" to "",
                        "groupIds" to FieldValue.arrayRemove(groupId)
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 통장 잔고 설정
    suspend fun updateBalanceSettings(
        groupId: String,
        balanceEnabled: Boolean,
        initialBalance: Long
    ): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update(
                    mapOf(
                        "balanceEnabled" to balanceEnabled,
                        "initialBalance" to initialBalance,
                        "currentBalance" to initialBalance
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 현재 잔고 업데이트
    suspend fun updateCurrentBalance(groupId: String, newBalance: Long): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("currentBalance", newBalance)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 그룹 설정 업데이트
    suspend fun updateGroupSettings(
        groupId: String,
        cashManagementEnabled: Boolean,
        highAmountThreshold: Long,
        childIncomeEnabled: Boolean
    ): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update(
                    mapOf(
                        "cashManagementEnabled" to cashManagementEnabled,
                        "highAmountThreshold" to highAmountThreshold,
                        "childIncomeEnabled" to childIncomeEnabled
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 사용자의 활성 가계부 변경
    suspend fun setActiveGroup(userId: String, groupId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update(
                    mapOf(
                        "activeGroupId" to groupId,
                        "groupId" to groupId
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 사용자에게 새 가계부 추가
    suspend fun addGroupToUser(userId: String, groupId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("groupIds", FieldValue.arrayUnion(groupId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 구독 상태 업데이트
    suspend fun updateSubscription(groupId: String, subscriptionType: String): Result<Unit> {
        return try {
            val maxMembers = when (subscriptionType) {
                "connect_plus" -> 999 // 무제한
                "connect", "forever" -> 10
                else -> 1
            }
            groupsCollection.document(groupId)
                .update(
                    mapOf(
                        "subscriptionType" to subscriptionType,
                        "maxMembers" to maxMembers
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
