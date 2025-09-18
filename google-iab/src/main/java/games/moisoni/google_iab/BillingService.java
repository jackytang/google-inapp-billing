package games.moisoni.google_iab;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.List;

import games.moisoni.google_iab.listeners.BillingEventListener;
import games.moisoni.utils.CommonUtil;

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
        CommonUtil.runOnUiThread(activityRef.get(), () -> billingConnector.purchase(activityRef.get(), productId));
    }

    public void purchaseNonConsumable(String productId) {
        CommonUtil.runOnUiThread(activityRef.get(), () -> billingConnector.purchase(activityRef.get(), productId));
    }

    public void subscribe(String productId) {
        CommonUtil.runOnUiThread(activityRef.get(), () -> billingConnector.subscribe(activityRef.get(), productId));
    }

    public void unsubscribe(String productId) {
        CommonUtil.runOnUiThread(activityRef.get(), () -> billingConnector.unsubscribe(activityRef.get(), productId));
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
}
