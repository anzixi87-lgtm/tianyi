package com.tianyi.meihua;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidNet");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ===== 原生桥：供网页内 JS 调用 =====
    public class Bridge {

        // 原生发起 HTTP POST（绕开 WebView 跨域限制），结果经回调送回网页
        @JavascriptInterface
        public void httpPost(final String reqId, final String url, final String auth, final String body) {
            new Thread(new Runnable() {
                public void run() {
                    int code = 0;
                    String result = "";
                    HttpURLConnection con = null;
                    try {
                        con = (HttpURLConnection) new URL(url).openConnection();
                        con.setRequestMethod("POST");
                        con.setRequestProperty("Content-Type", "application/json");
                        if (auth != null && auth.length() > 0) {
                            con.setRequestProperty("Authorization", auth);
                        }
                        con.setConnectTimeout(30000);
                        con.setReadTimeout(120000);
                        con.setDoOutput(true);

                        OutputStream os = con.getOutputStream();
                        os.write(body.getBytes("UTF-8"));
                        os.close();

                        code = con.getResponseCode();
                        InputStream is = (code >= 200 && code < 300)
                                ? con.getInputStream() : con.getErrorStream();
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) != -1) bo.write(buf, 0, n);
                        result = bo.toString("UTF-8");
                    } catch (Exception e) {
                        code = 0;
                        result = String.valueOf(e.getMessage());
                    } finally {
                        if (con != null) con.disconnect();
                    }

                    final int fcode = code;
                    final String fresult = result;
                    web.post(new Runnable() {
                        public void run() {
                            String js = "window.__netResult && window.__netResult("
                                    + JSONObject.quote(reqId) + "," + fcode + ","
                                    + JSONObject.quote(fresult) + ");";
                            web.evaluateJavascript(js, null);
                        }
                    });
                }
            }).start();
        }

        // 保存（记住 DeepSeek Key、解卦提示词等）
        @JavascriptInterface
        public void save(String key, String value) {
            SharedPreferences sp = getSharedPreferences("tianyi", Context.MODE_PRIVATE);
            sp.edit().putString(key, value).apply();
        }

        // 读取
        @JavascriptInterface
        public String load(String key) {
            SharedPreferences sp = getSharedPreferences("tianyi", Context.MODE_PRIVATE);
            return sp.getString(key, "");
        }
    }
}
