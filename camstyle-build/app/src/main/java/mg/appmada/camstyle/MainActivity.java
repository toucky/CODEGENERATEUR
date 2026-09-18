package mg.appmada.camstyle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 101;
    private static final int REQ_CAMERA = 102;
    private static final String PREFS = "camstyle_prefs";
    private static final String ENDPOINT = "https://api.openai.com/v1/images/edits";

    private Uri inputUri;
    private Uri cameraUri;
    private Bitmap resultBitmap;

    private ImageView preview;
    private EditText apiKey;
    private Spinner deviceSpinner;
    private Spinner styleSpinner;
    private Spinner qualitySpinner;
    private Button transformButton;
    private Button saveButton;
    private ProgressBar progress;
    private TextView status;

    private final String[] devices = new String[]{
            "iPhone 17 Pro", "Samsung Galaxy Ultra", "Google Pixel Pro", "Xiaomi Ultra",
            "Huawei Pura Pro", "Sony Xperia Pro", "Canon EOS R5", "Sony Alpha 1",
            "Nikon Z8", "Fujifilm GFX"
    };

    private final String[] styles = new String[]{
            "Auto naturel", "Portrait", "Paysage", "Nuit", "Studio", "Cinématique",
            "Produit", "Cuisine / Food"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10, 10, 12));
        getWindow().setNavigationBarColor(Color.rgb(10, 10, 12));
        setContentView(buildUi());
    }

    private View buildUi() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(10, 10, 12));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(22), pad, dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("CamStyle AI", 30, Color.WHITE, true);
        root.addView(title);
        TextView subtitle = text("Change le rendu caméra, pas les personnes ni les objets.", 15, Color.rgb(174,174,181), false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout keyCard = card();
        keyCard.addView(text("Clé API OpenAI", 14, Color.rgb(220,220,225), true));
        apiKey = new EditText(this);
        apiKey.setTextColor(Color.WHITE);
        apiKey.setHintTextColor(Color.rgb(120,120,128));
        apiKey.setHint("sk-…");
        apiKey.setSingleLine(true);
        apiKey.setTextSize(15);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKey.setBackground(rounded(Color.rgb(38,38,43), 14));
        apiKey.setPadding(dp(14), dp(12), dp(14), dp(12));
        apiKey.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("api_key", ""));
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        keyLp.topMargin = dp(8);
        keyCard.addView(apiKey, keyLp);
        TextView keyHint = text("La clé reste enregistrée localement sur ce téléphone.", 12, Color.rgb(145,145,153), false);
        keyHint.setPadding(0, dp(8), 0, 0);
        keyCard.addView(keyHint);
        root.addView(keyCard);

        LinearLayout photoCard = card();
        photoCard.addView(text("1. Photo", 15, Color.WHITE, true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button pick = button("Galerie", Color.rgb(45,45,52));
        Button camera = button("Appareil photo", Color.rgb(45,45,52));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(48), 1f);
        half.topMargin = dp(10);
        row.addView(pick, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        half2.topMargin = dp(10);
        half2.leftMargin = dp(8);
        row.addView(camera, half2);
        photoCard.addView(row);

        preview = new ImageView(this);
        preview.setBackground(rounded(Color.rgb(24,24,28), 16));
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setAdjustViewBounds(true);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330));
        pLp.topMargin = dp(12);
        photoCard.addView(preview, pLp);
        root.addView(photoCard);

        LinearLayout optionsCard = card();
        optionsCard.addView(text("2. Rendu souhaité", 15, Color.WHITE, true));
        deviceSpinner = spinner(devices);
        styleSpinner = spinner(styles);
        qualitySpinner = spinner(new String[]{"Éco — moins cher", "Standard", "Premium"});
        addLabeledSpinner(optionsCard, "Caméra / appareil", deviceSpinner);
        addLabeledSpinner(optionsCard, "Style", styleSpinner);
        addLabeledSpinner(optionsCard, "Qualité", qualitySpinner);
        TextView lock = text("🔒 Verrou identité & objets : visage, corps, pose, vêtements, arrière-plan et objets doivent rester identiques.", 13, Color.rgb(180,205,255), false);
        lock.setPadding(0, dp(14), 0, 0);
        optionsCard.addView(lock);
        root.addView(optionsCard);

        transformButton = button("Transformer la photo", Color.rgb(10,132,255));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        tLp.topMargin = dp(8);
        root.addView(transformButton, tLp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progLp.gravity = Gravity.CENTER_HORIZONTAL;
        progLp.topMargin = dp(14);
        root.addView(progress, progLp);

        status = text("Prêt.", 13, Color.rgb(155,155,163), false);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(8), 0, dp(4));
        root.addView(status);

        saveButton = button("Enregistrer le résultat", Color.rgb(45,45,52));
        saveButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        sLp.topMargin = dp(8);
        root.addView(saveButton, sLp);

        pick.setOnClickListener(v -> pickImage());
        camera.setOnClickListener(v -> takePhoto());
        transformButton.setOnClickListener(v -> transform());
        saveButton.setOnClickListener(v -> saveResult());
        return scroll;
    }

    private void addLabeledSpinner(LinearLayout parent, String label, Spinner spinner) {
        TextView tv = text(label, 12, Color.rgb(165,165,173), false);
        tv.setPadding(0, dp(12), 0, dp(5));
        parent.addView(tv);
        parent.addView(spinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.WHITE);
                v.setTextSize(15);
                v.setPadding(dp(12), 0, dp(12), 0);
                return v;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(Color.BLACK);
                v.setTextSize(15);
                v.setPadding(dp(12), dp(12), dp(12), dp(12));
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setBackground(rounded(Color.rgb(38,38,43), 14));
        return s;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(rounded(Color.rgb(24,24,28), 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(color, 16));
        return b;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_PICK);
    }

    private void takePhoto() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "CamStyle_input_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (cameraUri == null) {
            toast("Impossible de préparer la photo.");
            return;
        }
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast("Aucun appareil photo disponible.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQ_PICK && data != null && data.getData() != null) {
            inputUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(inputUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            preview.setImageURI(inputUri);
            resultBitmap = null;
            saveButton.setVisibility(View.GONE);
            status.setText("Photo chargée.");
        } else if (requestCode == REQ_CAMERA && cameraUri != null) {
            inputUri = cameraUri;
            preview.setImageURI(inputUri);
            resultBitmap = null;
            saveButton.setVisibility(View.GONE);
            status.setText("Photo prise.");
        }
    }

    private void transform() {
        String key = apiKey.getText().toString().trim();
        if (key.isEmpty()) {
            toast("Entre d'abord ta clé API OpenAI.");
            return;
        }
        if (inputUri == null) {
            toast("Choisis ou prends une photo.");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("api_key", key).apply();
        String device = devices[deviceSpinner.getSelectedItemPosition()];
        String style = styles[styleSpinner.getSelectedItemPosition()];
        int qualityPos = qualitySpinner.getSelectedItemPosition();
        String quality = qualityPos == 0 ? "low" : (qualityPos == 1 ? "medium" : "high");

        setBusy(true, "Transformation IA en cours…");
        new Thread(() -> {
            try {
                byte[] imageBytes = readAll(inputUri, 50 * 1024 * 1024);
                String mime = getContentResolver().getType(inputUri);
                if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
                String prompt = buildPrompt(device, style);

                ApiResult r = callImageEdit(key, "gpt-image-2.5-flare", prompt, imageBytes, mime, quality, true);
                if (!r.ok && shouldRetryWithoutFidelity(r.error)) {
                    r = callImageEdit(key, "gpt-image-2.5-flare", prompt, imageBytes, mime, quality, false);
                }
                if (!r.ok && shouldFallbackModel(r.error)) {
                    r = callImageEdit(key, "gpt-image-2", prompt, imageBytes, mime, quality, true);
                    if (!r.ok && shouldRetryWithoutFidelity(r.error)) {
                        r = callImageEdit(key, "gpt-image-2", prompt, imageBytes, mime, quality, false);
                    }
                }

                final ApiResult result = r;
                runOnUiThread(() -> {
                    if (result.ok && result.bitmap != null) {
                        resultBitmap = result.bitmap;
                        preview.setImageBitmap(result.bitmap);
                        saveButton.setVisibility(View.VISIBLE);
                        setBusy(false, "Terminé. Vérifie le visage et les objets avant d'enregistrer.");
                    } else {
                        setBusy(false, "Erreur API.");
                        showError(result.error == null ? "Réponse invalide." : result.error);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Erreur.");
                    showError(e.getMessage() == null ? e.toString() : e.getMessage());
                });
            }
        }).start();
    }

    private String buildPrompt(String device, String style) {
        String capture = deviceCharacteristics(device);
        String stylePrompt = styleCharacteristics(style);
        return "EDIT THE INPUT PHOTO ONLY. The input image is the single source of truth. " +
                "ABSOLUTE PRESERVATION RULES: preserve every person's identity exactly, including the exact same face, facial geometry, skin tone, skin marks, age, expression, eyes, nose, mouth, jaw, ears, hair, body proportions, hands, pose, gaze and clothing. " +
                "Preserve every object exactly: same objects, same count, same text and logos, same shapes, dimensions, colors, materials, positions, background structure, architecture and scene layout. " +
                "Preserve composition, crop, perspective and camera viewpoint. DO NOT add, remove, replace, invent, beautify, retouch, reshape, age, de-age or move any person or object. DO NOT modify the face. DO NOT change written text. " +
                "The ONLY allowed transformation is photographic capture/rendering characteristics: exposure, white balance, color science, tone curve, dynamic range, highlight roll-off, shadow rendering, micro-contrast, natural sharpening, noise rendering and physically plausible lens/depth rendering without changing geometry. " +
                "If any photographic effect would alter identity, geometry, objects, text or composition, skip that effect. Preservation always wins. " +
                "Target rendering: " + capture + ". Style: " + stylePrompt + ". " +
                "Final result must look like the SAME original photograph captured/processed by " + device + ", not a recreated scene.";
    }

    private String deviceCharacteristics(String d) {
        String s = d.toLowerCase(Locale.ROOT);
        if (s.contains("iphone")) return "premium modern smartphone look, natural skin tones, balanced HDR, controlled highlights, clean detail, realistic contrast, neutral-to-warm color science";
        if (s.contains("samsung")) return "premium flagship smartphone rendering, crisp detail, wide dynamic range, vivid but controlled colors, clean shadows";
        if (s.contains("pixel")) return "computational photography look, realistic HDR, excellent skin-tone balance, clean shadows, natural detail";
        if (s.contains("xiaomi")) return "premium flagship camera-phone rendering, rich tonal depth, refined local contrast, detailed but natural texture";
        if (s.contains("huawei")) return "high-end mobile photography look, strong dynamic range, clean low-light processing, refined color separation";
        if (s.contains("xperia")) return "natural Sony-style mobile rendering, restrained sharpening, realistic colors, photographic contrast";
        if (s.contains("canon")) return "full-frame professional camera rendering, smooth highlight roll-off, natural skin tones, rich but realistic color, organic detail";
        if (s.contains("alpha")) return "high-resolution professional full-frame rendering, neutral color, high micro-detail, clean dynamic range, realistic texture";
        if (s.contains("nikon")) return "professional full-frame rendering, strong tonal latitude, natural colors, clean detail and smooth gradients";
        return "medium-format professional rendering, exceptional tonal depth, fine natural detail, smooth color transitions, restrained sharpening";
    }

    private String styleCharacteristics(String s) {
        if (s.startsWith("Portrait")) return "portrait-optimized color and tonal rendering; keep the exact existing depth/composition and do not artificially change facial features or background geometry";
        if (s.startsWith("Paysage")) return "landscape-optimized dynamic range and natural color separation; keep every scene element and the exact framing";
        if (s.startsWith("Nuit")) return "clean low-light rendering, controlled noise, protected highlights and realistic night colors; do not invent light sources";
        if (s.startsWith("Studio")) return "polished studio-like tonal balance only; do not change or invent lighting direction, backdrop, face or objects";
        if (s.startsWith("Cin")) return "cinematic color grading and highlight roll-off only, subtle and realistic; no scene or subject changes";
        if (s.startsWith("Produit")) return "product-photo clarity and neutral color rendering while preserving exact product shape, labels, text and placement";
        if (s.startsWith("Cuisine")) return "natural appetizing food color and texture rendering while preserving exact ingredients, plate, portions and objects";
        return "natural premium automatic photo processing with realistic color and contrast";
    }

    private ApiResult callImageEdit(String key, String model, String prompt, byte[] image, String mime, String quality, boolean fidelity) {
        HttpURLConnection conn = null;
        try {
            String boundary = "----CamStyle" + System.currentTimeMillis();
            conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(240000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream out = conn.getOutputStream();
            writeField(out, boundary, "model", model);
            writeField(out, boundary, "prompt", prompt);
            writeField(out, boundary, "quality", quality);
            writeField(out, boundary, "size", "auto");
            writeField(out, boundary, "output_format", "jpeg");
            if (fidelity) writeField(out, boundary, "input_fidelity", "high");
            writeFile(out, boundary, "image", "input.jpg", mime, image);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            InputStream body = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String json = readString(body);
            if (code < 200 || code >= 300) return ApiResult.error("HTTP " + code + "\n" + extractError(json));

            JSONObject root = new JSONObject(json);
            JSONArray data = root.optJSONArray("data");
            if (data == null || data.length() == 0) return ApiResult.error("Aucune image dans la réponse API.");
            JSONObject first = data.getJSONObject(0);
            String b64 = first.optString("b64_json", "");
            byte[] bytes;
            if (!b64.isEmpty()) {
                bytes = Base64.decode(b64, Base64.DEFAULT);
            } else {
                String url = first.optString("url", "");
                if (url.isEmpty()) return ApiResult.error("Format de réponse image non reconnu.");
                bytes = download(url);
            }
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return ApiResult.error("Impossible de décoder l'image générée.");
            return ApiResult.ok(bmp);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void writeField(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFile(OutputStream out, String boundary, String name, String filename, String mime, byte[] bytes) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] readAll(Uri uri, int maxBytes) throws Exception {
        InputStream in = new BufferedInputStream(getContentResolver().openInputStream(uri));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                in.close();
                throw new Exception("La photo dépasse 50 Mo. Choisis une image plus légère.");
            }
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private byte[] download(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(30000);
        c.setReadTimeout(120000);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return out.toByteArray();
    }

    private String readString(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private String extractError(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject err = root.optJSONObject("error");
            if (err != null) return err.optString("message", json);
        } catch (Exception ignored) {}
        return json == null || json.trim().isEmpty() ? "Erreur API sans détail." : json.trim();
    }

    private boolean shouldRetryWithoutFidelity(String e) {
        if (e == null) return false;
        String s = e.toLowerCase(Locale.ROOT);
        return s.contains("input_fidelity") || s.contains("unknown parameter") || s.contains("unsupported parameter");
    }

    private boolean shouldFallbackModel(String e) {
        if (e == null) return false;
        String s = e.toLowerCase(Locale.ROOT);
        return s.contains("model") && (s.contains("not found") || s.contains("does not exist") || s.contains("access") || s.contains("unsupported"));
    }

    private void saveResult() {
        if (resultBitmap == null) return;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "CamStyle_AI_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Création du fichier impossible.");
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new Exception("Ouverture du fichier impossible.");
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 96, out);
            out.close();
            toast("Image enregistrée dans la galerie.");
        } catch (Exception e) {
            showError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void setBusy(boolean busy, String msg) {
        transformButton.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        status.setText(msg);
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("CamStyle AI")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static class ApiResult {
        final boolean ok;
        final Bitmap bitmap;
        final String error;
        private ApiResult(boolean ok, Bitmap bitmap, String error) {
            this.ok = ok;
            this.bitmap = bitmap;
            this.error = error;
        }
        static ApiResult ok(Bitmap bitmap) { return new ApiResult(true, bitmap, null); }
        static ApiResult error(String error) { return new ApiResult(false, null, error); }
    }
}
