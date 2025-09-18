package games.moisoni.google_ad.services;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.PlayGamesSdk;

import java.io.Serial;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import games.moisoni.google_ad.listeners.LoginEventListener;
import games.moisoni.utils.CommonUtil;

/**
 * Google 登录服务
 *
 * @author JackyTang
 */
@SuppressWarnings("unused")
public class LoginService implements Serializable {
    @Serial
    private static final long serialVersionUID = -891903167567909458L;

    private static final String TAG = "LoginService";
    private static boolean initialized = false;
    private final AtomicBoolean signingIn = new AtomicBoolean(false);
    private String oauthClientId;
    private LoginEventListener loginEventListener;
    private WeakReference<Activity> activityRef;
    private GamesSignInClient mGamesSignInClient;

    /**
     * 获取单例对象的全局访问点 - 懒加载（在第一次调用时创建实例） - 线程安全（JVM 类加载机制保证）
     *
     * @return 单例对象
     */
    public static LoginService getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public void signOut() {
        Log.d(TAG, "signOut()");
        if (loginEventListener != null) {
            loginEventListener.onLogout();
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate.");
    }

    public void onResume() {
        Log.d(TAG, "onResume()");

        // Since the state of the signed in user can change when the activity is not active
        // it is recommended to try and sign in silently from when the app resumes.
        signInSilently();
    }

    public void onStop() {
        Log.d(TAG, "onStop.");
    }

    /**
     * 设置登录回调（可选，用于动态更新回调）
     */
    public void setLoginEventListener(@NonNull LoginEventListener callback) {
        this.loginEventListener = callback;
    }

    /**
     * 初始化（建议在 Activity onCreate 中或首帧前调用一次）
     *
     * @param activity      当前 Activity 引用
     * @param oauthClientId OAuth 2.0 Web Client ID
     * @param callback      登录状态回调
     */
    public void init(@NonNull Activity activity, @NonNull String oauthClientId, @NonNull LoginEventListener callback) {
        Log.d(TAG, "init with clientId: " + oauthClientId);

        if (oauthClientId.trim().isEmpty()) {
            Log.d(TAG, "OAuth Client ID is null or empty");
            throw new IllegalArgumentException("OAuth Client ID cannot be null or empty");
        }

        PlayGamesSdk.initialize(activity);
        this.activityRef = new WeakReference<>(activity);
        this.mGamesSignInClient = PlayGames.getGamesSignInClient(activity);
        this.oauthClientId = oauthClientId;
        this.loginEventListener = callback;
    }

    /**
     * v2：静默检查当前是否已认证 - 成功：进入 onConnected() - 失败：进入 onDisconnected()
     */
    public void signInSilently() {
        Log.d(TAG, "signInSilently()");

        if (isInitialized()) {
            Log.e(TAG, "GoogleLoginManager not initialized. Call init() first.");
            showSignInError("Login manager not properly initialized");
            return;
        }

        Env env = requireEnvOrWarn();
        if (env == null) return;

        mGamesSignInClient
                .isAuthenticated()
                .addOnCompleteListener(
                        task -> {
                            boolean authed = task.isSuccessful() && task.getResult().isAuthenticated();
                            if (!authed) {
                                Log.d(TAG, "❌ 静默登录失败，转手动登录");
                                signInManual();
                                return;
                            }

                            Log.d(TAG, "✅ 静默登录成功");
                            requestAuthCode();
                        })
                .addOnFailureListener(
                        e -> {
                            Log.e(TAG, "❌ 静默登录异常：" + e.getMessage(), e);

                            String errorMsg = resolveSignInError(e);
                            showSignInError(errorMsg);
                            onDisconnected(errorMsg);
                        });
    }


    /**
     * 手动触发登录（例如点击登录按钮时调用） 成功：进入 fetchPlayerInfo() 失败：弹错误提示
     */
    private void signInManual() {
        Log.d(TAG, "startSignInIntent() - using Play Games Services v2");

        if (isInitialized()) {
            Log.e(TAG, "GoogleLoginManager not initialized. Call init() first.");
            showSignInError("Login manager not properly initialized");
            return;
        }

        Env env = requireEnvOrWarn();
        if (env == null) return;

        // 防止多次触发
        if (!signingIn.compareAndSet(false, true)) {
            Log.d(TAG, "signIn 已在进行中，忽略重复请求");
            return;
        }

        mGamesSignInClient
                .signIn()
                .addOnCompleteListener(
                        unused -> {
                            boolean authed = unused.isSuccessful() && unused.getResult().isAuthenticated();
                            if (!authed) {
                                Log.d(TAG, "❌ 手动登录失败");

                                signingIn.set(false);
                                onDisconnected("手动登录失败");
                                return;
                            }

                            Log.d(TAG, "✅ 手动登录成功");
                            signingIn.set(false);
                            requestAuthCode();
                        })
                .addOnFailureListener(
                        e -> {
                            Log.e(TAG, "❌ 手动登录异常: " + e.getMessage(), e);

                            signingIn.set(false);
                            String errorMessage = resolveSignInError(e);
                            showSignInError(errorMessage);
                            onDisconnected(errorMessage);
                        });
    }


    /**
     * 请求服务器授权码 - 成功：进入 onConnected() - 失败：弹错误提示
     */
    private void requestAuthCode() {
        Env env = requireEnvOrWarn();
        if (env == null) return;

        // ✅ 关键补充：避免静默与手动同时进入这里导致并发换码
        if (!signingIn.compareAndSet(false, true)) {
            Log.d(TAG, "AuthCode 请求已在进行中，忽略重复调用");
            return;
        }

        mGamesSignInClient
                .requestServerSideAccess(oauthClientId, false)
                .addOnCompleteListener(
                        task -> {
                            try {
                                if (!task.isSuccessful()) {
                                    Log.e(TAG, "❌ 获取授权码失败");

                                    String errorMessage = "Failed to get auth code";
                                    showSignInError(errorMessage);
                                    onDisconnected(errorMessage);
                                    return;
                                }

                                String result = task.getResult();
                                Log.d(TAG, "✅ 获取授权码成功: " + result);
                                onConnected(result);
                            } finally {
                                signingIn.set(false);
                            }
                        })
                .addOnFailureListener(
                        e -> {
                            try {
                                Log.e(TAG, "❌ 授权码异常: " + e.getMessage(), e);
                                String errorMessage = resolveSignInError(e);
                                showSignInError(errorMessage);
                                onDisconnected(errorMessage);
                            } finally {
                                signingIn.set(false);
                            }
                        });
    }


    private void onConnected(String authCode) {
        Log.d(TAG, "onConnected(): connected to Google APIs");

        // 使用回调接口通知外部
        // JYGameUtils.InfoToJs("logoutState", authCode);
        if (loginEventListener != null) {
            loginEventListener.onLoginSuccess(authCode);
        }
    }

    private void onDisconnected(String errorMessage) {
        Log.d(TAG, "onDisconnected()");
        // JYGameUtils.InfoToJs("loginState", "");
        // 使用回调接口通知外部
        if (loginEventListener != null) {
            loginEventListener.onLoginFailed(errorMessage);
        }
    }

    /**
     * 解析登录失败的异常信息，返回用户可读的错误消息
     *
     * @param e 异常对象
     * @return 用户可读的错误消息
     */
    private String resolveSignInError(@NonNull Exception e) {
        if (e instanceof ApiException apiEx) {
            int code = apiEx.getStatusCode();
            return switch (code) {
                case CommonStatusCodes.CANCELED -> "用户取消登录";
                case CommonStatusCodes.SIGN_IN_REQUIRED -> "需要重新登录";
                case CommonStatusCodes.NETWORK_ERROR -> "网络连接错误，请检查网络";
                case CommonStatusCodes.TIMEOUT -> "登录超时，请重试";
                default -> "登录失败，错误码: " + code;
            };
        }
        return safeMsg(e);
    }

    private String safeMsg(Throwable e) {
        return (e == null || e.getMessage() == null) ? "Unknown error" : e.getMessage();
    }

    private void showSignInError(@NonNull String message) {
        Activity act = activityRef != null ? activityRef.get() : null;
        if (act == null || act.isFinishing()) {
            Log.e(TAG, "Cannot show error dialog: activity context is null/finishing");
            return;
        }

        // 保证在 UI 线程上弹窗
        act.runOnUiThread(() -> CommonUtil.showText(act, message));
    }

    /**
     * 尝试获取可用的 Activity 与 Client，失败则弹错并返回 null（自愈优先，兜底报错）
     *
     * @return Env 包含 Activity 与 Client 的环境对象，失败则为 null
     */
    @Nullable
    private Env requireEnvOrWarn() {
        Activity act = (activityRef != null) ? activityRef.get() : null;
        if (act == null || act.isFinishing()) {
            Log.e(TAG, "Activity is null/finishing");
            showSignInError("No valid activity for sign-in");
            return null;
        }

        if (mGamesSignInClient == null) {
            mGamesSignInClient = PlayGames.getGamesSignInClient(act); // 自愈式获取
        }

        return new Env(act, mGamesSignInClient);
    }

    /**
     * 包含 Activity 与 Client 的环境对象
     */
    private static final class Env {
        final Activity activity;
        final GamesSignInClient client;

        Env(Activity a, GamesSignInClient c) {
            this.activity = a;
            this.client = c;
        }
    }

    /**
     * 检查是否已正确初始化
     */
    private boolean isInitialized() {
        return oauthClientId == null || oauthClientId.trim().isEmpty() || loginEventListener == null;
    }

    private LoginService() {
        if (initialized) {
            throw new RuntimeException("单例模式被破坏，请使用 getInstance() 方法获取实例");
        }

        initialized = true;
    }

    private static class SingletonHolder {
        private static final LoginService INSTANCE = new LoginService();
    }

    /**
     * 解决序列化和反序列化导致生成新对象的问题 - 当反序列化时，会调用该方法 - 保证返回同一个单例对象
     */
    @Serial
    private Object readResolve() {
        return SingletonHolder.INSTANCE;
    }
}
