package mg.appmada.gasyvideoia;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 9001;
    private static final long POLL_INTERVAL_MS = 20000L;
    private static final long POLL_BACKOFF_429_MS = 60000L;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private final Object pollLock = new Object();
    private long nextPollAllowedAt = 0L;
    private boolean pollInFlight = false;
    private String activePollVideoId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8,8,12));
        getWindow().setNavigationBarColor(Color.rgb(8,8,12));

        webView = new WebView(this);
        setContentView(webView);

        webView.setBackgroundColor(Color.rgb(8,8,12));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);
        webView.addJavascriptInterface(new AndroidApi(), "AndroidApi");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost();
                if (host.contains("agnes-ai.com") || host.contains("agnes-ai.space")) return false;
                if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> uploadMsg, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = uploadMsg;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                String[] accepts = params.getAcceptTypes();
                String type = "*/*";
                if (accepts != null && accepts.length > 0 && accepts[0] != null && !accepts[0].isEmpty()) {
                    if (accepts[0].contains("image")) type = "image/*";
                    else if (accepts[0].contains("audio")) type = "audio/*";
                    else type = accepts[0];
                }
                intent.setType(type);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Impossible d'ouvrir les fichiers.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);
                if (ext == null || ext.isEmpty()) ext = "mp4";
                String filename = "Gasy-Video-IA-" + System.currentTimeMillis() + "." + ext;
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setTitle("Gasy Video IA");
                req.setDescription("Téléchargement de la vidéo");
                req.setMimeType(mimetype == null ? "video/mp4" : mimetype);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(req);
                Toast.makeText(MainActivity.this, "Téléchargement lancé.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(results);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidApi");
            webView.destroy();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void callback(String function, String value) {
        runOnUiThread(() -> {
            if (webView == null) return;
            String js = function + "(" + JSONObject.quote(value == null ? "" : value) + ")";
            webView.evaluateJavascript(js, null);
        });
    }

    private void notifyRateLimit(long delayMs) {
        long seconds = Math.max(1L, delayMs / 1000L);
        runOnUiThread(() -> {
            if (webView == null) return;
            String message = "Serveur IA occupé. Nouvelle vérification automatique dans " + seconds + " s.";
            String js = "if(window.onNativeRateLimit){window.onNativeRateLimit(" + JSONObject.quote(message) + ");}";
            webView.evaluateJavascript(js, null);
        });
    }

    private String request(String method, String endpoint, String apiKey, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(45000);
        conn.setReadTimeout(180000);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "GasyVideoIA/1.0.1 Android");
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
        }
        int code = conn.getResponseCode();
        String retryAfterHeader = conn.getHeaderField("Retry-After");
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder out = new StringBuilder();
        if (stream != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line; while ((line = br.readLine()) != null) out.append(line);
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) throw new ApiException(code, out.toString(), retryAfterHeader);
        return out.toString();
    }

    private static String trim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static class ApiException extends Exception {
        final int code;
        final long retryAfterMs;

        ApiException(int code, String body, String retryAfterHeader) {
            super("API " + code + " : " + trim(body, 500));
            this.code = code;
            long retryMs = 0L;
            if (retryAfterHeader != null) {
                try {
                    retryMs = Long.parseLong(retryAfterHeader.trim()) * 1000L;
                } catch (Exception ignored) { }
            }
            this.retryAfterMs = retryMs;
        }
    }

    public class AndroidApi {
        @JavascriptInterface
        public void createVideo(String apiKey, String jsonPayload) {
            synchronized (pollLock) {
                activePollVideoId = "";
                nextPollAllowedAt = 0L;
                pollInFlight = false;
            }
            executor.submit(() -> {
                try {
                    String response = request("POST", "https://apihub.agnes-ai.com/v1/videos", apiKey, jsonPayload);
                    callback("window.onNativeCreateResponse", response);
                } catch (Exception e) {
                    callback("window.onNativeError", e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void pollVideo(String apiKey, String videoId, String model) {
            if (videoId == null || videoId.trim().isEmpty()) return;

            long now = System.currentTimeMillis();
            synchronized (pollLock) {
                if (!videoId.equals(activePollVideoId)) {
                    activePollVideoId = videoId;
                    nextPollAllowedAt = 0L;
                    pollInFlight = false;
                }
                if (pollInFlight || now < nextPollAllowedAt) return;
                pollInFlight = true;
                nextPollAllowedAt = now + POLL_INTERVAL_MS;
            }

            executor.submit(() -> {
                try {
                    String endpoint = "https://apihub.agnes-ai.com/agnesapi?video_id=" +
                            Uri.encode(videoId) + "&model_name=" + Uri.encode(model);
                    String response = request("GET", endpoint, apiKey, null);
                    callback("window.onNativePollResponse", response);
                } catch (ApiException e) {
                    if (e.code == 429) {
                        long delay = Math.max(POLL_BACKOFF_429_MS, e.retryAfterMs);
                        synchronized (pollLock) {
                            nextPollAllowedAt = Math.max(nextPollAllowedAt, System.currentTimeMillis() + delay);
                        }
                        notifyRateLimit(delay);
                    } else {
                        callback("window.onNativeError", e.getMessage());
                    }
                } catch (Exception e) {
                    callback("window.onNativeError", e.getMessage());
                } finally {
                    synchronized (pollLock) {
                        pollInFlight = false;
                    }
                }
            });
        }
    }
}
