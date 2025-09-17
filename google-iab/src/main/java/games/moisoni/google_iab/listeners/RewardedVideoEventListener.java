package games.moisoni.google_iab.listeners;

/**
 * Listener interface for AdMob advertisement events
 * <p>
 * Used to receive callbacks for various ad-related events such as loading,
 * displaying, clicking, and reward earning. This allows the implementing
 * class to handle ad lifecycle events appropriately.
 *
 * @author JackyTang
 */
public interface RewardedVideoEventListener {
    /**
     * 用户获得奖励回调
     * @param rewardEarned 是否获得奖励
     */
    void onRewardEarned(boolean rewardEarned);

    /**
     * 广告加载成功回调
     * @param slotName 槽位名称（如 "Slot A", "Slot B"）
     */
    void onAdLoaded(String slotName);

    /**
     * 广告加载失败回调
     * @param slotName 槽位名称
     * @param errorMessage 错误信息
     */
    void onAdLoadFailed(String slotName, String errorMessage);

    /**
     * 广告显示失败回调
     * @param errorMessage 错误信息
     */
    void onAdShowFailed(String errorMessage);

    /**
     * 广告关闭回调
     */
    default void onAdDismissed() {};

    /**
     * 广告展示回调
     */
    default void onAdShowed() {};

    /**
     * 广告点击回调
     */
    default void onAdClicked() {};

    /**
     * 广告曝光回调
     */
    default  void onAdImpression() {};

    /**
     * 广告 SDK 初始化完成回调
     */
    default void onAdSdkInitialized() {};
}
