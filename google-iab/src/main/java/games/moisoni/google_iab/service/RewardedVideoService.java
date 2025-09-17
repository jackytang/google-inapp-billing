package games.moisoni.google_iab.service;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.common.util.CollectionUtils;

import java.io.Serial;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import games.moisoni.google_iab.listeners.RewardedAdEventListener;
import games.moisoni.google_iab.utils.CommonUtil;

/**
 * RewardAdService class for managing rewarded advertisements.
 * <p>
 * This class implements Serializable to allow instances to be serialized
 * and deserialized, facilitating easy storage and transmission.
 * <p>
 * Note: This class currently does not contain any fields or methods.
 * It serves as a placeholder for future implementation related to
 * rewarded ads functionality.
 *
 * @author JackyTang
 */
@SuppressWarnings("unused")
public class RewardedVideoService implements Serializable {
    @Serial
    private static final long serialVersionUID = 6729803763120205545L;
    private static boolean initialized = false;
    private static final String TAG = "RewardedVideoService";

    // 双槽位广告
    private RewardedAd slotA;
    private RewardedAd slotB;
    // 存储外部传入的配置
    private String adUnitId;
    private List<String> testDeviceHashedIds;
    private WeakReference<Activity> activityRef;
    private RewardedAdEventListener adCallback;

