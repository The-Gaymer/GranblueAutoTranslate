package com.thegaymer.granblueautotranslate;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GranblueAccessibilityService extends AccessibilityService {
    private static final String TAG = "GranblueAutoTranslate";
    private static final String CHROME = "com.android.chrome";
    private static final String DOMAIN = "steam.granbluefantasy.com";
    private long lastClick = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!CHROME.equals(event.getPackageName())) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            String treeText = flattenText(root);
            boolean granblue = treeText.toLowerCase(Locale.ROOT).contains(DOMAIN);

            // Chrome may expose the current URL as an address-bar node.
            // We deliberately require the Granblue domain before acting.
            if (!granblue) return;

            log("Chrome + Granblue détecté (" + event.getEventType() + ")");

            AccessibilityNodeInfo candidate = findTranslateCandidate(root);
            if (candidate != null) {
                long now = System.currentTimeMillis();
                if (now - lastClick > 1800) {
                    lastClick = now;
                    log("Bouton de traduction trouvé: " + describe(candidate));
                    boolean ok = candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    log("ACTION_CLICK = " + ok);
                }
            }
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findTranslateCandidate(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);

        AccessibilityNodeInfo best = null;
        int bestScore = -1;

        for (AccessibilityNodeInfo n : nodes) {
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            String text = ((t == null ? "" : t.toString()) + " " +
                    (d == null ? "" : d.toString())).trim();
            String low = text.toLowerCase(Locale.ROOT);

            boolean exactFrench = low.equals("traduire") || low.equals("traduire la page");
            boolean exactEnglish = low.equals("translate") || low.equals("translate page");
            if (!exactFrench && !exactEnglish) continue;

            int score = 10;
            if (n.isClickable()) score += 10;
            if ("android.widget.Button".equals(n.getClassName())) score += 5;

            // Avoid page text that merely contains the word.
            if (!n.isClickable() && !n.isFocusable()) score -= 8;

            if (score > bestScore) {
                if (best != null) best.recycle();
                best = AccessibilityNodeInfo.obtain(n);
                bestScore = score;
            }
        }

        for (AccessibilityNodeInfo n : nodes) {
            if (n != best) n.recycle();
        }
        return best;
    }

    private void collect(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        out.add(AccessibilityNodeInfo.obtain(n));
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            if (child != null) {
                collect(child, out);
                child.recycle();
            }
        }
    }

    private String flattenText(AccessibilityNodeInfo n) {
        StringBuilder sb = new StringBuilder();
        if (n.getText() != null) sb.append(n.getText()).append(' ');
        if (n.getContentDescription() != null) sb.append(n.getContentDescription()).append(' ');
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                sb.append(flattenText(c)).append(' ');
                c.recycle();
            }
        }
        return sb.toString();
    }

    private String describe(AccessibilityNodeInfo n) {
        return "text=" + n.getText() + ", desc=" + n.getContentDescription()
                + ", class=" + n.getClassName() + ", clickable=" + n.isClickable();
    }

    private void log(String s) {
        Log.d(TAG, s);
        String old = getSharedPreferences("log", MODE_PRIVATE)
                .getString("text", "");
        String line = System.currentTimeMillis() + "  " + s + "\n";
        String combined = (line + old);
        if (combined.length() > 12000) combined = combined.substring(0, 12000);
        getSharedPreferences("log", MODE_PRIVATE).edit().putString("text", combined).apply();
    }

    @Override public void onInterrupt() {
        log("Service interrompu par Android.");
    }
}
