package com.oamsapps.pdfzy.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
import com.android.billingclient.api.BillingClient.BillingResponseCode.OK
import com.android.billingclient.api.BillingClient.ProductType

/**
 * One-time "Remove ads" purchase (Play Console → Products → One-time products, ID = [REMOVE_ADS]).
 * Ad-free state is cached so ads stay hidden offline, and re-checked with Play on start/resume
 * (restores after reinstall, revokes after refund).
 */
object Billing : PurchasesUpdatedListener {
    const val REMOVE_ADS = "remove_ads"
    val adFree = mutableStateOf(false)
    val price = mutableStateOf<String?>(null)
    val pending = mutableStateOf(false)

    private lateinit var prefs: SharedPreferences
    private lateinit var client: BillingClient
    private var details: ProductDetails? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("billing", Context.MODE_PRIVATE)
        adFree.value = prefs.getBoolean("adFree", false)
        client = BillingClient.newBuilder(ctx.applicationContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) { if (r.responseCode == OK) { refresh(); loadDetails() } }
            override fun onBillingServiceDisconnected() {} // auto-reconnection is enabled
        })
    }

    private fun loadDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(REMOVE_ADS).setProductType(ProductType.INAPP).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, result ->
            details = result.productDetailsList.firstOrNull()
            price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /** Re-check ownership with Play (call on start and onResume). */
    fun refresh() {
        if (!::client.isInitialized || !client.isReady) return
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == OK) handle(list, fromQuery = true)
        }
    }

    /** Returns false if the product isn't available (not set up in Play Console, or no Play Store). */
    fun buy(activity: Activity): Boolean {
        val d = details ?: return false
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        return true
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        when {
            r.responseCode == OK && purchases != null -> handle(purchases, fromQuery = false)
            r.responseCode == ITEM_ALREADY_OWNED -> refresh()
        }
    }

    private fun handle(list: List<Purchase>, fromQuery: Boolean) {
        val p = list.firstOrNull { REMOVE_ADS in it.products }
        when {
            p?.purchaseState == Purchase.PurchaseState.PURCHASED -> {
                set(true); pending.value = false
                // Unacknowledged purchases are auto-refunded after 3 days
                if (!p.isAcknowledged) client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
            }
            p?.purchaseState == Purchase.PurchaseState.PENDING -> pending.value = true
            fromQuery -> { set(false); pending.value = false } // not owned (never bought, or refunded)
        }
    }

    private fun set(v: Boolean) { adFree.value = v; prefs.edit().putBoolean("adFree", v).apply() }
}
