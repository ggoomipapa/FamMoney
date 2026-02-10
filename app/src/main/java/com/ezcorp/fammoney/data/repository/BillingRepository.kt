package com.ezcorp.fammoney.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.ezcorp.fammoney.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BillingRepo"

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
        AppLogger.i(TAG, "init: initializing BillingRepository")
        startConnection()
    }

    fun startConnection() {
        AppLogger.d(TAG, "startConnection: connecting to billing service")
        _billingState.value = _billingState.value.copy(isLoading = true)

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    AppLogger.i(TAG, "onBillingSetupFinished: connected successfully")
                    _billingState.value = _billingState.value.copy(
                        isConnected = true,
                        isLoading = false,
                        error = null
                    )
                    queryProducts()
                    queryPurchases()
                } else {
                    AppLogger.e(TAG, "onBillingSetupFinished: failed - code=${billingResult.responseCode}, msg=${billingResult.debugMessage}", null)
                    _billingState.value = _billingState.value.copy(
                        isConnected = false,
                        isLoading = false,
                        error = "결제 서비스 연결 실패: ${billingResult.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                AppLogger.w(TAG, "onBillingServiceDisconnected: billing service disconnected")
                _billingState.value = _billingState.value.copy(
                    isConnected = false,
                    error = "결제 서비스 연결 끊김"
                )
            }
        })
    }

    private fun queryProducts() {
        AppLogger.d(TAG, "queryProducts: querying subscription products")
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
                AppLogger.d(TAG, "queryProducts: subscription products found=${productDetailsList.size}")
                val currentProducts = _billingState.value.availableProducts.toMutableList()
                currentProducts.addAll(productDetailsList)
                _billingState.value = _billingState.value.copy(availableProducts = currentProducts)
            } else {
                AppLogger.w(TAG, "queryProducts: subscription query failed - code=${billingResult.responseCode}")
            }
        }

        AppLogger.d(TAG, "queryProducts: querying in-app products")
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
                AppLogger.d(TAG, "queryProducts: in-app products found=${productDetailsList.size}")
                val currentProducts = _billingState.value.availableProducts.toMutableList()
                currentProducts.addAll(productDetailsList)
                _billingState.value = _billingState.value.copy(availableProducts = currentProducts)
            } else {
                AppLogger.w(TAG, "queryProducts: in-app query failed - code=${billingResult.responseCode}")
            }
        }
    }

    private fun queryPurchases() {
        AppLogger.d(TAG, "queryPurchases: checking subscription purchases")
        // 구독 구매 확인
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productIds = purchasesList.flatMap { it.products }
                AppLogger.d(TAG, "queryPurchases: found ${purchasesList.size} subscription purchases, productIds=$productIds")
                _billingState.value = _billingState.value.copy(
                    purchasedProducts = _billingState.value.purchasedProducts + productIds
                )

                // 미확인 구매 처리
                purchasesList.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        AppLogger.d(TAG, "queryPurchases: acknowledging unacknowledged subscription purchase")
                        acknowledgePurchase(purchase)
                    }
                }
            } else {
                AppLogger.w(TAG, "queryPurchases: subscription query failed - code=${billingResult.responseCode}")
            }
        }

        AppLogger.d(TAG, "queryPurchases: checking in-app purchases")
        // 일회성 구매 확인 (평생 이용권)
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productIds = purchasesList.flatMap { it.products }
                AppLogger.d(TAG, "queryPurchases: found ${purchasesList.size} in-app purchases, productIds=$productIds")
                _billingState.value = _billingState.value.copy(
                    purchasedProducts = _billingState.value.purchasedProducts + productIds
                )

                purchasesList.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        AppLogger.d(TAG, "queryPurchases: acknowledging unacknowledged in-app purchase")
                        acknowledgePurchase(purchase)
                    }
                }
            } else {
                AppLogger.w(TAG, "queryPurchases: in-app query failed - code=${billingResult.responseCode}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        AppLogger.d(TAG, "acknowledgePurchase: token=${purchase.purchaseToken.take(20)}...")
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                AppLogger.e(TAG, "acknowledgePurchase: failed - code=${billingResult.responseCode}, msg=${billingResult.debugMessage}", null)
                _billingState.value = _billingState.value.copy(
                    error = "구매 확인 실패: ${billingResult.debugMessage}"
                )
            } else {
                AppLogger.i(TAG, "acknowledgePurchase: success")
            }
        }
    }

    /**
     * 셀머니 커넥트 월간 구독 시작
     */
    fun purchaseConnectMonthly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        AppLogger.d(TAG, "purchaseConnectMonthly: launching purchase")
        launchPurchase(activity, PRODUCT_CONNECT_MONTHLY, BillingClient.ProductType.SUBS, onComplete)
    }

    /**
     * 셀머니 커넥트 연간 구독 시작
     */
    fun purchaseConnectYearly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        AppLogger.d(TAG, "purchaseConnectYearly: launching purchase")
        launchPurchase(activity, PRODUCT_CONNECT_YEARLY, BillingClient.ProductType.SUBS, onComplete)
    }

    /**
     * 셀머니 커넥트+ 월간 구독 시작
     */
    fun purchaseConnectPlusMonthly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        AppLogger.d(TAG, "purchaseConnectPlusMonthly: launching purchase")
        launchPurchase(activity, PRODUCT_CONNECT_PLUS_MONTHLY, BillingClient.ProductType.SUBS, onComplete)
    }

    /**
     * 셀머니 커넥트+ 연간 구독 시작
     */
    fun purchaseConnectPlusYearly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        AppLogger.d(TAG, "purchaseConnectPlusYearly: launching purchase")
        launchPurchase(activity, PRODUCT_CONNECT_PLUS_YEARLY, BillingClient.ProductType.SUBS, onComplete)
    }

    /**
     * 셀머니 포에버 (평생 이용권) 구매
     */
    fun purchaseForever(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        AppLogger.d(TAG, "purchaseForever: launching purchase")
        launchPurchase(activity, PRODUCT_FOREVER, BillingClient.ProductType.INAPP, onComplete)
    }

    private fun launchPurchase(
        activity: Activity,
        productId: String,
        productType: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        AppLogger.d(TAG, "launchPurchase: productId=$productId, productType=$productType")

        if (!_billingState.value.isConnected) {
            AppLogger.w(TAG, "launchPurchase: billing service not connected")
            onComplete(false, "결제 서비스에 연결되지 않았습니다.")
            return
        }

        val productDetails = _billingState.value.availableProducts.find { it.productId == productId }

        if (productDetails == null) {
            AppLogger.w(TAG, "launchPurchase: product details not found for $productId")
            onComplete(false, "상품 정보를 찾을 수 없습니다. 나중에 다시 시도해주세요.")
            return
        }

        onPurchaseComplete = onComplete

        val productDetailsParamsList = if (productType == BillingClient.ProductType.SUBS) {
            // 구독 상품의 경우 offer 선택 필요
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                AppLogger.w(TAG, "launchPurchase: no offer token found for subscription $productId")
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
            AppLogger.e(TAG, "launchPurchase: billing flow failed - code=${billingResult.responseCode}, msg=${billingResult.debugMessage}", null)
            onPurchaseComplete = null
            onComplete(false, "결제 시작 실패: ${billingResult.debugMessage}")
        } else {
            AppLogger.i(TAG, "launchPurchase: billing flow launched successfully for $productId")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        AppLogger.d(TAG, "onPurchasesUpdated: responseCode=${billingResult.responseCode}, purchases=${purchases?.size ?: 0}")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        AppLogger.i(TAG, "onPurchasesUpdated: purchase completed - products=${purchase.products}")
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
                AppLogger.i(TAG, "onPurchasesUpdated: user canceled purchase")
                onPurchaseComplete?.invoke(false, "결제가 취소되었습니다.")
                onPurchaseComplete = null
            }
            else -> {
                AppLogger.e(TAG, "onPurchasesUpdated: purchase failed - code=${billingResult.responseCode}, msg=${billingResult.debugMessage}", null)
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
        val type = when {
            purchased.contains(PRODUCT_FOREVER) -> "forever"
            purchased.contains(PRODUCT_CONNECT_PLUS_MONTHLY) ||
            purchased.contains(PRODUCT_CONNECT_PLUS_YEARLY) -> "connect_plus"
            purchased.contains(PRODUCT_CONNECT_MONTHLY) ||
            purchased.contains(PRODUCT_CONNECT_YEARLY) -> "connect"
            else -> "free"
        }
        AppLogger.d(TAG, "getSubscriptionType: $type (purchased=$purchased)")
        return type
    }

    /**
     * 평생 이용권 보유 여부 확인
     */
    fun hasForeverPlan(): Boolean {
        val has = _billingState.value.purchasedProducts.contains(PRODUCT_FOREVER)
        AppLogger.d(TAG, "hasForeverPlan: $has")
        return has
    }

    /**
     * 유료 구독 보유 여부 확인
     */
    fun hasPaidSubscription(): Boolean {
        val has = getSubscriptionType() != "free"
        AppLogger.d(TAG, "hasPaidSubscription: $has")
        return has
    }

    fun refreshPurchases() {
        AppLogger.d(TAG, "refreshPurchases: isConnected=${_billingState.value.isConnected}")
        if (_billingState.value.isConnected) {
            queryPurchases()
        }
    }

    fun endConnection() {
        AppLogger.i(TAG, "endConnection: closing billing connection")
        billingClient.endConnection()
    }
}
