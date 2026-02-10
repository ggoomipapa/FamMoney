package com.ezcorp.fammoney.data.repository

import android.content.Intent
import com.ezcorp.fammoney.util.AppLogger
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val googleSignInClient: GoogleSignInClient
) {
    companion object {
        private const val TAG = "AuthRepo"
    }

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isLoggedIn: Boolean
        get() = currentUser != null

    val isAnonymous: Boolean
        get() = currentUser?.isAnonymous == true

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        AppLogger.d(TAG, "authStateFlow: 인증 상태 감시 시작")
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            AppLogger.i(TAG, "authStateFlow: 인증 상태 변경 uid=${user?.uid}, isAnonymous=${user?.isAnonymous}")
            trySend(user)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose {
            AppLogger.d(TAG, "authStateFlow: 인증 상태 감시 종료")
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    suspend fun signInAnonymously(): Result<FirebaseUser> {
        AppLogger.d(TAG, "signInAnonymously: 익명 로그인 시도")
        return try {
            val result = firebaseAuth.signInAnonymously().await()
            result.user?.let {
                AppLogger.i(TAG, "signInAnonymously 성공: uid=${it.uid}")
                Result.success(it)
            } ?: Result.failure<FirebaseUser>(Exception("익명 로그인 실패")).also {
                AppLogger.e(TAG, "signInAnonymously 실패: user가 null")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "signInAnonymously 실패", e)
            Result.failure(e)
        }
    }

    fun getGoogleSignInIntent(): Intent {
        AppLogger.d(TAG, "getGoogleSignInIntent: Google 로그인 인텐트 요청")
        return googleSignInClient.signInIntent
    }

    suspend fun handleGoogleSignInResult(data: Intent?): Result<FirebaseUser> {
        AppLogger.d(TAG, "handleGoogleSignInResult: Google 로그인 결과 처리")
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            AppLogger.d(TAG, "handleGoogleSignInResult: Google 계정 획득 email=${account.email}")
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            linkOrSignInWithCredential(credential)
        } catch (e: ApiException) {
            AppLogger.e(TAG, "handleGoogleSignInResult 실패: statusCode=${e.statusCode}", e)
            Result.failure(Exception("Google 로그???�패: ${e.statusCode}"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "handleGoogleSignInResult 실패", e)
            Result.failure(e)
        }
    }

    private suspend fun linkOrSignInWithCredential(credential: AuthCredential): Result<FirebaseUser> {
        AppLogger.d(TAG, "linkOrSignInWithCredential: 자격증명으로 로그인/연결 시도")
        return try {
            val currentUser = firebaseAuth.currentUser

            if (currentUser != null && currentUser.isAnonymous) {
                AppLogger.d(TAG, "linkOrSignInWithCredential: 익명 계정 연결 시도 uid=${currentUser.uid}")
                val result = currentUser.linkWithCredential(credential).await()
                result.user?.let {
                    AppLogger.i(TAG, "linkOrSignInWithCredential 성공: 익명→Google 연결 uid=${it.uid}")
                    Result.success(it)
                } ?: Result.failure<FirebaseUser>(Exception("계정 연결 실패")).also {
                    AppLogger.e(TAG, "linkOrSignInWithCredential 실패: 연결 후 user가 null")
                }
            } else {
                AppLogger.d(TAG, "linkOrSignInWithCredential: 자격증명 로그인 시도")
                val result = firebaseAuth.signInWithCredential(credential).await()
                result.user?.let {
                    AppLogger.i(TAG, "linkOrSignInWithCredential 성공: 로그인 uid=${it.uid}")
                    Result.success(it)
                } ?: Result.failure<FirebaseUser>(Exception("로그인 실패")).also {
                    AppLogger.e(TAG, "linkOrSignInWithCredential 실패: 로그인 후 user가 null")
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("already in use") == true) {
                AppLogger.w(TAG, "linkOrSignInWithCredential: 이미 사용 중인 계정, 기존 익명 삭제 후 재시도")
                try {
                    firebaseAuth.currentUser?.delete()?.await()
                    val result = firebaseAuth.signInWithCredential(credential).await()
                    result.user?.let {
                        AppLogger.i(TAG, "linkOrSignInWithCredential 성공: 재시도 로그인 uid=${it.uid}")
                        Result.success(it)
                    } ?: Result.failure<FirebaseUser>(Exception("로그인 실패")).also {
                        AppLogger.e(TAG, "linkOrSignInWithCredential 실패: 재시도 후 user가 null")
                    }
                } catch (deleteError: Exception) {
                    AppLogger.e(TAG, "linkOrSignInWithCredential 실패: 익명 계정 삭제 실패", deleteError)
                    Result.failure(Exception("?��? ?�용 중인 계정?�니?? 기존 계정?�로 로그?�됩?�다."))
                }
            } else {
                AppLogger.e(TAG, "linkOrSignInWithCredential 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun signOut(): Result<Unit> {
        AppLogger.d(TAG, "signOut: 로그아웃 시도 uid=${currentUser?.uid}")
        return try {
            googleSignInClient.signOut().await()
            firebaseAuth.signOut()
            AppLogger.i(TAG, "signOut 성공")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "signOut 실패", e)
            Result.failure(e)
        }
    }

    fun getUserId(): String? {
        val uid = currentUser?.uid
        AppLogger.d(TAG, "getUserId: uid=$uid")
        return uid
    }

    fun getUserEmail(): String? {
        val email = currentUser?.email
        AppLogger.d(TAG, "getUserEmail: email=$email")
        return email
    }

    fun getUserDisplayName(): String? {
        val name = currentUser?.displayName
        AppLogger.d(TAG, "getUserDisplayName: name=$name")
        return name
    }
}
