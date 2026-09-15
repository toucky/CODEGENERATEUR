package mg.appmada.parlefrancais;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    private static final int REQ_SPEECH = 9001;
    private static final int REQ_AUDIO = 9002;

    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean pendingSpeech = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(Locale.FRANCE);
                tts.setSpeechRate(0.96f);
            }
        });
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void startRecognition() {
            runOnUiThread(() -> {
                if (android.os.Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingSpeech = true;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                } else {
                    launchSpeechRecognition();
                }
            });
        }

        @JavascriptInterface
        public void speak(String text, String gender) {
            runOnUiThread(() -> speakNative(text, gender));
        }

        @JavascriptInterface
        public void callOpenAI(String apiKey, String model, String messagesJson) {
            new Thread(() -> callOpenAIInternal(apiKey, model, messagesJson)).start();
        }
    }

    private void launchSpeechRecognition() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez en français");
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            sendJs("window.onNativeSpeechError && window.onNativeSpeechError(" +
                    JSONObject.quote("Reconnaissance vocale indisponible sur cet appareil.") + ")");
        }
    }

    private void speakNative(String text, String gender) {
        if (!ttsReady || text == null || text.trim().isEmpty()) return;
        tts.setLanguage(Locale.FRANCE);
        tts.setSpeechRate(0.96f);

        try {
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                Voice best = null;
                String[] maleHints = {"male","homme","mascul","thomas","henri","paul","louis","hugo"};
                String[] femaleHints = {"female","femme","feminin","amelie","audrey","julie","marie","celine","virginie"};
                String[] hints = "male".equalsIgnoreCase(gender) ? maleHints : femaleHints;

                for (Voice v : voices) {
                    if (v.getLocale() == null || !"fr".equalsIgnoreCase(v.getLocale().getLanguage())) continue;
                    String name = v.getName() == null ? "" : v.getName().toLowerCase(Locale.ROOT);
                    for (String h : hints) {
                        if (name.contains(h)) {
                            best = v;
                            break;
                        }
                    }
                    if (best != null) break;
                }
                if (best == null) {
                    for (Voice v : voices) {
                        if (v.getLocale() != null && "fr".equalsIgnoreCase(v.getLocale().getLanguage())) {
                            best = v;
                            break;
                        }
                    }
                }
                if (best != null) tts.setVoice(best);
            }
        } catch (Exception ignored) {}

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "parle_francais");
    }

    private void callOpenAIInternal(String apiKey, String model, String messagesJson) {
        HttpsURLConnection conn = null;
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new Exception("Clé API manquante.");
            }

            URL url = new URL("https://api.openai.com/v1/chat/completions");
            conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(45000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", (model == null || model.trim().isEmpty()) ? "gpt-5-nano" : model.trim());
            body.put("messages", new JSONArray(messagesJson));
            body.put("reasoning_effort", "minimal");
            body.put("max_completion_tokens", 240);

            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String response = readAll(stream);

            if (code < 200 || code >= 300) {
                String msg = "Erreur API " + code;
                try {
                    JSONObject err = new JSONObject(response);
                    if (err.has("error")) msg += " : " + err.getJSONObject("error").optString("message", "");
                } catch (Exception ignored) {}
                throw new Exception(msg);
            }

            JSONObject json = new JSONObject(response);
            String reply = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "");

            if (reply.trim().isEmpty()) throw new Exception("Réponse API vide.");
            final String safeReply = reply;
            runOnUiThread(() -> sendJs("window.onNativeApiResult && window.onNativeApiResult(" +
                    JSONObject.quote(safeReply) + ")"));

        } catch (Exception e) {
            final String error = e.getMessage() == null ? "Erreur API inconnue." : e.getMessage();
            runOnUiThread(() -> sendJs("window.onNativeApiError && window.onNativeApiError(" +
                    JSONObject.quote(error) + ")"));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void sendJs(String script) {
        if (webView != null) webView.evaluateJavascript(script, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    sendJs("window.onNativeSpeechResult && window.onNativeSpeechResult(" +
                            JSONObject.quote(results.get(0)) + ")");
                    return;
                }
            }
            sendJs("window.onNativeSpeechError && window.onNativeSpeechError(" +
                    JSONObject.quote("Aucune phrase reconnue.") + ")");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && pendingSpeech) {
            pendingSpeech = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchSpeechRecognition();
            } else {
                sendJs("window.onNativeSpeechError && window.onNativeSpeechError(" +
                        JSONObject.quote("Permission micro refusée.") + ")");
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
