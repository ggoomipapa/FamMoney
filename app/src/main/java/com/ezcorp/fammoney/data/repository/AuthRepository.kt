package com.ezcorp.fammoney.data.repository

import android.content.Intent
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
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isLoggedIn: Boolean
        get() = currentUser != null

    val isAnonymous: Boolean
        get() = currentUser?.isAnonymous == true

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    suspend fun signInAnonymously(): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInAnonymously().await()
            result.user?.let {
                Result.success(it)
            } ?: Result.failure(Exception("?�명 로그???�패"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun handleGoogleSignInResult(data: Intent?): Result<FirebaseUser> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            linkOrSignInWithCredential(credential)
        } catch (e: ApiException) {
            Result.failure(Exception("Google 로그???�패: ${e.statusCode}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun linkOrSignInWithCredential(credential: AuthCredential): Result<FirebaseUser> {
        return try {
            val currentUser = firebaseAuth.currentUser

            if (currentUser != null && currentUser.isAnonymous) {
                val result = currentUser.linkWithCredential(credential).await()
                result.user?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("계정 ?�결 ?�패"))
            } else {
                val result = firebaseAuth.signInWithCredential(credential).await()
                result.user?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("로그???�패"))
            }
        } catch (e: Exception) {
            if (e.message?.contains("already in use") == true) {
                try {
                    firebaseAuth.currentUser?.delete()?.await()
                    val result = firebaseAuth.signInWithCredential(credential).await()
                    result.user?.let {
                        Result.success(it)
                    } ?: Result.failure(Exception("로그???�패"))
                } catch (deleteError: Exception) {
                    Result.failure(Exception("?��? ?�용 중인 계정?�니?? 기존 계정?�로 로그?�됩?�다."))
                }
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            googleSignInClient.signOut().await()
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserId(): String? = currentUser?.uid

    fun getUserEmail(): String? = currentUser?.email

    fun getUserDisplayName(): String? = currentUser?.displayName
}
