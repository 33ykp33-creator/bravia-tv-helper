package com.yakboz.braviahelper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(11,14,18));

        TextView title = new TextView(this);
        title.setText("BRAVIA Helper");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1,-2));

        TextView info = new TextView(this);
        info.setText("Bu uygulama web kumandasını TV'ye bağlar. Bir kez Erişilebilirlik izni ver; sonrasında TV tarafında tekrar kurulum gerekmez.");
        info.setTextColor(Color.rgb(170,180,192));
        info.setTextSize(17);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1,-2); ip.setMargins(0,24,0,24);
        root.addView(info, ip);

        status = new TextView(this);
        status.setTextColor(Color.rgb(89,168,255));
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1,-2));

        Button btn = new Button(this);
        btn.setText("ERİŞİLEBİLİRLİĞİ AÇ");
        btn.setTextSize(16);
        btn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 72); bp.setMargins(0,30,0,0);
        root.addView(btn,bp);

        Button done = new Button(this);
        done.setText("TAMAM");
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, 64); dp.setMargins(0,14,0,0);
        root.addView(done,dp);

        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        boolean enabled = isAccessibilityEnabled();
        status.setText(enabled ? "✓ Helper aktif — siteyi kullanabilirsin" : "1 kez izin gerekiyor");
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.toLowerCase().contains(getPackageName().toLowerCase());
    }
}
