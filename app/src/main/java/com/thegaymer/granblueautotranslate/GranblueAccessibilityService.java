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
    private long lastDismissAttempt = 0;
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

            // Base = version 5: keep the broad translation-button detection that
            // was confirmed working. Banner handling must never block this path.
            AccessibilityNodeInfo candidate = findTranslateCandidate(root);
            if (candidate != null) {
                try {
                    long now = System.currentTimeMillis();
                    if (now - lastClick > 1800) {
                        lastClick = now;
                        log("Bouton de traduction trouvé: " + describe(candidate));
                        boolean ok = candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        log("ACTION_CLICK = " + ok);

                        // Chrome shows its "Page traduite" banner asynchronously.
                        // Try to dismiss that banner after the translation click,
                        // without touching the translation itself.
                        if (ok) scheduleDismissTranslationBanner();
                    }
                } finally {
                    candidate.recycle();
                }
            }

            // The banner can also appear on a later accessibility event.
            // This is completely independent from translation-button detection.
            if (containsTranslatedBanner(treeText.toLowerCase(Locale.ROOT))) {
                dismissTranslationBanner(root);
            }
        } finally {
            root.recycle();
        }
    }

    private boolean containsTranslatedBanner(String lowTree) {
        return lowTree.contains("page traduite")
                || lowTree.contains("page translated")
                || lowTree.contains("traduit en français")
                || lowTree.contains("translated to french");
    }

    private void scheduleDismissTranslationBanner() {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            try {
                if (!CHROME.equals(root.getPackageName())) return;
                String treeText = flattenText(root);
                String lowTree = treeText.toLowerCase(Locale.ROOT);
                if (lowTree.contains(DOMAIN) && containsTranslatedBanner(lowTree)) {
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

                // Only use ACTION_DISMISS on the banner itself or one of its
                // parents. Never click a button labelled "Annuler" because that
                // would undo the translation.
                if (tryDismissChain(node)) {
                    log("Bannière Chrome masquée via ACTION_DISMISS: " + nodeText(node));
                    break;
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
                || text.contains("translated to french");
    }

    private boolean tryDismissChain(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (hasDismissAction(current)) {
                boolean ok = current.performAction(AccessibilityNodeInfo.ACTION_DISMISS);
                current.recycle();
                if (ok) return true;
                current = null;
                continue;
            }

            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        return false;
    }

    private boolean hasDismissAction(AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
            if (action.getId() == AccessibilityNodeInfo.ACTION_DISMISS) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findTranslateCandidate(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);

        AccessibilityNodeInfo best = null;
        int bestScore = -1;

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
        if (low.isEmpty()) return false;

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
