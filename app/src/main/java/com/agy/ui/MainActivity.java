package com.agy.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.agy.core.CoreRuntimeState;
import com.agy.core.CoreServiceLauncher;
import com.agy.oauth.OAuthManager;
import com.agy.runtime.CliDownloader;
import com.agy.runtime.RuntimeManager;
import com.agy.util.SensitiveDataRedactor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Antigravity for Android:
 * Embedded Google Remote Control Studio powered by official native Bionic CLI engine.
 */
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_STORAGE = 100;
    private static final int REQUEST_FILE_CHOOSER = 200;

    private static final int VIEW_DASHBOARD = 0;
    private static final int VIEW_STUDIO = 1;
    private int currentView = VIEW_DASHBOARD;

    public static volatile MainActivity activeActivity;

    private OAuthManager oauthManager;
    private RuntimeManager runtimeManager;

    // View Containers
    private FrameLayout rootLayout;
    private ScrollView dashboardContainer;
    private LinearLayout studioContainer;

    // Dashboard UI
    private TextView statusBadge;
    private TextView infoText;
    private TextView sessionUrlText;
    private Button openRemoteButton;
    private Button openExternalLink;
    private Button startStopButton;
    private Button downloadButton;
    private ProgressBar downloadProgress;
    private TextView downloadStatusText;
    private TextView logTextView;
    private ScrollView logScrollView;

    // Studio UI
    private WebView webView;
    private ProgressBar webLoadingProgress;
    private TextView studioStatusDot;
    private ValueCallback<Uri[]> mFilePathCallback;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean serviceRunning = false;

    private final Runnable pollStatus = new Runnable() {
        @Override
        public void run() {
            updateDashboard();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeActivity = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        applySystemBars(isNightMode());

        oauthManager = new OAuthManager();
        runtimeManager = new RuntimeManager(this);

        buildUnifiedUI();
        requestStoragePermissions();

        // Check if intent contains a session URL or deep link
        if (getIntent() != null && getIntent().hasExtra("session_url")) {
            String url = getIntent().getStringExtra("session_url");
            if (CoreRuntimeState.isValidSessionUrl(url)) {
                loadSessionUrl(url);
            }
        } else {
            oauthManager.handleDeepLink(this, getIntent(), () -> {
                Toast.makeText(this, "Authentication successful! Establishing Remote Control session...", Toast.LENGTH_SHORT).show();
                if (CoreRuntimeState.isValidSessionUrl(CoreRuntimeState.serverUrl)) {
                    loadSessionUrl(CoreRuntimeState.serverUrl);
                }
            });
        }

        if (CliDownloader.isInstalled(this)) {
            startCoreService();
        } else {
            promptDownload();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("session_url")) {
            String url = intent.getStringExtra("session_url");
            if (CoreRuntimeState.isValidSessionUrl(url)) {
                loadSessionUrl(url);
            }
        }
        oauthManager.handleDeepLink(this, intent, () -> {
            Toast.makeText(this, "Authentication successful! Establishing Remote Control session...", Toast.LENGTH_SHORT).show();
            if (CoreRuntimeState.isValidSessionUrl(CoreRuntimeState.serverUrl)) {
                loadSessionUrl(CoreRuntimeState.serverUrl);
            }
        });
    }

    private boolean isNightMode() {
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySystemBars(boolean dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(dark ? Color.parseColor("#121316") : Color.parseColor("#F8F9FA"));
            getWindow().setNavigationBarColor(dark ? Color.parseColor("#121316") : Color.parseColor("#F8F9FA"));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void buildUnifiedUI() {
        boolean dark = isNightMode();
        int bgColor = dark ? Color.parseColor("#121316") : Color.parseColor("#F8F9FA");

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(bgColor);

        buildStudioView(dark);
        buildDashboardView(dark);

        rootLayout.addView(studioContainer);
        rootLayout.addView(dashboardContainer);

        setContentView(rootLayout);
        showDashboardView();
    }

    private void buildStudioView(boolean dark) {
        int topBarBg = dark ? Color.parseColor("#18191C") : Color.parseColor("#FFFFFF");
        int textColor = dark ? Color.parseColor("#FFFFFF") : Color.parseColor("#1A1A1A");
        int subTextColor = dark ? Color.parseColor("#A0A5AD") : Color.parseColor("#666666");
        int accentColor = Color.parseColor("#1A73E8");

        studioContainer = new LinearLayout(this);
        studioContainer.setOrientation(LinearLayout.VERTICAL);
        studioContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        studioContainer.setVisibility(View.GONE);

        // 1. Studio Top Navigation Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(topBarBg);
        topBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        // Back to Dashboard Button
        Button btnToDash = new Button(this);
        btnToDash.setText("← Dashboard");
        btnToDash.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnToDash.setTypeface(Typeface.DEFAULT_BOLD);
        btnToDash.setTextColor(textColor);
        btnToDash.setBackground(createButtonDrawable(dark ? Color.parseColor("#282A2E") : Color.parseColor("#E8EAED"), dp(6)));
        btnToDash.setPadding(dp(10), dp(2), dp(10), dp(2));
        btnToDash.setOnClickListener(v -> showDashboardView());
        topBar.addView(btnToDash);

        // Title and Status
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setGravity(Gravity.CENTER);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Antigravity Studio");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        titleCol.addView(title);

        studioStatusDot = new TextView(this);
        studioStatusDot.setText("● Initializing");
        studioStatusDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        studioStatusDot.setTextColor(subTextColor);
        titleCol.addView(studioStatusDot);

        topBar.addView(titleCol);

        // Reload Button
        Button btnReload = new Button(this);
        btnReload.setText("🔄");
        btnReload.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnReload.setTextColor(textColor);
        btnReload.setBackground(null);
        btnReload.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });
        topBar.addView(btnReload, new LinearLayout.LayoutParams(dp(40), dp(36)));

        // External Chrome fallback Button
        Button btnExternal = new Button(this);
        btnExternal.setText("🌐");
        btnExternal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnExternal.setTextColor(textColor);
        btnExternal.setBackground(null);
        btnExternal.setOnClickListener(v -> openInExternalBrowser());
        topBar.addView(btnExternal, new LinearLayout.LayoutParams(dp(40), dp(36)));

        studioContainer.addView(topBar);

        // 2. Loading Progress Bar
        webLoadingProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        webLoadingProgress.setMax(100);
        webLoadingProgress.setVisibility(View.GONE);
        webLoadingProgress.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        studioContainer.addView(webLoadingProgress);

        // 3. Embedded Android WebView
        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        configureWebView(webView);
        studioContainer.addView(webView);
    }

    private void configureWebView(WebView wv) {
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);

        // Stripping "; wv" and "Version/X.X" so Google accounts and Antigravity Web UI
        // recognize it as a modern standard Chrome mobile browser:
        String defaultUa = ws.getUserAgentString();
        String cleanUa;
        if (defaultUa != null && !defaultUa.isEmpty()) {
            cleanUa = defaultUa.replace("; wv", "")
                               .replace(";wv", "")
                               .replaceAll("Version/\\d+(\\.\\d+)*\\s*", "");
        } else {
            cleanUa = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
        }
        ws.setUserAgentString(cleanUa);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        wv.setWebViewClient(new WebViewClient() {
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlOverride(view, Uri.parse(url));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return handleUrlOverride(view, request.getUrl());
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (webLoadingProgress != null) {
                    webLoadingProgress.setVisibility(View.VISIBLE);
                    webLoadingProgress.setProgress(15);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (webLoadingProgress != null) {
                    webLoadingProgress.setVisibility(View.GONE);
                }
                try {
                    CookieManager.getInstance().flush();
                } catch (Exception ignored) {}

                if (url != null) {
                    // If user completed Google sign-in and landed on antigravity root,
                    // automatically redirect into the active Remote Control session
                    String activeSession = CoreRuntimeState.serverUrl;
                    if (CoreRuntimeState.isValidSessionUrl(activeSession)
                            && !url.contains("/r/")
                            && (url.contains("antigravity.google") || url.contains("antigravity.google.com"))
                            && isGoogleSessionActive()) {
                        Log.i(TAG, "Authenticated in WebView; auto-redirecting to active session: " + activeSession);
                        view.loadUrl(activeSession);
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.w(TAG, "WebView error [" + errorCode + "]: " + description + " for " + failingUrl);
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (webLoadingProgress != null) {
                    webLoadingProgress.setProgress(newProgress);
                    if (newProgress >= 100) {
                        webLoadingProgress.setVisibility(View.GONE);
                    } else {
                        webLoadingProgress.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    runOnUiThread(() -> request.grant(request.getResources()));
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("StudioWebConsole", consoleMessage.message() + " ["
                        + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + "]");
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;
                Intent intent = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    intent = fileChooserParams.createIntent();
                }
                if (intent == null) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(intent, REQUEST_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    mFilePathCallback = null;
                    return false;
                }
            }
        });
    }

    private boolean isGoogleSessionActive() {
        try {
            CookieManager cm = CookieManager.getInstance();
            String cookies = cm.getCookie("https://accounts.google.com");
            if (cookies == null) {
                cookies = cm.getCookie("https://antigravity.google.com");
            }
            if (cookies != null) {
                return cookies.contains("SID=") || cookies.contains("SSID=")
                        || cookies.contains("SAPISID=") || cookies.contains("OSID=");
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean handleUrlOverride(WebView view, Uri uri) {
        if (uri == null) return false;
        String urlStr = uri.toString();

        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase(java.util.Locale.US);

        // 1. Intercept OAuth success callbacks delivered via deep link scheme
        if ("antigravity".equals(scheme) || "antigravity-dev".equals(scheme)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            oauthManager.handleDeepLink(this, intent, () -> {
                Toast.makeText(this, "Authentication successful! Establishing Remote Control session...", Toast.LENGTH_SHORT).show();
                if (CoreRuntimeState.isValidSessionUrl(CoreRuntimeState.serverUrl)) {
                    loadSessionUrl(CoreRuntimeState.serverUrl);
                }
            });
            return true;
        }

        if (scheme.equals("http") || scheme.equals("https")) {
            String host = uri.getHost();
            if (host != null) {
                String hostLower = host.toLowerCase(java.util.Locale.US);
                // Allow Google auth (accounts.google.com), Antigravity Studio, and related Google assets directly inside WebView
                if (hostLower.contains("google") || hostLower.contains("gstatic")
                        || hostLower.contains("googleapis") || hostLower.contains("googleusercontent")) {
                    return false;
                }
            }
            // For other external links, launch external browser so user keeps their Studio session active
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Cannot handle external URL: " + uri, e);
                return false;
            }
        }

        // External intents (mailto, tel, market, etc.)
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Cannot handle intent for URI: " + uri, e);
            return true;
        }
    }

    private void buildDashboardView(boolean dark) {
        int cardColor = dark ? Color.parseColor("#1E1F24") : Color.parseColor("#FFFFFF");
        int textColor = dark ? Color.parseColor("#FFFFFF") : Color.parseColor("#1A1A1A");
        int subTextColor = dark ? Color.parseColor("#A0A5AD") : Color.parseColor("#666666");
        int accentColor = Color.parseColor("#1A73E8");

        dashboardContainer = new ScrollView(this);
        dashboardContainer.setFillViewport(true);
        dashboardContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        // 1. Header (Logo + Title)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(16));

        ImageView logo = new ImageView(this);
        int logoRes = getResources().getIdentifier("ic_launcher_logo", "drawable", getPackageName());
        if (logoRes == 0) logoRes = getResources().getIdentifier("splash_logo", "drawable", getPackageName());
        if (logoRes != 0) logo.setImageResource(logoRes);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        logoLp.setMargins(0, 0, dp(12), 0);
        header.addView(logo, logoLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Antigravity for Android");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        titleBox.addView(title);

        TextView subTitle = new TextView(this);
        subTitle.setText("Official Bionic CLI • Embedded Studio");
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subTitle.setTextColor(subTextColor);
        titleBox.addView(subTitle);
        header.addView(titleBox);

        layout.addView(header);

        // 2. Status Card
        LinearLayout statusCard = createCard(cardColor, dp(12));
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView statusLabel = new TextView(this);
        statusLabel.setText("Status: ");
        statusLabel.setTextColor(subTextColor);
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusRow.addView(statusLabel);

        statusBadge = new TextView(this);
        statusBadge.setText("INITIALIZING");
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
        statusRow.addView(statusBadge);
        statusCard.addView(statusRow);

        infoText = new TextView(this);
        infoText.setText("Engine: Google Antigravity CLI (ARM64)");
        infoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        infoText.setTextColor(subTextColor);
        infoText.setPadding(0, dp(8), 0, 0);
        statusCard.addView(infoText);

        sessionUrlText = new TextView(this);
        sessionUrlText.setText("Session: None");
        sessionUrlText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sessionUrlText.setTextColor(accentColor);
        sessionUrlText.setPadding(0, dp(4), 0, 0);
        statusCard.addView(sessionUrlText);

        layout.addView(statusCard);

        // 3. Download Progress Area (Hidden by default unless downloading)
        downloadStatusText = new TextView(this);
        downloadStatusText.setText("Downloading Antigravity CLI...");
        downloadStatusText.setTextColor(textColor);
        downloadStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        downloadStatusText.setVisibility(View.GONE);
        downloadStatusText.setPadding(0, dp(12), 0, dp(4));
        layout.addView(downloadStatusText);

        downloadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setMax(100);
        downloadProgress.setVisibility(View.GONE);
        layout.addView(downloadProgress);

        // 4. Primary Hero Button: OPEN EMBEDDED STUDIO
        openRemoteButton = new Button(this);
        openRemoteButton.setText("🖥️  OPEN EMBEDDED STUDIO");
        openRemoteButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        openRemoteButton.setTypeface(Typeface.DEFAULT_BOLD);
        openRemoteButton.setTextColor(Color.WHITE);
        openRemoteButton.setBackground(createButtonDrawable(accentColor, dp(10)));
        openRemoteButton.setOnClickListener(v -> openEmbeddedStudio());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        btnLp.setMargins(0, dp(16), 0, dp(6));
        layout.addView(openRemoteButton, btnLp);

        // 4b. Secondary Action: Open in External Chrome
        openExternalLink = new Button(this);
        openExternalLink.setText("Launch in External Chrome");
        openExternalLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        openExternalLink.setTextColor(subTextColor);
        openExternalLink.setBackground(null);
        openExternalLink.setOnClickListener(v -> openInExternalBrowser());
        LinearLayout.LayoutParams extLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        extLp.setMargins(0, 0, 0, dp(8));
        layout.addView(openExternalLink, extLp);

        // 5. Controls Row (Start/Stop, Update)
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);

        startStopButton = new Button(this);
        startStopButton.setText("START");
        startStopButton.setTextColor(textColor);
        startStopButton.setBackground(createButtonDrawable(cardColor, dp(8)));
        startStopButton.setOnClickListener(v -> toggleService());
        LinearLayout.LayoutParams ctrlLp1 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        ctrlLp1.setMargins(0, 0, dp(6), 0);
        controlsRow.addView(startStopButton, ctrlLp1);

        downloadButton = new Button(this);
        downloadButton.setText("UPDATE CLI");
        downloadButton.setTextColor(textColor);
        downloadButton.setBackground(createButtonDrawable(cardColor, dp(8)));
        downloadButton.setOnClickListener(v -> triggerDownload());
        LinearLayout.LayoutParams ctrlLp2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        ctrlLp2.setMargins(dp(6), 0, 0, 0);
        controlsRow.addView(downloadButton, ctrlLp2);

        layout.addView(controlsRow);

        // 6. Log Console
        LinearLayout logCard = createCard(dark ? Color.parseColor("#0C0D0E") : Color.parseColor("#ECEFF1"), dp(8));
        logCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams logCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        logCardLp.setMargins(0, dp(16), 0, 0);

        LinearLayout logHeader = new LinearLayout(this);
        logHeader.setOrientation(LinearLayout.HORIZONTAL);
        logHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView logTitle = new TextView(this);
        logTitle.setText("CONSOLE LOG");
        logTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        logTitle.setTextColor(subTextColor);
        logHeader.addView(logTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button copyBtn = new Button(this);
        copyBtn.setText("COPY");
        copyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        copyBtn.setTextColor(textColor);
        copyBtn.setBackground(null);
        copyBtn.setOnClickListener(v -> copyLogs());
        logHeader.addView(copyBtn);
        logCard.addView(logHeader);

        logScrollView = new ScrollView(this);
        logTextView = new TextView(this);
        logTextView.setText("Ready.\n");
        logTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        logTextView.setTypeface(Typeface.MONOSPACE);
        logTextView.setTextColor(dark ? Color.parseColor("#00E676") : Color.parseColor("#1B5E20"));
        logScrollView.addView(logTextView);
        logCard.addView(logScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));

        layout.addView(logCard, logCardLp);

        dashboardContainer.addView(layout);
    }

    public void showStudioView() {
        currentView = VIEW_STUDIO;
        if (dashboardContainer != null) dashboardContainer.setVisibility(View.GONE);
        if (studioContainer != null) studioContainer.setVisibility(View.VISIBLE);
        if (webView != null) webView.requestFocus();
    }

    public void showDashboardView() {
        currentView = VIEW_DASHBOARD;
        if (studioContainer != null) studioContainer.setVisibility(View.GONE);
        if (dashboardContainer != null) dashboardContainer.setVisibility(View.VISIBLE);
    }

    public void loadSessionUrl(final String url) {
        if (url == null || url.trim().isEmpty()) return;
        final String target = url.trim();

        // Safety check: Never load an auth URL into WebView!
        if (oauthManager.isAuthUrl(target)) {
            Log.w(TAG, "Auth URL passed to loadSessionUrl -> redirecting to external browser");
            oauthManager.openInBrowser(this, target);
            return;
        }

        // Only valid session URLs may be loaded in WebView
        if (!CoreRuntimeState.isValidSessionUrl(target)) {
            Log.w(TAG, "Rejecting invalid session URL candidate: " + SensitiveDataRedactor.safeUrl(target));
            return;
        }

        handler.post(() -> {
            CoreRuntimeState.serverUrl = target;
            CoreRuntimeState.authPending = false;
            CoreRuntimeState.pendingAuthUrl = null;
            if (sessionUrlText != null) {
                sessionUrlText.setText("Session: " + target);
            }
            if (webView != null) {
                String current = webView.getUrl();
                if (current == null || !current.equals(target)) {
                    webView.loadUrl(target);
                }
            }
            showStudioView();
        });
    }

    private void openEmbeddedStudio() {
        if (!CliDownloader.isInstalled(this)) {
            Toast.makeText(this, "Please install the Antigravity CLI first.", Toast.LENGTH_SHORT).show();
            triggerDownload();
            return;
        }
        if (!serviceRunning) {
            startCoreService();
        }
        String sessionUrl = CoreRuntimeState.serverUrl;
        if (CoreRuntimeState.isValidSessionUrl(sessionUrl)) {
            if (webView != null) {
                String current = webView.getUrl();
                if (current == null || !current.equals(sessionUrl)) {
                    webView.loadUrl(sessionUrl);
                }
            }
            showStudioView();
        } else if (CoreRuntimeState.authPending) {
            Toast.makeText(this, "Google authentication required. Opening browser...", Toast.LENGTH_SHORT).show();
            if (CoreRuntimeState.pendingAuthUrl != null) {
                oauthManager.openInBrowser(this, CoreRuntimeState.pendingAuthUrl);
            }
        } else {
            Toast.makeText(this, "Remote Control session connecting, please wait...", Toast.LENGTH_SHORT).show();
        }
    }

    private void openInExternalBrowser() {
        String sessionUrl = CoreRuntimeState.serverUrl;
        if (CoreRuntimeState.isValidSessionUrl(sessionUrl)) {
            oauthManager.openInBrowser(this, sessionUrl);
        } else if (CoreRuntimeState.authPending && CoreRuntimeState.pendingAuthUrl != null) {
            oauthManager.openInBrowser(this, CoreRuntimeState.pendingAuthUrl);
        } else {
            Toast.makeText(this, "Remote Control session not ready yet.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDashboard() {
        boolean installed = CliDownloader.isInstalled(this);
        String lastError = CoreRuntimeState.lastError;
        String sessionUrl = CoreRuntimeState.serverUrl;
        boolean authPending = CoreRuntimeState.authPending;
        boolean hasToken = OAuthManager.hasToken(this);

        if (!installed) {
            setBadge("NOT INSTALLED", Color.parseColor("#FF5722"));
            infoText.setText("Antigravity CLI is not installed yet.");
            openRemoteButton.setEnabled(false);
            openRemoteButton.setAlpha(0.5f);
            startStopButton.setEnabled(false);
            sessionUrlText.setText("Session: Not available");
            if (studioStatusDot != null) {
                studioStatusDot.setText("● Not Installed");
                studioStatusDot.setTextColor(Color.parseColor("#FF5722"));
            }
        } else if (lastError != null) {
            setBadge("ERROR", Color.parseColor("#D32F2F"));
            infoText.setText(lastError);
            openRemoteButton.setEnabled(true);
            openRemoteButton.setAlpha(1.0f);
            openRemoteButton.setText("🖥️  OPEN EMBEDDED STUDIO");
            openRemoteButton.setOnClickListener(v -> openEmbeddedStudio());
            startStopButton.setEnabled(true);
            startStopButton.setText("RETRY");
            if (studioStatusDot != null) {
                studioStatusDot.setText("● Error");
                studioStatusDot.setTextColor(Color.parseColor("#D32F2F"));
            }
        } else if (CoreRuntimeState.isValidSessionUrl(sessionUrl)) {
            setBadge("READY", Color.parseColor("#2E7D32"));
            String userTag = CoreRuntimeState.authenticatedEmail != null
                    ? " (" + CoreRuntimeState.authenticatedEmail + ")"
                    : "";
            infoText.setText("Google Antigravity CLI active & connected" + userTag);
            sessionUrlText.setText("Session: " + sessionUrl);
            openRemoteButton.setEnabled(true);
            openRemoteButton.setAlpha(1.0f);
            openRemoteButton.setText("🖥️  OPEN EMBEDDED STUDIO");
            openRemoteButton.setOnClickListener(v -> openEmbeddedStudio());
            startStopButton.setEnabled(true);
            startStopButton.setText("STOP");
            serviceRunning = true;
            if (studioStatusDot != null) {
                studioStatusDot.setText("● Live Connected");
                studioStatusDot.setTextColor(Color.parseColor("#00E676"));
            }
        } else if (CoreRuntimeState.onboardingPending) {
            setBadge("INITIALIZING", Color.parseColor("#1976D2"));
            infoText.setText("Completing CLI onboarding & welcome wizard...");
            sessionUrlText.setText("Session: Initializing Remote Control session");
            openRemoteButton.setEnabled(true);
            openRemoteButton.setAlpha(1.0f);
            openRemoteButton.setText("⚡  FINISH SETUP / ENTER");
            openRemoteButton.setOnClickListener(v -> {
                new Thread(() -> {
                    CoreRuntimeState.submitStdin("\t");
                    try { Thread.sleep(120); } catch (Exception ignored) {}
                    CoreRuntimeState.submitStdin("\t");
                    try { Thread.sleep(120); } catch (Exception ignored) {}
                    CoreRuntimeState.submitStdin("\r\n");
                }).start();
                Toast.makeText(this, "Completing setup...", Toast.LENGTH_SHORT).show();
            });
            startStopButton.setEnabled(true);
            startStopButton.setText("STOP");
            if (studioStatusDot != null) {
                studioStatusDot.setText("● Initializing...");
                studioStatusDot.setTextColor(Color.parseColor("#1976D2"));
            }
        } else if (authPending || (!hasToken && serviceRunning && sessionUrl == null)) {
            setBadge("AUTH NEEDED", Color.parseColor("#E65100"));
            openRemoteButton.setEnabled(true);
            openRemoteButton.setAlpha(1.0f);
            if (CoreRuntimeState.pendingAuthUrl != null) {
                infoText.setText("Google authentication in progress. Complete login in Chrome or enter code.");
                sessionUrlText.setText("Session: Awaiting authorization code");
                openRemoteButton.setText("🔑  ENTER AUTH CODE");
                openRemoteButton.setOnClickListener(v -> showAuthCodeDialog(null));
            } else {
                infoText.setText("Google authentication required. Sign in via external browser.");
                sessionUrlText.setText("Session: Awaiting Google authentication");
                openRemoteButton.setText("🔐  SIGN IN WITH GOOGLE");
                openRemoteButton.setOnClickListener(v -> {
                    Toast.makeText(this, "Starting Google login flow...", Toast.LENGTH_SHORT).show();
                    if (!serviceRunning) startCoreService();
                });
            }
            startStopButton.setEnabled(true);
            startStopButton.setText("STOP");
            if (studioStatusDot != null) {
                studioStatusDot.setText("● Awaiting Google Login");
                studioStatusDot.setTextColor(Color.parseColor("#E65100"));
            }
        } else if (serviceRunning) {
            setBadge("CONNECTING", Color.parseColor("#F57C00"));
            infoText.setText("Starting CLI process (--remote-control)...");
            sessionUrlText.setText("Session: Establishing connection...");
            openRemoteButton.setEnabled(true);
            openRemoteButton.setAlpha(1.0f);
            openRemoteButton.setText("🖥️  OPEN EMBEDDED STUDIO");
            openRemoteButton.setOnClickListener(v -> openEmbeddedStudio());
            startStopButton.setEnabled(true);
            startStopButton.setText("STOP");
            if (studioStatusDot != null) {
                studioStatusDot.setText("● Connecting...");
                studioStatusDot.setTextColor(Color.parseColor("#F57C00"));
            }
        } else {
            setBadge("STOPPED", Color.parseColor("#757575"));
            infoText.setText("CLI process is idle");
            sessionUrlText.setText("Session: None");
            openRemoteButton.setEnabled(true);
            openRemoteButton.setAlpha(1.0f);
            openRemoteButton.setText("🖥️  OPEN EMBEDDED STUDIO");
            openRemoteButton.setOnClickListener(v -> openEmbeddedStudio());
            startStopButton.setEnabled(true);
            startStopButton.setText("START");
            if (studioStatusDot != null) {
                studioStatusDot.setText("● Idle");
                studioStatusDot.setTextColor(Color.parseColor("#757575"));
            }
        }

        readRecentLogs();
    }

    private void setBadge(String text, int color) {
        statusBadge.setText(text);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(4));
        statusBadge.setBackground(gd);
        statusBadge.setTextColor(Color.WHITE);
    }

    private void toggleService() {
        if (serviceRunning) {
            CoreServiceLauncher.stop(this);
            serviceRunning = false;
            CoreRuntimeState.serverUrl = null;
            CoreRuntimeState.lastError = null;
            updateDashboard();
        } else {
            startCoreService();
        }
    }

    private void startCoreService() {
        if (!CliDownloader.isInstalled(this)) {
            promptDownload();
            return;
        }
        serviceRunning = true;
        updateDashboard();
        try {
            CoreServiceLauncher.start(this);
        } catch (Exception e) {
            Log.e(TAG, "Cannot start service", e);
            CoreRuntimeState.lastError = e.getMessage();
            updateDashboard();
        }
    }

    private void promptDownload() {
        downloadButton.setVisibility(View.VISIBLE);
        triggerDownload();
    }

    private void triggerDownload() {
        downloadStatusText.setVisibility(View.VISIBLE);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setProgress(0);

        CliDownloader.downloadAndInstall(this, new CliDownloader.DownloadListener() {
            @Override
            public void onProgress(final int percent, final String message) {
                handler.post(() -> {
                    downloadProgress.setProgress(percent);
                    downloadStatusText.setText(message + " (" + percent + "%)");
                });
            }

            @Override
            public void onSuccess(final File binary) {
                handler.post(() -> {
                    downloadStatusText.setText("Installed successfully!");
                    downloadProgress.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Antigravity CLI installed!", Toast.LENGTH_SHORT).show();
                    updateDashboard();
                    startCoreService();
                });
            }

            @Override
            public void onError(final String error) {
                handler.post(() -> {
                    downloadStatusText.setText("Download failed: " + error);
                    Toast.makeText(MainActivity.this, "Download error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void readRecentLogs() {
        File logFile = new File(getFilesDir(), "logs/core.log");
        if (!logFile.isFile()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                count++;
                if (count > 80) {
                    sb.delete(0, sb.indexOf("\n") + 1);
                }
            }
            logTextView.setText(sb.toString());
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        } catch (Exception ignored) {
        }
    }

    private void copyLogs() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Antigravity Logs", logTextView.getText()));
            Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (!android.os.Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            } catch (Exception ignored) {
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_STORAGE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_CHOOSER) {
            if (mFilePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    ClipData clipData = data.getClipData();
                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                mFilePathCallback.onReceiveValue(results);
                mFilePathCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (currentView == VIEW_STUDIO) {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return;
            }
            showDashboardView();
            return;
        }
        super.onBackPressed();
    }

    private String lastSubmittedCode = "";

    private void checkClipboardForAuthCode() {
        if (!CoreRuntimeState.authPending) {
            return;
        }
        try {
            android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cb.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    String clip = text.toString().trim();
                    if (clip.startsWith("4/") && clip.length() >= 20 && !clip.equals(lastSubmittedCode)) {
                        showAuthCodeDialog(clip);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void showAuthCodeDialog(String prefill) {
        if (isFinishing()) return;
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("4/0Aean...");
        input.setSingleLine(true);
        if (prefill != null) {
            input.setText(prefill);
            input.setSelection(prefill.length());
        }
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        container.addView(input);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Google Authorization Code")
                .setMessage("Paste the authorization code displayed in your browser after signing in:")
                .setView(container)
                .setPositiveButton("Submit", (dialog, which) -> {
                    String code = input.getText().toString().trim();
                    if (!code.isEmpty()) {
                        submitAuthCode(code);
                    }
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Open Chrome", (dialog, which) -> {
                    if (CoreRuntimeState.pendingAuthUrl != null) {
                        oauthManager.openInBrowser(this, CoreRuntimeState.pendingAuthUrl);
                    }
                })
                .show();
    }

    private void submitAuthCode(String code) {
        lastSubmittedCode = code;
        Toast.makeText(this, "Submitting authentication code to Antigravity CLI...", Toast.LENGTH_SHORT).show();
        boolean sent = CoreRuntimeState.submitStdin(code + "\r\n");
        if (!sent) {
            Log.w(TAG, "Failed submitting auth code via stdinWriter");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeActivity = this;
        if (webView != null) {
            webView.onResume();
        }
        checkClipboardForAuthCode();
        handler.removeCallbacks(pollStatus);
        handler.post(pollStatus);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        handler.removeCallbacks(pollStatus);
    }

    @Override
    protected void onDestroy() {
        if (activeActivity == this) {
            activeActivity = null;
        }
        handler.removeCallbacks(pollStatus);
        if (webView != null) {
            if (studioContainer != null) {
                studioContainer.removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private LinearLayout createCard(int color, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        card.setBackground(gd);
        return card;
    }

    private GradientDrawable createButtonDrawable(int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
