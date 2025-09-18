package games.moisoni.utils;

import android.app.Activity;
import android.widget.Toast;

public class CommonUtil {

    public static String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void runOnUiThread(Activity activity, Runnable runnable) {
        if (activity != null) {
            activity.runOnUiThread(runnable);
        }
    }

    public static void showText(Activity activity, String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }
}
