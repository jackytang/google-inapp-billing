package games.moisoni.google_ad.services;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.List;

import games.moisoni.google_iab.BillingConnector;
import games.moisoni.google_iab.listeners.BillingEventListener;

public class BillingService implements Serializable {
    @Serial
    private static final long serialVersionUID = 6040574201984767266L;

    private static final String TAG = "BillingService";
    private static boolean initialized = false;
    private BillingConnector billingConnector;
    private WeakReference<Activity> activityRef;
    private List<String> nonConsumableIds;
    private List<String> consumableIds;
    private List<String> subscriptionIds;

    public static BillingService getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public void init(@NonNull Activity activity, String base64Key, BillingEventListener billingEventListener) {
        Log.d(TAG, "billing service init");
        this.activityRef = new WeakReference<>(activity);
        initializeBillingClient(base64Key, billingEventListener);
    }

    public void onDestroy() {
        if (billingConnector != null) {
            billingConnector.release();
        }
    }

    public void purchaseConsumable(String productId) {
        billingConnector.purchase(activityRef.get(), productId);
    }

    public void purchaseNonConsumable(String productId) {
        billingConnector.purchase(activityRef.get(), productId);
    }

    public void subscribe(String productId) {
        billingConnector.subscribe(activityRef.get(), productId);
    }

    public void unsubscribe(String productId) {
        billingConnector.unsubscribe(activityRef.get(), productId);
    }

    private void initializeBillingClient(String base64Key, BillingEventListener billingEventListener) {
        billingConnector =
                new BillingConnector(activityRef.get(), base64Key, null)
                        .setConsumableIds(consumableIds)
                        .setNonConsumableIds(nonConsumableIds)
                        .setSubscriptionIds(subscriptionIds)
                        .autoAcknowledge()
                        .autoConsume()
                        .enableLogging()
                        .connect();

        billingConnector.setBillingEventListener(billingEventListener);
    }

    private BillingService() {
        if (initialized) {
            throw new RuntimeException("单例模式被破坏，请使用 getInstance() 方法获取实例");
        }

        initialized = true;
    }

    private static class SingletonHolder {
        private static final BillingService INSTANCE = new BillingService();
    }

    @Serial
    private Object readResolve() {
        return SingletonHolder.INSTANCE;
    }



    /**
     * To set consumable products ids
     */
    public final void setConsumableIds(List<String> consumableIds) {
        this.consumableIds = consumableIds;
    }

    /**
     * To set non-consumable products ids
     */
    public final void setNonConsumableIds(List<String> nonConsumableIds) {
        this.nonConsumableIds = nonConsumableIds;
    }

    /**
     * To set subscription products ids
     */
    public final void setSubscriptionIds(List<String> subscriptionIds) {
        this.subscriptionIds = subscriptionIds;
    }

