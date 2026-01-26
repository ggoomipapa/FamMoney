package com.ezcorp.fammoney.di

import android.content.Context
import com.ezcorp.fammoney.R
import com.ezcorp.fammoney.data.local.AppDatabase
import com.ezcorp.fammoney.data.local.LearnedMerchantRuleDao
import com.ezcorp.fammoney.service.ExchangeRateService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return Firebase.firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging {
        return FirebaseMessaging.getInstance()
    }

    @Provides
    @Singleton
    fun provideGoogleSignInClient(
        @ApplicationContext context: Context
    ): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    // New providers for OkHttpClient and Json
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        }
    }

    // Provide ExchangeRateService
    @Provides
    @Singleton
    fun provideExchangeRateService(
        okHttpClient: OkHttpClient,
        json: Json
    ): ExchangeRateService {
        return ExchangeRateService(okHttpClient, json)
    }

    // ==================== Room Database ====================

    /**
     * Room 데이터베이스 인스턴스 제공
     * 정책 3: LearnedRule 저장/조회를 위한 로컬 DB
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    /**
     * LearnedMerchantRule DAO 제공
     * 정책 3: signature 기반 사용처 학습
     */
    @Provides
    @Singleton
    fun provideLearnedMerchantRuleDao(
        database: AppDatabase
    ): LearnedMerchantRuleDao {
        return database.learnedMerchantRuleDao()
    }

    // Note: NotificationParser, MerchantCandidateExtractor, MerchantNormalizer,
    // LearnedMerchantRuleRepository use @Inject constructor with @Singleton,
    // so Hilt automatically provides them
}
