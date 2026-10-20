package dev.local.farmerapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                  // ayarlarin hatirlanmasi buna bagli
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);  // ses ilk dokunusu beklemesin
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        web.setBackgroundColor(0xFF101418);
        web.addJavascriptInterface(new Bridge(), "AppBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(DOWNLOAD_SHIM, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");   // bazi cihazlar ozel uzantilari tanimiyor
                    startActivityForResult(Intent.createChooser(i, "Dosya sec"), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("file:///android_asset/Ciftlik2.html");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) web.setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (filePathCallback == null) return;
            Uri[] out = null;
            if (res == Activity.RESULT_OK && data != null && data.getData() != null)
                out = new Uri[]{ data.getData() };
            filePathCallback.onReceiveValue(out);
            filePathCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    /* Sayfa dosyalari blob URL'iyle veriyorsa WebView bunu kaydedemez.
       Blob yakalanip base64'e cevriliyor, Android tarafi diske yaziyor. */
    private static final String DOWNLOAD_SHIM =
        "(function(){if(window.__a2dl)return;window.__a2dl=1;" +
        "var m={},oc=URL.createObjectURL.bind(URL);" +
        "URL.createObjectURL=function(b){var u=oc(b);m[u]=b;return u;};" +
        "var orv=URL.revokeObjectURL.bind(URL);" +
        "URL.revokeObjectURL=function(u){setTimeout(function(){delete m[u];},15000);return orv(u);};" +
        "var ck=HTMLAnchorElement.prototype.click;" +
        "HTMLAnchorElement.prototype.click=function(){" +
        " var b=m[this.href];" +
        " if(this.download&&b){var n=this.download,r=new FileReader();" +
        "  r.onloadend=function(){try{AppBridge.save(r.result,n);}catch(e){}};" +
        "  r.readAsDataURL(b);return;}" +
        " return ck.apply(this,arguments);};})();";

    public class Bridge {
        @JavascriptInterface
        public void save(String dataUrl, String name) {
            try {
                int comma = dataUrl.indexOf(',');
                String meta = dataUrl.substring(5, comma);
                String mime = meta.split(";")[0];
                byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);

                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE,
                       mime.isEmpty() ? "application/octet-stream" : mime);
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Exception("uri null");
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(bytes);
                os.close();
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, cv, null, null);
                final String nm = name;
                runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(MainActivity.this, "Indirilenler: " + nm,
                                   Toast.LENGTH_SHORT).show(); }});
            } catch (Exception e) {
                runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(MainActivity.this, "Kaydedilemedi",
                                   Toast.LENGTH_SHORT).show(); }});
            }
        }
    }
}
