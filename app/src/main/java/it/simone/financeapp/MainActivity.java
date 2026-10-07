package it.simone.financeapp;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Guscio nativo: mostra l'interfaccia (assets/index.html) e salva i dati in un file privato. */
public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 11;
    private static final int REQ_IMPORT = 12;

    private WebView web;
    private String pendingExport;
    private final ExecutorService net = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= 29 && Build.VERSION.SDK_INT < 33) {
            s.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F766E"));
        root.setFitsSystemWindows(true);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.onBack&&window.onBack())?'1':'0'", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"\"1\"".equals(value)) {
                    finish();
                }
            }
        });
    }

    private void js(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript(code, null);
            }
        });
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toString("UTF-8");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean good = resultCode == RESULT_OK && data != null && data.getData() != null;
        if (requestCode == REQ_EXPORT) {
            boolean saved = false;
            if (good && pendingExport != null) {
                try {
                    OutputStream os = getContentResolver().openOutputStream(data.getData(), "wt");
                    os.write(pendingExport.getBytes("UTF-8"));
                    os.close();
                    saved = true;
                } catch (Exception e) {
                    saved = false;
                }
            }
            pendingExport = null;
            js("window.onExported&&window.onExported(" + saved + ")");
        } else if (requestCode == REQ_IMPORT && good) {
            String text = "";
            try {
                text = readAll(getContentResolver().openInputStream(data.getData()));
            } catch (Exception e) {
                text = "";
            }
            js("window.onImport&&window.onImport(" + JSONObject.quote(text) + ")");
        }
    }

    /** Funzioni richiamabili dalla pagina come window.Android.xxx() */
    public class Bridge {
        @JavascriptInterface
        public String load() {
            try {
                File f = new File(getFilesDir(), "data.json");
                if (!f.exists()) {
                    return null;
                }
                return readAll(new FileInputStream(f));
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public void save(String json) {
            try {
                File tmp = new File(getFilesDir(), "data.json.tmp");
                FileOutputStream os = new FileOutputStream(tmp);
                os.write(json.getBytes("UTF-8"));
                os.getFD().sync();
                os.close();
                File dst = new File(getFilesDir(), "data.json");
                if (!tmp.renameTo(dst)) {
                    dst.delete();
                    tmp.renameTo(dst);
                }
            } catch (Exception e) {
                // la pagina mostra comunque i dati in memoria
            }
        }

        @JavascriptInterface
        public boolean isNight() {
            int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return m == Configuration.UI_MODE_NIGHT_YES;
        }

        @JavascriptInterface
        public void setBars(final String statusHex, final String navHex, final boolean lightNav) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Window w = getWindow();
                        w.setStatusBarColor(Color.parseColor(statusHex));
                        w.setNavigationBarColor(Color.parseColor(navHex));
                        View d = w.getDecorView();
                        int f = d.getSystemUiVisibility();
                        if (lightNav) {
                            f = f | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        } else {
                            f = f & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                        d.setSystemUiVisibility(f);
                    } catch (Exception e) {
                        // ignora
                    }
                }
            });
        }

        @JavascriptInterface
        public void exportFile(final String name, final String data) {
            pendingExport = data;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, name);
                    startActivityForResult(i, REQ_EXPORT);
                }
            });
        }

        @JavascriptInterface
        public void httpPost(final String callId, final String urlStr, final String body) {
            net.execute(new Runnable() {
                @Override
                public void run() {
                    String result = null;
                    String error = null;
                    HttpURLConnection c = null;
                    try {
                        URL url = new URL(urlStr);
                        if (!"https".equals(url.getProtocol())) {
                            throw new Exception("Indirizzo non valido");
                        }
                        c = (HttpURLConnection) url.openConnection();
                        c.setRequestMethod("POST");
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(20000);
                        c.setDoOutput(true);
                        c.setInstanceFollowRedirects(true);
                        c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                        OutputStream os = c.getOutputStream();
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                        os.close();
                        int code = c.getResponseCode();
                        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
                        result = readAll(in);
                        if (code < 200 || code >= 300) {
                            error = "HTTP " + code;
                        }
                    } catch (Exception e) {
                        error = e.getMessage() != null ? e.getMessage() : e.toString();
                    } finally {
                        if (c != null) {
                            c.disconnect();
                        }
                    }
                    final String fr = result, fe = error;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            js("window.onHttp&&window.onHttp(" + JSONObject.quote(callId) + ","
                                    + (fr != null ? JSONObject.quote(fr) : "null") + ","
                                    + (fe != null ? JSONObject.quote(fe) : "null") + ")");
                        }
                    });
                }
            });
        }

        @JavascriptInterface
        public void importFile() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_IMPORT);
                }
            });
        }
    }
}