    private final AtomicBoolean isShowing = new AtomicBoolean(false);
    private final AtomicBoolean isLoadingSlotA = new AtomicBoolean(false);
    private final AtomicBoolean isLoadingSlotB = new AtomicBoolean(false);
    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);
    // 用于轮换使用两个槽位
    private final AtomicInteger currentSlot = new AtomicInteger(0);

    public static RewardedVideoService getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 设置广告回调（可选，用于动态更新回调）
     */
    public void setAdEventListener(@NonNull RewardedAdEventListener callback) {
        this.adCallback = callback;
    }

    /**
     * 初始化广告管理器
     *
     * @param activity 当前 Activity 引用
     * @param adUnitId 广告单元 ID
     * @param callback 广告状态回调
     */
    public void init(@NonNull Activity activity, @NonNull String adUnitId, @NonNull RewardedAdEventListener callback) {
        Log.d(TAG, "init with adUnitId: " + adUnitId);

        if (adUnitId.trim().isEmpty()) {
            throw new IllegalArgumentException("Ad Unit ID cannot be null or empty");
        }

        this.activityRef = new WeakReference<>(activity);
        this.adUnitId = adUnitId;
        this.adCallback = callback;

        this.initRequestConfiguration();
        this.initializeMobileAdsSdk();
    }

    public final void setTestDeviceHashedIds(List<String> testDeviceHashedIds) {
        this.testDeviceHashedIds = testDeviceHashedIds;
    }

    private void initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return;
        }

        new Thread(() -> {
            // Initialize the Google Mobile Ads SDK on a background thread.
            MobileAds.initialize(activityRef.get(), initializationStatus -> {
                Log.d(TAG, "Mobile Ads SDK initialized");
            });

            // Load ads on the main thread.
            CommonUtil.runOnUiThread(this.activityRef.get(), this::loadAllRewardedAds);
        }).start();
    }

    private void initRequestConfiguration() {
        if (CollectionUtils.isEmpty(testDeviceHashedIds)) {
            return;
        }

        var configuration = new RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceHashedIds)
                .build();
        MobileAds.setRequestConfiguration(configuration);
    }

    private void loadAllRewardedAds() {
        loadRewardedAdForSlot(true);  // 加载槽位A
        loadRewardedAdForSlot(false); // 加载槽位B
    }

    public void showRewardedVideoAd() {
        RewardedAd adToShow = getAvailableAd();

        if (adToShow == null) {
            Log.d(TAG, "No rewarded ad is ready yet.");
            // 如果没有可用广告，尝试加载
            loadAllRewardedAds();
            return;
        }

        final Activity activity = activityRef != null ? activityRef.get() : null;
        if (activity == null) {
            Log.w(TAG, "Activity is null, cannot show ad.");
            return;
        }

        if (!isShowing.compareAndSet(false, true)) {
            Log.d(TAG, "Rewarded is already showing, ignore");
            return;
        }

        final AtomicBoolean rewardEarned = new AtomicBoolean(false);
        final RewardedAd finalAdToShow = adToShow;

        CommonUtil.runOnUiThread(activity, () -> {
            finalAdToShow.setFullScreenContentCallback(
                    new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Ad was dismissed.");

                            // 清除已使用的广告引用
                            clearUsedAd(finalAdToShow);
                            isShowing.set(false);

                            // if (!rewardEarned.get()) {
                            //     JYGameUtils.InfoToJs("rewardedVideo", String.valueOf(rewardEarned.get()));
                            // }

                            if (!rewardEarned.get() && adCallback != null) {
                                adCallback.onRewardEarned(rewardEarned.get());
                            }

                            // 预加载下一个广告
                            loadAllRewardedAds();
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                            Log.d(TAG, "Ad failed to show: " + adError.getMessage());

                            // 清除失败的广告引用
                            clearUsedAd(finalAdToShow);
                            isShowing.set(false);

                            // JYGameUtils.InfoToJs("rewardedVideo", String.valueOf(rewardEarned.get()));
                            if (adCallback != null) {
                                adCallback.onRewardEarned(rewardEarned.get());
                                adCallback.onAdShowFailed(adError.getMessage());
                            }

                            // 尝试显示另一个槽位的广告
                            showBackupAd();

                            // 预加载广告
                            loadAllRewardedAds();
                        }

                        @Override
                        public void onAdShowedFullScreenContent() {
                            Log.d(TAG, "Ad showed fullscreen content.");
                        }

                        @Override
                        public void onAdImpression() {
                            Log.d(TAG, "Ad recorded an impression.");
                        }

                        @Override
                        public void onAdClicked() {
                            Log.d(TAG, "Ad was clicked.");
                        }
                    });

            finalAdToShow.show(activity, rewardItem -> {
                Log.d(TAG, "User earned the reward.");
                rewardEarned.set(true);
                // JYGameUtils.InfoToJs("rewardedVideo", String.valueOf(rewardEarned.get()));
                if (adCallback != null) {
                    adCallback.onRewardEarned(rewardEarned.get());
                }
            });
        });
    }

    /**
     * 获取可用的广告（优先使用轮换策略）
     */
    private RewardedAd getAvailableAd() {
        // 尝试按轮换顺序使用广告
        int slot = currentSlot.get() % 2;

        if (slot == 0 && slotA != null) {
            currentSlot.incrementAndGet();
            return slotA;
        } else if (slot == 1 && slotB != null) {
            currentSlot.incrementAndGet();
            return slotB;
        }

        // 如果轮换的广告不可用，尝试使用任何可用的广告
        if (slotA != null) {
            return slotA;
        } else if (slotB != null) {
            return slotB;
        }

        return null;
    }

    /**
     * 显示备用广告（当主广告失败时）
     */
    private void showBackupAd() {
        RewardedAd backupAd = null;

        if (slotA != null) {
            backupAd = slotA;
        } else if (slotB != null) {
            backupAd = slotB;
        }

        if (backupAd != null && !isShowing.get()) {
            Log.d(TAG, "Trying to show backup ad");
            // 递归调用，但会使用不同的广告
            showRewardedVideoAd();
        }
    }

    /**
     * 清除已使用的广告引用
     */
    private void clearUsedAd(RewardedAd usedAd) {
        if (slotA == usedAd) {
            slotA = null;
            Log.d(TAG, "Cleared slot A");
        } else if (slotB == usedAd) {
            slotB = null;
            Log.d(TAG, "Cleared slot B");
        }
    }

    /**
     * 为指定槽位加载广告
     */
    private void loadRewardedAdForSlot(boolean isSlotA) {
        final Activity activity = activityRef != null ? activityRef.get() : null;
        if (activity == null) {
            Log.w(TAG, "Activity is null, cannot load ad.");
            return;
        }

        final AtomicBoolean isLoading = isSlotA ? isLoadingSlotA : isLoadingSlotB;
        final String slotName = isSlotA ? "Slot A" : "Slot B";

        CommonUtil.runOnUiThread(activity, () -> {
            // 检查是否已有广告或正在加载
            if ((isSlotA && slotA != null) || (!isSlotA && slotB != null) || isLoading.get()) {
                Log.d(TAG, slotName + " already has ad or is loading, skip");
                return;
            }

            isLoading.set(true);
            Log.d(TAG, "Loading ad for " + slotName);

            RewardedAd.load(
                    activity,
                    adUnitId,
                    new AdRequest.Builder().build(),
                    new RewardedAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                            Log.d(TAG, slotName + " ad was loaded.");

                            if (isSlotA) {
                                slotA = rewardedAd;
                            } else {
                                slotB = rewardedAd;
                            }

                            isLoading.set(false);
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            Log.d(TAG, slotName + " failed to load: " + loadAdError.getMessage());
                            isLoading.set(false);

                            // 延迟重试加载
                            retryLoadAd(isSlotA, 5000); // 5秒后重试
                        }
                    });
        });
    }

    /**
     * 延迟重试加载广告
     */
    private void retryLoadAd(boolean isSlotA, long delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                loadRewardedAdForSlot(isSlotA);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Retry load interrupted", e);
            }
        }).start();
    }


    /**
     * 检查是否有可用的广告
     */
    public boolean hasAvailableAd() {
        return slotA != null || slotB != null;
    }

    /**
     * 获取已加载的广告数量
     */
    public int getLoadedAdCount() {
        int count = 0;
        if (slotA != null) count++;
        if (slotB != null) count++;
        return count;
    }

    /**
     * 预加载广告（在适当的时机调用，如游戏启动、关卡结束等）
     */
    public void preloadAds() {
        Log.d(TAG, "Preloading ads...");
        loadAllRewardedAds();
    }

    private RewardedVideoService() {
        if (initialized) {
            throw new RuntimeException("单例模式被破坏，请使用 getInstance() 方法获取实例");
        }

        initialized = true;
    }

    private static class SingletonHolder {
        private static final RewardedVideoService INSTANCE = new RewardedVideoService();
    }

    @Serial
    private Object readResolve() {
        return SingletonHolder.INSTANCE;
    }
}
