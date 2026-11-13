package com.agy.oauth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.agy.util.SensitiveDataRedactor;

/**
 * Handles the antigravity://auth-success deep link delivered to MainActivity.
 * Returns true when the intent was the auth callback.
 */
public final class DeepLinkHandler {

    private DeepLinkHandler() {
    }

    public static String getCallbackScheme(Context context) {
        if (context != null) {
            try {
                int resId = context.getResources().getIdentifier(
                        "oauth_scheme", "string", context.getPackageName());
                if (resId != 0) {
                    return context.getString(resId);
                }
            } catch (Exception ignored) {
            }
        }
        return OAuthConstants.CALLBACK_SCHEME;
    }

    /**
     * An unsolicited deep link is never proof of authentication (B04a/R06).
     *
     * Until the real vendor flow is verified (V1/B04b) this handler accepts only
     * this flavor's own callback scheme and host, writes nothing to CLI stdin,
     * and never announces success. A token/cookie/URL is not correlation.
     *
     * @return true when the intent matched this flavor's callback shape (it is
     *         consumed but intentionally ignored), false otherwise.
     */
    public static boolean handleDeepLink(Activity activity, Intent intent, Runnable onSuccess) {
        if (activity == null || intent == null || intent.getData() == null) {
            return false;
        }
        Uri data = intent.getData();
        String expectedScheme = getCallbackScheme(activity);
        String scheme = data.getScheme();
        // Never accept the other flavor's scheme.
        boolean schemeOk = scheme != null && expectedScheme.equalsIgnoreCase(scheme);
        String host = data.getHost();
        boolean hostOk = OAuthConstants.CALLBACK_HOST.equalsIgnoreCase(host)
                || OAuthConstants.CALLBACK_HOST_COMPAT.equalsIgnoreCase(host);
        if (!schemeOk || !hostOk) {
            return false;
        }
        Log.i(OAuthConstants.TAG, "auth callback shape received but no verified active "
                + "correlation exists; ignoring it and writing no stdin: "
                + SensitiveDataRedactor.safeUrl(data.toString()));
        return true;
    }
}
