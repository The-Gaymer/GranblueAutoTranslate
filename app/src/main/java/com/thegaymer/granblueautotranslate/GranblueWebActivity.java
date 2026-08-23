package com.thegaymer.granblueautotranslate;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class GranblueWebActivity extends Activity {
    private static final String GRANBLUE_URL = "https://steam.granbluefantasy.com/";
    private static final long TRANSLATION_DELAY_MS = 2000;

    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingTranslation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF000000);
        setContentView(webView);

        configureWebView();
        hideSystemUi();

        if (savedInstanceState == null) {
            webView.loadUrl(GRANBLUE_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Present the embedded WebView as ordinary mobile Chrome. Some web apps
        // change behaviour when the default "; wv" WebView token is present.
        String ua = settings.getUserAgentString();
        ua = ua.replace("; wv", "").replace(" Version/4.0", "");
        settings.setUserAgentString(ua);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isGranblueUrl(url)) {
                    scheduleTranslation();
                }
            }
        });
    }

    private boolean isGranblueUrl(String url) {
        return url != null && url.startsWith("https://steam.granbluefantasy.com/");
    }

    private void scheduleTranslation() {
        if (pendingTranslation != null) {
            handler.removeCallbacks(pendingTranslation);
        }

        pendingTranslation = () -> {
            if (webView == null || !isGranblueUrl(webView.getUrl())) return;
            injectGoogleTranslate();
        };

        handler.postDelayed(pendingTranslation, TRANSLATION_DELAY_MS);
    }

    private void injectGoogleTranslate() {
        String js =
                "(function(){" +
                "if(!location.hostname.endsWith('granbluefantasy.com'))return;" +
                "var style=document.getElementById('__gb_fr_style');" +
                "if(!style){style=document.createElement('style');style.id='__gb_fr_style';" +
                "style.textContent='.goog-te-banner-frame,iframe.goog-te-banner-frame,#goog-gt-tt,.goog-te-balloon-frame,.goog-te-gadget{display:none!important;}body{top:0!important;}';" +
                "document.documentElement.appendChild(style);}" +
                "document.cookie='googtrans=/en/fr;path=/';" +
                "function chooseFrench(){var c=document.querySelector('.goog-te-combo');if(!c)return false;c.value='fr';c.dispatchEvent(new Event('change',{bubbles:true}));return true;}" +
                "window.__gbChooseFrench=chooseFrench;" +
                "if(!document.getElementById('google_translate_element')){var d=document.createElement('div');d.id='google_translate_element';d.style.display='none';document.documentElement.appendChild(d);}" +
                "if(window.google&&google.translate&&google.translate.TranslateElement){" +
                "try{if(!window.__gbTranslateWidget){window.__gbTranslateWidget=new google.translate.TranslateElement({pageLanguage:'en',includedLanguages:'fr',autoDisplay:false},'google_translate_element');}}catch(e){}" +
                "setTimeout(chooseFrench,250);setTimeout(chooseFrench,800);return;}" +
                "window.googleTranslateElementInit=function(){try{window.__gbTranslateWidget=new google.translate.TranslateElement({pageLanguage:'en',includedLanguages:'fr',autoDisplay:false},'google_translate_element');}catch(e){}setTimeout(chooseFrench,250);setTimeout(chooseFrench,900);};" +
                "if(!document.getElementById('__gb_google_translate_script')){var s=document.createElement('script');s.id='__gb_google_translate_script';s.src='https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';s.async=true;document.head.appendChild(s);}" +
                "if(!window.__gbHashHook){window.__gbHashHook=true;window.addEventListener('hashchange',function(){setTimeout(function(){if(window.__gbChooseFrench)window.__gbChooseFrench();},2000);});}" +
                "})();";

        webView.evaluateJavascript(js, null);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (pendingTranslation != null) handler.removeCallbacks(pendingTranslation);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
