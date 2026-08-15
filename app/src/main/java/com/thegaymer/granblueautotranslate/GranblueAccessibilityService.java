package com.thegaymer.granblueautotranslate;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
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

    // Keep the successful v5 translation detection, but make the click a
    // one-shot action for the current URL. Chrome can keep exposing the same
    // Translate control through many accessibility events; clicking it again
    // causes the "Page translated" snackbar to be recreated over and over.
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

            // URL changes are our page/navigation boundary. This lets the
            // translator fire once again when Granblue navigates to a new page,
            // while preventing repeated clicks on the same page.
            String currentUrl = findCurrentUrl(root);
            if (!currentUrl.isEmpty()) {
                if (!currentUrl.equals(lastUrl)) {
                    lastUrl = currentUrl;
                    translationTriggered = false;
                    log("Nouvelle page Granblue: " + currentUrl);
                }
            }

            // The post-translation Chrome snackbar is itself an accessibility
            // node. If it is present, the page is already translated: never
            // click the Translate control again. Instead try to remove ONLY the
            // snackbar, first with ACTION_DISMISS and then with a swipe gesture.
            if (containsTranslatedBanner(lowTree)) {
                translationTriggered = true;
                dismissTranslationBanner(root);
                return;
            }

            // Critical anti-spam guard. Once Chrome accepted the translation
            // click, do not click the same Translate control again until the URL
            // changes.
            if (translationTriggered) return;

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
                            translationTriggered = true;
                            // Chrome creates the translated-page snackbar
                            // asynchronously, so give it a moment to appear.
                            scheduleDismissTranslationBanner();
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

    private boolean containsTranslatedBanner(String lowTree) {
        return lowTree.contains("page traduite")
                || lowTree.contains("page translated")
                || lowTree.contains("traduit en français")
                || lowTree.contains("translated to french")
                || lowTree.contains("page traduite en français")
                || lowTree.contains("page translated to french");
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
        }, 200);
    }

    private void dismissTranslationBanner(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        if (now - lastDismissAttempt < 700) return;
        lastDismissAttempt = now;

        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);

        AccessibilityNodeInfo bannerNode = null;
        Rect bestBounds = null;
        long bestArea = -1;

        for (AccessibilityNodeInfo node : nodes) {
            String text = nodeText(node).toLowerCase(Locale.ROOT);
            if (!isTranslatedBannerText(text)) continue;

            // First try Android's semantic dismiss action. This is the safest
            // method because it does not activate Chrome's "Annuler"/Undo action.
            if (tryDismissChain(node)) {
                log("Bannière Chrome masquée via ACTION_DISMISS");
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return;
            }

            // If Chrome does not expose ACTION_DISMISS, keep the largest useful
            // bounds around the matching text. We will swipe the snackbar itself
            // away instead of clicking its "Annuler" action.
            AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
            for (int depth = 0; current != null && depth < 8; depth++) {
                Rect bounds = new Rect();
                current.getBoundsInScreen(bounds);
                long area = (long) Math.max(0, bounds.width()) * Math.max(0, bounds.height());
                if (bounds.width() >= 250 && bounds.height() >= 45 && area > bestArea) {
                    if (bannerNode != null) bannerNode.recycle();
                    bannerNode = current;
                    bestBounds = bounds;
                    bestArea = area;
                    current = null;
                    break;
                }

                AccessibilityNodeInfo parent = current.getParent();
                current.recycle();
                current = parent;
            }
        }

        if (bannerNode != null && bestBounds != null) {
            boolean sent = swipeBannerAway(bestBounds);
            log("Bannière Chrome masquée par geste = " + sent);
            bannerNode.recycle();
        }

        for (AccessibilityNodeInfo node : nodes) node.recycle();
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
            if (hasAction(current, AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)) {
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

    private boolean hasAction(AccessibilityNodeInfo node, AccessibilityNodeInfo.AccessibilityAction action) {
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        for (AccessibilityNodeInfo.AccessibilityAction available : actions) {
            if (available.getId() == action.getId()) return true;
        }
        return false;
    }

    private boolean swipeBannerAway(Rect bounds) {
        // Chrome's post-translation element is a snackbar/message. Dismiss it
        // with an upward swipe over the banner rather than touching "Annuler".
        float x = bounds.centerX();
        float startY = bounds.centerY();
        float endY = Math.max(1, bounds.top - Math.max(120, bounds.height()));

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 220);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        return dispatchGesture(gesture, null, handler);
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
