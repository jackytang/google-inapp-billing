package games.moisoni.google_ad.listeners;

/**
 * Listener interface for login events
 *
 * @author JackyTang
 */
public interface LoginEventListener {
    /**
     * 登录成功回调
     *
     * @param authCode 授权码
     */
    void onLoginSuccess(String authCode);

    /**
     * 登录失败回调
     *
     * @param errorMessage 错误信息
     */
    void onLoginFailed(String errorMessage);

    /**
     * 登出回调
     */
    void onLogout();
}
