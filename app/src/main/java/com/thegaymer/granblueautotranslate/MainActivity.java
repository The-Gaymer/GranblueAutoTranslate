package com.thegaymer.granblueautotranslate;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private TextView log;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 36);

        TextView title = new TextView(this);
        title.setText("Granblue Auto Translate");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 28, 0, 20);
        root.addView(status);

        Button settings = new Button(this);
        settings.setText("Activer le service d'accessibilité");
        settings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings);

        Button clear = new Button(this);
        clear.setText("Effacer le journal");
        clear.setOnClickListener(v -> {
            getSharedPreferences("log", MODE_PRIVATE).edit().clear().apply();
            refresh();
        });
        root.addView(clear);

        log = new TextView(this);
        log.setTextSize(13);
        log.setPadding(0, 20, 0, 0);
        root.addView(log, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean enabled = false;
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices != null &&
                enabledServices.contains(getPackageName() + "/.GranblueAccessibilityService")) {
            enabled = true;
        }
        status.setText(enabled
                ? "🟢 Service actif\nSurveillance de Chrome + Granblue."
                : "🔴 Service inactif\nActive-le dans les paramètres d'accessibilité.");
        String text = getSharedPreferences("log", MODE_PRIVATE).getString("text", "Aucun événement.");
        log.setText(text);
    }
}
