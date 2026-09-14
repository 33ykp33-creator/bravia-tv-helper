package com.yakboz.braviahelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

public class RemoteAccessibilityService extends AccessibilityService {
    private static final String TOPIC = "bravia-98a5813bc2129963b10979c29af9ea0b2d8904e80662605f";
    private static final String PSK = "555";
    private volatile boolean running = true;
    private WindowManager wm;
    private CursorView cursor;
    private WindowManager.LayoutParams cursorParams;
    private int screenW = 1920, screenH = 1080;
    private float x, y;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        setupCursor();
        Thread t = new Thread(this::listenLoop, "bravia-remote-listener");
        t.setDaemon(true);
        t.start();
    }

    @Override public void onDestroy() {
        running = false;
        try { if (wm != null && cursor != null) wm.removeView(cursor); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    private void setupCursor() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point p = new Point(); display.getRealSize(p); screenW = p.x; screenH = p.y;
        x = screenW / 2f; y = screenH / 2f;
        cursor = new CursorView();
        cursorParams = new WindowManager.LayoutParams(42,42,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        cursorParams.gravity = Gravity.TOP | Gravity.LEFT;
        moveOverlay();
        wm.addView(cursor, cursorParams);
    }

    private void listenLoop() {
        while (running) {
            HttpURLConnection c = null;
            try {
                URL u = new URL("https://ntfy.sh/" + TOPIC + "/json");
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(0);
                c.setRequestProperty("User-Agent", "BRAVIA-Helper/1.1");
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (running && (line = br.readLine()) != null) {
                    try {
                        JSONObject envelope = new JSONObject(line);
                        if (!"message".equals(envelope.optString("event"))) continue;
                        String msg = envelope.optString("message", "");
                        if (msg.isEmpty()) continue;
                        handle(new JSONObject(msg));
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored2) {}
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }

    private void handle(JSONObject o) {
        String type = o.optString("type");
        switch (type) {
            case "move":
                float dx = (float)o.optDouble("dx",0); float dy = (float)o.optDouble("dy",0);
                x = clamp(x + dx, 2, screenW-2); y = clamp(y + dy,2,screenH-2);
                runOnMain(this::moveOverlay); break;
            case "click": runOnMain(this::clickAtCursor); break;
            case "scroll":
                float amount = (float)o.optDouble("dy",0);
                runOnMain(() -> scrollAtCursor(amount)); break;
            case "text":
                String text = o.optString("text","");
                runOnMain(() -> setFocusedText(text)); break;
            case "global":
                String action = o.optString("action","");
                runOnMain(() -> doGlobal(action)); break;
            case "ircc":
                String code = o.optString("code","");
                if (!code.isEmpty()) new Thread(() -> sendIrcc(code)).start(); break;
        }
    }

    private void doGlobal(String a) {
        if ("back".equals(a)) performGlobalAction(GLOBAL_ACTION_BACK);
        else if ("home".equals(a)) performGlobalAction(GLOBAL_ACTION_HOME);
        else if ("recents".equals(a)) performGlobalAction(GLOBAL_ACTION_RECENTS);
        else if ("notifications".equals(a)) performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    private void moveOverlay() {
        if (cursorParams == null || wm == null || cursor == null) return;
        cursorParams.x = Math.round(x - 8); cursorParams.y = Math.round(y - 7);
        try { wm.updateViewLayout(cursor, cursorParams); } catch (Exception ignored) {}
    }

    private void clickAtCursor() {
        Path path = new Path(); path.moveTo(x,y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(path,0,45));
        dispatchGesture(b.build(),null,null);
    }

    private void scrollAtCursor(float amount) {
        float dy = amount >= 0 ? -260 : 260;
        Path path = new Path(); path.moveTo(x,y); path.lineTo(x, clamp(y+dy,30,screenH-30));
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(path,0,260));
        dispatchGesture(b.build(),null,null);
    }

    private void setFocusedText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo n = findEditableFocused(root);
        if (n == null) return;
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo findEditableFocused(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && (n.isFocused() || n.isAccessibilityFocused())) return n;
        for (int i=0;i<n.getChildCount();i++) {
            AccessibilityNodeInfo f = findEditableFocused(n.getChild(i)); if (f != null) return f;
        }
        return null;
    }

    private void sendIrcc(String code) {
        HttpURLConnection c = null;
        try {
            String ip = ownIpv4();
            if (ip == null) return;
            URL u = new URL("http://" + ip + "/sony/IRCC");
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(2500); c.setReadTimeout(2500); c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("X-Auth-PSK", PSK);
            c.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
            c.setRequestProperty("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"");
            String xml = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:X_SendIRCC xmlns:u=\"urn:schemas-sony-com:service:IRCC:1\"><IRCCCode>"+code+"</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>";
            try (OutputStream os = c.getOutputStream()) { os.write(xml.getBytes(StandardCharsets.UTF_8)); }
            c.getResponseCode();
        } catch (Exception ignored) { } finally { if (c != null) c.disconnect(); }
    }

    private String ownIpv4() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(ifs)) {
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    String s = a.getHostAddress();
                    if (!a.isLoopbackAddress() && s != null && s.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) return s;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private void runOnMain(Runnable r){ new android.os.Handler(getMainLooper()).post(r); }

    private class CursorView extends View {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        CursorView(){ super(RemoteAccessibilityService.this); fill.setColor(Color.WHITE); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(2f); edge.setColor(Color.BLACK); }
        @Override protected void onDraw(Canvas c){
            Path p = new Path(); p.moveTo(4,3); p.lineTo(4,34); p.lineTo(13,26); p.lineTo(19,39); p.lineTo(25,36); p.lineTo(19,23); p.lineTo(32,23); p.close();
            c.drawPath(p,fill); c.drawPath(p,edge);
        }
    }
}