    // private boolean verifyOnServer(PurchaseInfo p) {
    //     // TODO: 实际项目中调用你的后端，后端用 Google Play Developer API 校验 purchaseToken
    //     return true;
    // }
    //
    // private class MyBillingEventListener implements BillingEventListener {
    //     /**
    //      * 从 Google Play 获取产品列表时调用
    //      *
    //      * @param productDetails 产品详情列表
    //      */
    //     @Override
    //     public void onProductsFetched(@NonNull List<ProductInfo> productDetails) {
    //         String product;
    //         String price;
    //
    //         for (ProductInfo productInfo : productDetails) {
    //             product = productInfo.getProduct();
    //             price = productInfo.getOneTimePurchaseOfferFormattedPrice();
    //
    //             if (product.equalsIgnoreCase("remove_ads")) {
    //                 Log.d("BillingConnector", "Product fetched: " + product);
    //                 // getShow("Product fetched: " + product);
    //
    //                 Log.d("BillingConnector", "Product price: " + price);
    //                 // getShow("Product price: " + price);
    //             }
    //         }
    //     }
    //
    //     /**
    //      * 从 Google Play 获取已购买的产品列表时调用
    //      *
    //      * @param productType 商品类型
    //      * @param purchases 购买列表
    //      */
    //     @Override
    //     public void onPurchasedProductsFetched(
    //             @NonNull ProductType productType, @NonNull List<PurchaseInfo> purchases) {
    //         // This will be called even when no purchased products are returned by the API
    //         // 即使API未返回任何已购买的产品，此方法仍会被调用。
    //
    //         // String purchaseProduct;
    //         boolean isAcknowledged;
    //
    //         switch (productType) {
    //             case INAPP -> {
    //                 // TODO - non-consumable/consumable products
    //                 for (PurchaseInfo purchaseInfo : purchases) {
    //                     // purchaseProduct = purchaseInfo.getProduct();
    //                     isAcknowledged = purchaseInfo.isAcknowledged();
    //
    //                     if (isAcknowledged) {
    //                         JYGameUtils.InfoToJs("purchaseNonConsumable", "2");
    //                         getShow("The previous purchase was successfully restored.");
    //                     }
    //                 }
    //             }
    //             case SUBS -> {
    //                 // TODO - subscription products
    //             }
    //             case COMBINED -> {
    //                 // this will be triggered on activity start
    //                 // the other two (INAPP and SUBS) will be triggered when the user
    //                 // actually buys a product
    //                 // TODO - restore purchases
    //             }
    //             default -> {
    //                 Log.d("BillingConnector", "Unknown product type");
    //                 getShow("Unknown product type");
    //             }
    //         }
    //     }
    //
    //     /**
    //      * 当商品购买成功时将被调用
    //      *
    //      * @param purchases - list of successfully purchased products
    //      */
    //     @Override
    //     public void onProductsPurchased(@NonNull List<PurchaseInfo> purchases) {
    //         String product;
    //         String purchaseToken;
    //
    //         for (PurchaseInfo purchaseInfo : purchases) {
    //             product = purchaseInfo.getProduct();
    //             purchaseToken = purchaseInfo.getPurchaseToken();
    //
    //             // TODO - do something
    //             Log.d("BillingConnector", "Product purchased: " + product);
    //             // getShow("Product purchased: " + product);
    //
    //             // TODO - do something
    //             Log.d("BillingConnector", "Purchase token: " + purchaseToken);
    //             // getShow("Purchase token: " + purchaseToken);
    //
    //             // TODO - similarly check for other ids
    //             // purchasedInfoList.add(purchaseInfo);
    //             // check "usefulPublicMethods" to see how to
    //             // acknowledge or consume a purchase manually
    //         }
    //     }
    //
    //     /**
    //      * 当购买被确认时调用
    //      *
    //      * @param purchase - specifier of acknowledged purchase
    //      */
    //     @Override
    //     public void onPurchaseAcknowledged(@NonNull PurchaseInfo purchase) {
    //         // String acknowledgedProduct = purchase.getProduct();
    //
    //         // 1) 发送 p.getPurchaseToken() 给你自己的服务端做校验
    //         boolean ok = verifyOnServer(purchase);
    //         if (!ok) {
    //             Log.e(TAG, "Server verification failed for token=" + purchase.getPurchaseToken());
    //             return;
    //         }
    //
    //         // if (acknowledgedProduct.equalsIgnoreCase(productId)) {
    //         // here we are saving the purchase status into our "userPrefersAdFree" variable
    //         // SharedPrefsHelper.putBoolean("userPrefersAdFree", true);
    //
    //         JYGameUtils.InfoToJs("purchaseNonConsumable", "1");
    //         getShow("The purchase was successfully made.");
    //         // }
    //     }
    //
    //     /**
    //      * 当购买被消耗时调用, 这里是指一次性消耗品
    //      *
    //      * @param purchase - specifier of consumed purchase
    //      */
    //     @Override
    //     public void onPurchaseConsumed(@NonNull PurchaseInfo purchase) {
    //         /*
    //          * Grant user entitlement for CONSUMABLE products here
    //          *
    //          * Even though onProductsPurchased is triggered when a purchase is successfully made
    //          * there might be a problem along the way with the payment and the user will be able consume the product
    //          * without actually paying
    //          * */
    //         String consumedProduct = purchase.getProduct();
    //
    //         if (consumedProduct.equalsIgnoreCase("consumable_id_1")) {
    //             Log.d("BillingConnector", "Consumed: " + consumedProduct);
    //             getShow("Consumed: " + consumedProduct);
    //         }
    //     }
    //
    //     /** 错误处理 */
    //     @Override
    //     public void onBillingError(
    //             @NonNull BillingConnector billingConnector, @NonNull BillingResponse response) {
    //         switch (response.getErrorType()) {
    //             case ACKNOWLEDGE_WARNING:
    //                 // this response will be triggered when the purchase is still
    //                 // PENDING
    //                 getShow(
    //                         "The transaction is still pending. Please come back later to receive the purchase!");
    //                 break;
    //             case BILLING_UNAVAILABLE:
    //             case SERVICE_UNAVAILABLE:
    //                 getShow(
    //                         "Billing is unavailable at the moment. Check your internet connection!");
    //                 break;
    //             case ERROR:
    //                 getShow("Something happened, the transaction was canceled!");
    //                 break;
    //         }
    //
    //         Log.d(
    //                 "BillingConnector",
    //                 "Error type: "
    //                         + response.getErrorType()
    //                         + " Response code: "
    //                         + response.getResponseCode()
    //                         + " Message: "
    //                         + response.getDebugMessage());
    //         JYGameUtils.InfoToJs("purchaseNonConsumable", "0");
    //     }
    // }
    //
    // private void getShow(String text) {
    //     Toast.makeText(activityRef.get(), text, Toast.LENGTH_SHORT).show();
    // }

}
