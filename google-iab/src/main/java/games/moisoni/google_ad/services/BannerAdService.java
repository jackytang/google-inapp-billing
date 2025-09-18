package games.moisoni.google_ad.services;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerAdView;
import com.google.android.gms.common.util.CollectionUtils;

import java.io.Serial;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import games.moisoni.utils.CommonUtil;

public class BannerAdService implements Serializable {

    @Serial
    private static final long serialVersionUID = -2966827878221209798L;
    private static final String TAG = "BannerAdService";
    private static boolean initialized = false;
    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);
    private String adUnitId;
    private AdManagerAdView adView;
    private List<String> testDeviceHashedIds;
    private WeakReference<Activity> activityRef;
    private LinearLayout bannerLayout;

    public static BannerAdService getInstance() {
        return BannerAdService.SingletonHolder.INSTANCE;
    }

    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate.");
    }

    public void onDestroy() {
        try {
            // 1) 销毁并清空 AdView
            if (adView != null) {
                // 如果你设置过 AdListener，这里也一并移除，避免持有 Activity 引用
                adView.setAdListener(null);
                adView.destroy();
                adView = null;
            }

            // 2) 从父容器移除 Banner 容器并清空其子 View（防止 Window/Context 泄漏）
            if (bannerLayout != null) {
                try {
                    android.view.ViewParent parent = bannerLayout.getParent();
                    if (parent instanceof android.view.ViewGroup) {
                        ((android.view.ViewGroup) parent).removeView(bannerLayout);
                    }
                    bannerLayout.removeAllViews(); // 保险起见
                } catch (Exception ignored) {
                }
                bannerLayout = null;
            }

            // 3) 释放对 Activity 的引用
            if (activityRef != null) {
                Activity act = activityRef.get();
                if (act != null && act.isFinishing()) {
                    // 没有额外动作，只在即将销毁时清理引用
                }
                activityRef.clear();
                activityRef = null;
            }

            // 4) 其他可释放字段
            testDeviceHashedIds = null;
            // isMobileAdsInitializeCalled 不需要改回 false，避免重复初始化

            Log.d(TAG, "BannerAdService resources released.");
        } catch (Exception e) {
            Log.w(TAG, "release() error", e);
        }
    }


    /** 在 Activity.onPause() 调用；暂停广告的计时器/动画等，节省资源 */
    public void onPause() {
        Activity activity = activityRef != null ? activityRef.get() : null;
        if (activity == null) return;

        CommonUtil.runOnUiThread(activity, () -> {
            if (adView != null) {
                try {
                    adView.pause();
                    Log.d(TAG, "AdView paused.");
                } catch (Exception e) {
                    Log.w(TAG, "pause() error", e);
                }
            }
        });
    }

    /** 在 Activity.onResume() 调用；恢复广告 */
    public void onResume() {
        Activity activity = activityRef != null ? activityRef.get() : null;
        if (activity == null) return;

        CommonUtil.runOnUiThread(activity, () -> {
            if (adView != null) {
                try {
                    adView.resume();
                    Log.d(TAG, "AdView resumed.");
                } catch (Exception e) {
                    Log.w(TAG, "resume() error", e);
                }
            }
        });
    }

    public final void setTestDeviceHashedIds(List<String> testDeviceHashedIds) {
        this.testDeviceHashedIds = testDeviceHashedIds;
    }

    public void init(@NonNull Activity activity, @NonNull String adUnitId) {
        Log.d(TAG, "init with adUnitId: " + adUnitId);

        if (adUnitId.trim().isEmpty()) {
            throw new IllegalArgumentException("Ad Unit ID cannot be null or empty");
        }

        this.adUnitId = adUnitId;
        this.activityRef = new WeakReference<>(activity);
        initRequestConfiguration();
        initializeMobileAdsSdk();
    }

    public void hideBannerAd() {
        if (bannerLayout != null) {
            CommonUtil.runOnUiThread(activityRef.get(), () -> bannerLayout.setVisibility(View.GONE));
        }
    }

    public void showBannerAd() {
        if (bannerLayout != null) {
            CommonUtil.runOnUiThread(activityRef.get(), () -> bannerLayout.setVisibility(View.VISIBLE));
        }
    }

    private void initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return;
        }

        new Thread(
                () -> {
                    // Initialize the Google Mobile Ads SDK on a background thread.
                    MobileAds.initialize(activityRef.get(), initializationStatus -> {
                        Log.d(TAG, "BannerAdService Mobile Ads SDK initialized");
                    });

                    // Load an ad on the main thread.
                    CommonUtil.runOnUiThread(activityRef.get(), this::loadBanner);
                })
                .start();
    }

    @RequiresPermission("android.permission.INTERNET")
    private void loadBanner() {
        Activity activity = activityRef.get();
        if (activity == null) return;

        bannerLayout = new LinearLayout(activity);
        bannerLayout.setOrientation(LinearLayout.VERTICAL);

        // 计算自适应 AdSize（注意：宽度用 dp）
        AdSize adSize = getAdaptiveAdSize(activity);

        adView = new AdManagerAdView(activity);
        adView.setAdUnitId(adUnitId);
        adView.setAdSize(adSize);

        bannerLayout.removeAllViews();
        bannerLayout.addView(adView);

        AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();
        adView.loadAd(adRequest);

        activity.addContentView(bannerLayout, getUnifiedBannerLayoutParams());
        bannerLayout.setVisibility(View.GONE);
    }

    /**
     * 获取当前方向下的自适应 Banner 尺寸（自动把 px 转 dp，兼容 API 30+ 的 WindowMetrics）
     */
    private static AdSize getAdaptiveAdSize(Activity activity) {
        int widthPx;
        float density = activity.getResources().getDisplayMetrics().density;

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            final android.view.WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
            android.graphics.Rect bounds = metrics.getBounds();
            widthPx = bounds.width();

            // 扣除系统栏等 inset，避免测量过宽
            final android.view.WindowInsets windowInsets = metrics.getWindowInsets();
            final int insetLeft = windowInsets.getInsets(android.view.WindowInsets.Type.systemBars()).left;
            final int insetRight = windowInsets.getInsets(android.view.WindowInsets.Type.systemBars()).right;
            widthPx -= (insetLeft + insetRight);
        } else {
            android.util.DisplayMetrics outMetrics = new android.util.DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
            widthPx = outMetrics.widthPixels;
        }

        int adWidthDp = Math.max(0, (int) (widthPx / density));
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidthDp);
    }

    private FrameLayout.LayoutParams getUnifiedBannerLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        return params;
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

    private BannerAdService() {
        if (initialized) {
            throw new RuntimeException("单例模式被破坏，请使用 getInstance() 方法获取实例");
        }

        initialized = true;
    }

    private static class SingletonHolder {
        private static final BannerAdService INSTANCE = new BannerAdService();
    }

    /**
     * 解决序列化和反序列化导致生成新对象的问题 - 当反序列化时，会调用该方法 - 保证返回同一个单例对象
     */
    @Serial
    private Object readResolve() {
        return BannerAdService.SingletonHolder.INSTANCE;
    }
}
