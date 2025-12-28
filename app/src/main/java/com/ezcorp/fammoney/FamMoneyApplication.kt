package com.ezcorp.fammoney

import android.app.Application
import com.ezcorp.fammoney.util.AIFeatureConfig
import com.ezcorp.fammoney.util.DebugConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class FamMoneyApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // 디버그 빌드 여부 초기화 - 프리미엄 기능 잠금 해제용
        DebugConfig.initialize(BuildConfig.DEBUG)

        // AI 기능 Remote Config 초기화
        AIFeatureConfig.initialize()

        // 백그라운드에서 최신 설정 가져오기
        applicationScope.launch(Dispatchers.IO) {
            AIFeatureConfig.fetchAndActivate()
        }
    }
}
