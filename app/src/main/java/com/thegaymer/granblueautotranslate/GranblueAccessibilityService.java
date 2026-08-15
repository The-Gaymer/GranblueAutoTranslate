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
    private String lastTranslatedUrl = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!CHROME.equals(event.getPackageName())) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            String treeText = flattenText(root);
            boolean granblue = treeText.toLowerCase(Locale.ROOT).contains(DOMAIN);
            if (!granblue) return;

            log("Chrome + Granblue détecté (" + event.getEventType() + ")");

            // Chrome displays a "Page traduite / Annuler" banner after translation.
            // If it is already present, do NOT click Traduire again: repeated clicks
            // keep reopening/spamming that banner and make navigation painful.
            String lowTree = treeText.toLowerCase(Locale.ROOT);
            if (lowTree.contains("page traduite") && lowTree.contains("annuler")) {
                return;
            }

            String currentUrl = findCurrentUrl(root);
            if (currentUrl.isEmpty()) {
                // If Chrome doesn't expose the URL in this accessibility event,
                // retain the old short debounce as a safe fallback.
                if (System.currentTimeMillis() - lastClick < 5000) return;
            } else if (currentUrl.equals(lastTranslatedUrl)) {
                // Already translated this page. The translation remains active while
                // the user navigates within the same document/URL.
                return;
            }

            AccessibilityNodeInfo candidate = findTranslateCandidate(root);
            if (candidate != null) {
                try {
                    long now = System.currentTimeMillis();
                    if (now - lastClick > 1500) {
                        log("Bouton de traduction trouvé: " + describe(candidate));
                        boolean ok = candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        log("ACTION_CLICK = " + ok);
                        if (ok) {
                            lastClick = now;
                            if (!currentUrl.isEmpty()) {
                                lastTranslatedUrl = currentUrl;
                                log("Traduction déclenchée pour: " + currentUrl);
                            }
                        }
                    }
                } finally {
                    candidate.recycle();
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

        for (AccessibilityNodeInfo node : nodes) {
            if (!isTranslationLabel(nodeText(node))) continue;

            AccessibilityNodeInfo target = findClickableTarget(node);
            if (target == null) continue;

            int score = 10;
            if (node.isClickable()) score += 10;
            if ("android.widget.Button".contentEquals(target.getClassName())) score += 5;
            String viewId = target.getViewIdResourceName();
            if (viewId != null && viewId.startsWith(CHROME + ":")) score += 8;

            if (score > bestScore) {
                if (best != null) best.recycle();
                best = target;
                bestScore = score;
            } else {
                target.recycle();
            }
        }

        for (AccessibilityNodeInfo node : nodes) node.recycle();
        return best;
    }

    private String nodeText(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return ((text == null ? "" : text.toString()) + " "
                + (description == null ? "" : description.toString())).trim();
    }

    private boolean isTranslationLabel(String text) {
        String low = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return low.equals("traduire")
                || low.startsWith("traduire la page")
                || low.startsWith("traduire en ")
                || low.startsWith("traduire de ")
                || low.equals("translate")
                || low.startsWith("translate page")
                || low.startsWith("translate to ")
                || low.startsWith("translate from ");
    }

    private AccessibilityNodeInfo findClickableTarget(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current.isClickable() && current.isEnabled()) return current;
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        return null;
    }

    private String findCurrentUrl(AccessibilityNodeInfo node) {
        String viewId = node.getViewIdResourceName();
        if (viewId != null && viewId.toLowerCase(Locale.ROOT).contains("url_bar")) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String value = text != null ? text.toString() : (desc != null ? desc.toString() : "");
            if (value.contains(DOMAIN)) return value;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String result = findCurrentUrl(child);
                child.recycle();
                if (!result.isEmpty()) return result;
            }
        }
        return "";
    }

    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        out.add(AccessibilityNodeInfo.obtain(node));
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collect(child, out);
                child.recycle();
            }
        }
    }

    private String flattenText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) sb.append(node.getText()).append(' ');
        if (node.getContentDescription() != null) sb.append(node.getContentDescription()).append(' ');
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(flattenText(child)).append(' ');
                child.recycle();
            }
        }
        return sb.toString();
    }

    private String describe(AccessibilityNodeInfo node) {
        return "text=" + node.getText() + ", desc=" + node.getContentDescription()
                + ", class=" + node.getClassName() + ", clickable=" + node.isClickable();
    }

    private void log(String s) {
        Log.d(TAG, s);
        String old = getSharedPreferences("log", MODE_PRIVATE)
                .getString("text", "");
        String line = System.currentTimeMillis() + "  " + s + "\n";
        String combined = line + old;
        if (combined.length() > 12000) combined = combined.substring(0, 12000);
        getSharedPreferences("log", MODE_PRIVATE).edit().putString("text", combined).apply();
    }

    @Override public void onInterrupt() {
        log("Service interrompu par Android.");
    }
}
