package com.ezcorp.fammoney.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class BillingState(
    val isConnected: Boolean = false,
    val availableProducts: List<ProductDetails> = emptyList(),
    val purchasedProducts: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        // 구독 상품 ID (Google Play Console에서 설정한 ID와 일치해야 함)
        const val PRODUCT_CONNECT_MONTHLY = "fammoney_connect_monthly"
        const val PRODUCT_CONNECT_YEARLY = "fammoney_connect_yearly"
        const val PRODUCT_CONNECT_PLUS_MONTHLY = "fammoney_connect_plus_monthly"
        const val PRODUCT_CONNECT_PLUS_YEARLY = "fammoney_connect_plus_yearly"

        // 평생 이용권 (일회성 결제)
        const val PRODUCT_FOREVER = "fammoney_forever"
    }

    private val _billingState = MutableStateFlow(BillingState())
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var onPurchaseComplete: ((Boolean, String?) -> Unit)? = null

    init {
        startConnection()
    }

    fun startConnection() {
        _billingState.value = _billingState.value.copy(isLoading = true)

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingState.value = _billingState.value.copy(
                        isConnected = true,
                        isLoading = false,
                        error = null
                    )
                    queryProducts()
                    queryPurchases()
                } else {
                    _billingState.value = _billingState.value.copy(
                        isConnected = false,
                        isLoading = false,
                        error = "결제 서비스 연결 실패: ${billingResult.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingState.value = _billingState.value.copy(
                    isConnected = false,
                    error = "결제 서비스 연결 끊김"
                )
            }
        })
    }

    private fun queryProducts() {
        // 구독 상품 쿼리
        val subscriptionParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_CONNECT_MONTHLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_CONNECT_YEARLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_CONNECT_PLUS_MONTHLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_CONNECT_PLUS_YEARLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(subscriptionParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val currentProducts = _billingState.value.availableProducts.toMutableList()
                currentProducts.addAll(productDetailsList)
                _billingState.value = _billingState.value.copy(availableProducts = currentProducts)
            }
        }

        // 일회성 결제 상품 쿼리 (평생 이용권)
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_FOREVER)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val currentProducts = _billingState.value.availableProducts.toMutableList()
                currentProducts.addAll(productDetailsList)
                _billingState.value = _billingState.value.copy(availableProducts = currentProducts)
            }
        }
    }

    private fun queryPurchases() {
        // 구독 구매 확인
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productIds = purchasesList.flatMap { it.products }
                _billingState.value = _billingState.value.copy(
                    purchasedProducts = _billingState.value.purchasedProducts + productIds
                )

                // 미확인 구매 처리
                purchasesList.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }

        // 일회성 구매 확인 (평생 이용권)
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productIds = purchasesList.flatMap { it.products }
                _billingState.value = _billingState.value.copy(
                    purchasedProducts = _billingState.value.purchasedProducts + productIds
                )

                purchasesList.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _billingState.value = _billingState.value.copy(
                    error = "구매 확인 실패: ${billingResult.debugMessage}"
                )
            }
        }
    }

    /**
     * 셀머니 커넥트 월간 구독 시작
     */
    fun purchaseConnectMonthly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        launchPurchase(activity, PRODUCT_CONNECT_MONTHLY, BillingClient.ProductType.SUBS, onComplete)
    }

    /**
     * 셀머니 커넥트 연간 구독 시작
     */
    fun purchaseConnectYearly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        launchPurchase(activity, PRODUCT_CONNECT_YEARLY, BillingClient.ProductType.SUBS, onComplete)
    }

    /**
     * 셀머니 커넥트+ 월간 구독 시작
     */
    fun purchaseConnectPlusMonthly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        launchPurchase(activity, PRODUCT_CONNECT_PLUS_MONTHLY, BillingClient.ProductType.SUBS, onComplete)
    }

    /**
     * 셀머니 커넥트+ 연간 구독 시작
     */
    fun purchaseConnectPlusYearly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        launchPurchase(activity, PRODUCT_CONNECT_PLUS_YEARLY, BillingClient.ProductType.SUBS, onComplete)
    }

    /**
     * 셀머니 포에버 (평생 이용권) 구매
     */
    fun purchaseForever(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        launchPurchase(activity, PRODUCT_FOREVER, BillingClient.ProductType.INAPP, onComplete)
    }

    private fun launchPurchase(
        activity: Activity,
        productId: String,
        productType: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (!_billingState.value.isConnected) {
            onComplete(false, "결제 서비스에 연결되지 않았습니다.")
            return
        }

        val productDetails = _billingState.value.availableProducts.find { it.productId == productId }

        if (productDetails == null) {
            onComplete(false, "상품 정보를 찾을 수 없습니다. 나중에 다시 시도해주세요.")
            return
        }

        onPurchaseComplete = onComplete

        val productDetailsParamsList = if (productType == BillingClient.ProductType.SUBS) {
            // 구독 상품의 경우 offer 선택 필요
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                onComplete(false, "구독 정보를 찾을 수 없습니다")
                return
            }
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
            // 일회성 결제
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            onPurchaseComplete = null
            onComplete(false, "결제 시작 실패: ${billingResult.debugMessage}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        // 구매 확인
                acknowledgePurchase(purchase)

                        // 구매한 상품 목록 업데이트
                val updatedProducts = _billingState.value.purchasedProducts.toMutableList()
                        updatedProducts.addAll(purchase.products)
                        _billingState.value = _billingState.value.copy(
                            purchasedProducts = updatedProducts.distinct()
                        )

                        // 구매 완료 콜백
                val productId = purchase.products.firstOrNull() ?: ""
                        onPurchaseComplete?.invoke(true, productId)
                        onPurchaseComplete = null
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                onPurchaseComplete?.invoke(false, "결제가 취소되었습니다.")
                onPurchaseComplete = null
            }
            else -> {
                onPurchaseComplete?.invoke(false, "결제 실패: ${billingResult.debugMessage}")
                onPurchaseComplete = null
            }
        }
    }

    /**
     * 현재 구독 상태 확인
     */
    fun getSubscriptionType(): String {
        val purchased = _billingState.value.purchasedProducts
        return when {
            purchased.contains(PRODUCT_FOREVER) -> "forever"
            purchased.contains(PRODUCT_CONNECT_PLUS_MONTHLY) ||
            purchased.contains(PRODUCT_CONNECT_PLUS_YEARLY) -> "connect_plus"
            purchased.contains(PRODUCT_CONNECT_MONTHLY) ||
            purchased.contains(PRODUCT_CONNECT_YEARLY) -> "connect"
            else -> "free"
        }
    }

    /**
     * 평생 이용권 보유 여부 확인
     */
    fun hasForeverPlan(): Boolean {
        return _billingState.value.purchasedProducts.contains(PRODUCT_FOREVER)
    }

    /**
     * 유료 구독 보유 여부 확인
     */
    fun hasPaidSubscription(): Boolean {
        return getSubscriptionType() != "free"
    }

    fun refreshPurchases() {
        if (_billingState.value.isConnected) {
            queryPurchases()
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}
