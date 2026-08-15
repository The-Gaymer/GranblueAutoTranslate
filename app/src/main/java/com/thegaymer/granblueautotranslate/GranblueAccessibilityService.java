package com.thegaymer.granblueautotranslate;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
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

    private boolean translationTriggered = false;
    private String lastUrl = "";
    private long lastClick = 0;
    private long lastDismissAttempt = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!CHROME.equals(event.getPackageName())) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            String treeText = flattenText(root);
            String lowTree = treeText.toLowerCase(Locale.ROOT);
            if (!lowTree.contains(DOMAIN)) return;

            String currentUrl = findCurrentUrl(root);
            if (!currentUrl.isEmpty() && !currentUrl.equals(lastUrl)) {
                lastUrl = currentUrl;
                translationTriggered = false;
                log("Nouvelle page Granblue: " + currentUrl);
            }

            // IMPORTANT: this is the v5 detection that was actually working.
            // Keep it broad because Chrome does not always expose the exact same
            // accessibility label for its Translate control.
            if (!translationTriggered) {
                AccessibilityNodeInfo candidate = findTranslateCandidate(root);
                if (candidate != null) {
                    try {
                        long now = System.currentTimeMillis();
                        if (now - lastClick > 1800) {
                            lastClick = now;
                            log("Bouton de traduction trouvé: " + describe(candidate));
                            boolean ok = candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            log("ACTION_CLICK = " + ok);
                            if (ok) {
                                translationTriggered = true;
                                scheduleDismissTranslationBanner();
                            }
                        }
                    } finally {
                        candidate.recycle();
                    }
                }
            }

            // Banner handling is independent and must never prevent translation.
            if (containsTranslatedBanner(lowTree)) {
                dismissTranslationBanner(root);
            }
        } finally {
            root.recycle();
        }
    }

    private boolean containsTranslatedBanner(String text) {
        return text.contains("page traduite")
                || text.contains("page translated")
                || text.contains("traduit en français")
                || text.contains("translated to french")
                || text.contains("page traduite en français")
                || text.contains("page translated to french");
    }

    private void scheduleDismissTranslationBanner() {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            try {
                if (!CHROME.equals(root.getPackageName())) return;
                String text = flattenText(root).toLowerCase(Locale.ROOT);
                if (text.contains(DOMAIN) && containsTranslatedBanner(text)) {
                    dismissTranslationBanner(root);
                }
            } finally {
                root.recycle();
            }
        }, 300);
    }

    private void dismissTranslationBanner(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        if (now - lastDismissAttempt < 500) return;
        lastDismissAttempt = now;

        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);

        try {
            for (AccessibilityNodeInfo node : nodes) {
                String text = nodeText(node).toLowerCase(Locale.ROOT);
                if (!isTranslatedBannerText(text)) continue;
                if (tryDismissChain(node)) {
                    log("Bannière Chrome masquée via ACTION_DISMISS: " + nodeText(node));
                    return;
                }
            }
        } finally {
            for (AccessibilityNodeInfo node : nodes) node.recycle();
        }
    }

    private boolean isTranslatedBannerText(String text) {
        return text.contains("page traduite")
                || text.contains("page translated")
                || text.contains("traduit en français")
                || text.contains("translated to french")
                || text.contains("page traduite en français")
                || text.contains("page translated to french");
    }

    private boolean tryDismissChain(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (hasDismissAction(current)) {
                boolean ok = current.performAction(AccessibilityNodeInfo.ACTION_DISMISS);
                current.recycle();
                return ok;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        return false;
    }

    private boolean hasDismissAction(AccessibilityNodeInfo node) {
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            if (action.getId() == AccessibilityNodeInfo.ACTION_DISMISS) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findTranslateCandidate(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);

        AccessibilityNodeInfo best = null;
        int bestScore = -1;

        try {
            for (AccessibilityNodeInfo node : nodes) {
                String text = nodeText(node);
                if (!isTranslationLabel(text)) continue;

                AccessibilityNodeInfo target = findClickableTarget(node);
                if (target == null) {
                    log("Libellé traduction trouvé mais non cliquable: " + text);
                    continue;
                }

                int score = 10;
                String low = text.toLowerCase(Locale.ROOT);
                if (low.contains("traduire la page") || low.contains("translate page")) score += 20;
                if (low.contains("google traduction") || low.contains("google translate")) score += 15;
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
        } finally {
            for (AccessibilityNodeInfo node : nodes) node.recycle();
        }
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
        if (low.isEmpty()) return false;

        // Exact v5 principle: broad matching, because Chrome's accessibility
        // tree may expose "Traduire", "Traduction", "Google Traduction",
        // "Translate", etc. in different nodes/versions.
        return low.contains("traduire")
                || low.contains("traduction")
                || low.contains("translate")
                || low.contains("translation")
                || low.contains("google traduction")
                || low.contains("google translate");
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

    private String describe(AccessibilityNodeInfo node) {
        return "text=" + node.getText() + ", desc=" + node.getContentDescription()
                + ", class=" + node.getClassName() + ", clickable=" + node.isClickable();
    }

    private void log(String s) {
        Log.d(TAG, s);
        String old = getSharedPreferences("log", MODE_PRIVATE).getString("text", "");
        String line = System.currentTimeMillis() + "  " + s + "\n";
        String combined = line + old;
        if (combined.length() > 12000) combined = combined.substring(0, 12000);
        getSharedPreferences("log", MODE_PRIVATE).edit().putString("text", combined).apply();
    }

    @Override public void onInterrupt() {
        log("Service interrompu par Android.");
    }
}
