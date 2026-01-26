package com.ezcorp.fammoney.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ezcorp.fammoney.data.model.LearnedMerchantRule

/**
 * 팸머니 앱의 로컬 Room 데이터베이스
 *
 * 포함 테이블:
 *   - learned_merchant_rules: 학습된 사용처 규칙 (signature 기반)
 *
 * 정책 3 준수: LearnedRule 저장/조회로 우선순위 기반 사용처 결정
 * 정책 2 준수: 사용자 수정 학습을 통한 재발 방지
 */
@Database(
    entities = [LearnedMerchantRule::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * 학습된 사용처 규칙 DAO
     */
    abstract fun learnedMerchantRuleDao(): LearnedMerchantRuleDao

    companion object {
        private const val DATABASE_NAME = "fammoney_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
