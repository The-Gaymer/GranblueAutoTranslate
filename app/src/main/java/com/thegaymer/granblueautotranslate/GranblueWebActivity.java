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
    private String lastObservedUrl = "";

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

        String ua = settings.getUserAgentString();
        ua = ua.replace("; wv", "").replace(" Version/4.0", "");
        settings.setUserAgentString(ua);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                handleGranblueNavigation(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleGranblueNavigation(url);
            }
        });
    }

    private boolean isGranblueUrl(String url) {
        return url != null && url.startsWith("https://steam.granbluefantasy.com/");
    }

    private void handleGranblueNavigation(String url) {
        if (!isGranblueUrl(url)) return;
        if (url.equals(lastObservedUrl)) return;

        lastObservedUrl = url;
        scheduleTranslation(url);
    }

    private void scheduleTranslation(final String expectedUrl) {
        if (pendingTranslation != null) {
            handler.removeCallbacks(pendingTranslation);
        }

        pendingTranslation = () -> {
            if (webView == null) return;
            String currentUrl = webView.getUrl();
            if (!expectedUrl.equals(currentUrl) || !isGranblueUrl(currentUrl)) return;
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
                "style.textContent='.goog-te-banner-frame,iframe.goog-te-banner-frame,.goog-te-banner-frame.skiptranslate,.VIpgJd-ZVi9od-ORHb,.VIpgJd-ZVi9od-ORHb-OEVmcd,iframe[class*=\\\"VIpgJd-ZVi9od-ORHb\\\"],body>div.skiptranslate,body>iframe.skiptranslate{display:none!important;visibility:hidden!important;height:0!important;max-height:0!important;overflow:hidden!important;}#goog-gt-tt,.goog-te-balloon-frame,.goog-te-gadget{display:none!important;}html,body{top:0!important;margin-top:0!important;}.__gb_fr_pending_text{color:transparent!important;-webkit-text-fill-color:transparent!important;text-shadow:none!important;}';" +
                "document.documentElement.appendChild(style);}" +
                "function hideGoogleBar(){" +
                "var q=['.goog-te-banner-frame','iframe.goog-te-banner-frame','.VIpgJd-ZVi9od-ORHb','.VIpgJd-ZVi9od-ORHb-OEVmcd','iframe[class*=\\\"VIpgJd-ZVi9od-ORHb\\\"]','body>div.skiptranslate','body>iframe.skiptranslate'];" +
                "for(var i=0;i<q.length;i++){var n=document.querySelectorAll(q[i]);for(var j=0;j<n.length;j++){if(n[j].id==='google_translate_element')continue;n[j].style.setProperty('display','none','important');n[j].style.setProperty('visibility','hidden','important');n[j].style.setProperty('height','0','important');n[j].style.setProperty('max-height','0','important');}}" +
                "document.documentElement.style.setProperty('top','0px','important');document.documentElement.style.setProperty('margin-top','0px','important');" +
                "if(document.body){document.body.style.setProperty('top','0px','important');document.body.style.setProperty('margin-top','0px','important');}" +
                "}" +
                "function scheduleHide(){var d=[0,100,250,500,1000,2000,4000,8000];for(var i=0;i<d.length;i++){setTimeout(hideGoogleBar,d[i]);}}" +
                "function isTextTarget(el){if(!el||el.nodeType!==1)return false;if(el.id==='google_translate_element'||el.closest('#google_translate_element'))return false;if(el.closest('.skiptranslate'))return false;var tag=el.tagName;if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT'||tag==='INPUT'||tag==='TEXTAREA'||tag==='SELECT'||tag==='OPTION')return false;if(el.children.length!==0)return false;var t=(el.textContent||'').trim();return t.length>0&&/[A-Za-z]/.test(t);}" +
                "function hideTextFor200ms(el){if(!isTextTarget(el)||el.classList.contains('__gb_fr_pending_text'))return;el.classList.add('__gb_fr_pending_text');setTimeout(function(){el.classList.remove('__gb_fr_pending_text');},200);}" +
                "function scanTextNode(node){if(!node)return;if(node.nodeType===3){hideTextFor200ms(node.parentElement);return;}if(node.nodeType!==1)return;hideTextFor200ms(node);var all=node.querySelectorAll('*');for(var i=0;i<all.length;i++)hideTextFor200ms(all[i]);}" +
                "function hideExistingTextFor200ms(){if(!document.body)return;var all=document.body.querySelectorAll('*');for(var i=0;i<all.length;i++)hideTextFor200ms(all[i]);}" +
                "if(!window.__gbTextObserver){window.__gbTextObserver=new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var m=ms[i];if(m.type==='characterData'){hideTextFor200ms(m.target.parentElement);}else if(m.type==='childList'){for(var j=0;j<m.addedNodes.length;j++)scanTextNode(m.addedNodes[j]);}}});window.__gbTextObserver.observe(document.documentElement,{subtree:true,childList:true,characterData:true});}" +
                "scheduleHide();" +
                "document.cookie='googtrans=/en/fr;path=/';" +
                "function chooseFrench(){var c=document.querySelector('.goog-te-combo');if(!c){scheduleHide();return false;}hideExistingTextFor200ms();c.value='fr';c.dispatchEvent(new Event('change',{bubbles:true}));scheduleHide();return true;}" +
                "function translateCurrentView(){var d=[100,300,700,1500];for(var i=0;i<d.length;i++){setTimeout(function(){chooseFrench();},d[i]);}scheduleHide();}" +
                "window.__gbChooseFrench=chooseFrench;window.__gbHideGoogleBar=hideGoogleBar;window.__gbTranslateCurrentView=translateCurrentView;" +
                "if(!window.__gbSpaTranslationInstalled){window.__gbSpaTranslationInstalled=true;" +
                "window.addEventListener('hashchange',translateCurrentView);window.addEventListener('popstate',translateCurrentView);" +
                "if(window.history&&history.pushState){var p=history.pushState;history.pushState=function(){var r=p.apply(this,arguments);translateCurrentView();return r;};}" +
                "if(window.history&&history.replaceState){var r=history.replaceState;history.replaceState=function(){var v=r.apply(this,arguments);translateCurrentView();return v;};}" +
                "}" +
                "if(!document.getElementById('google_translate_element')){var d=document.createElement('div');d.id='google_translate_element';d.style.display='none';document.documentElement.appendChild(d);}" +
                "if(window.google&&google.translate&&google.translate.TranslateElement){" +
                "try{if(!window.__gbTranslateWidget){window.__gbTranslateWidget=new google.translate.TranslateElement({pageLanguage:'en',includedLanguages:'fr',autoDisplay:false},'google_translate_element');}}catch(e){}" +
                "translateCurrentView();scheduleHide();return;}" +
                "window.googleTranslateElementInit=function(){try{window.__gbTranslateWidget=new google.translate.TranslateElement({pageLanguage:'en',includedLanguages:'fr',autoDisplay:false},'google_translate_element');}catch(e){}translateCurrentView();scheduleHide();};" +
                "if(!document.getElementById('__gb_google_translate_script')){var s=document.createElement('script');s.id='__gb_google_translate_script';s.src='https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';s.async=true;document.head.appendChild(s);}" +
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
