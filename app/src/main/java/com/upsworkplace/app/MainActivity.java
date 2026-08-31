package com.upsworkplace.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PDF_W = 612;
    private static final int PDF_H = 894;
    private WebView web;
    private File lastPdfFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(53, 28, 21));
        getWindow().setNavigationBarColor(Color.rgb(53, 28, 21));

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setFitsSystemWindows(true);
        web.setWebViewClient(new WebViewClient());

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        web.addJavascriptInterface(new AndroidBridge(), "Android");
        setContentView(web);

        String saved = getPreferences(MODE_PRIVATE).getString("last_pdf", null);
        if (saved != null) {
            File f = new File(saved);
            if (f.exists()) lastPdfFile = f;
        }

        web.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void generateRuolino(String json) {
            runOnUiThread(() -> {
                try {
                    lastPdfFile = buildRuolino(new JSONObject(json));
                    getPreferences(MODE_PRIVATE).edit()
                            .putString("last_pdf", lastPdfFile.getAbsolutePath())
                            .apply();
                    Toast.makeText(MainActivity.this,
                            "Ruolino generato e salvato",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this,
                            "Errore nella generazione del ruolino",
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void shareRuolino() {
            share(makeShareIntent(), "Condividi ruolino");
        }

        @JavascriptInterface
        public void shareEmail() {
            Intent i = makeShareIntent();
            i.putExtra(Intent.EXTRA_SUBJECT, "Ruolino UpsWorkPlace");
            i.putExtra(Intent.EXTRA_TEXT, "Ruolino di lavoro");
            share(i, "Invia ruolino via email");
        }

        @JavascriptInterface
        public void shareWhatsApp() {
            Intent i = makeShareIntent();
            i.setPackage("com.whatsapp");
            try {
                startActivity(i);
            } catch (Exception e) {
                share(makeShareIntent(), "Condividi ruolino");
            }
        }
    }

    private void share(Intent i, String title) {
        if (lastPdfFile == null || !lastPdfFile.exists()) {
            Toast.makeText(this,
                    "Genera prima il ruolino",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            startActivity(Intent.createChooser(i, title));
        } catch (Exception e) {
            Toast.makeText(this,
                    "Impossibile condividere il ruolino",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private Intent makeShareIntent() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");

        if (lastPdfFile != null && lastPdfFile.exists()) {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    lastPdfFile
            );
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        return i;
    }

    private File buildRuolino(JSONObject d) throws Exception {

        File dir = new File(getFilesDir(), "ruolini");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Impossibile creare cartella ruolini");
        }

        String date = d.optString(
                "date",
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.ITALY
                ).format(new Date())
        );

        File out = new File(
                dir,
                "ruolino_" + date.replace("/", "-") + ".pdf"
        );

        Bitmap template;

        try (InputStream in =
                     getAssets().open("ruolino_template.png")) {
            template = BitmapFactory.decodeStream(in);
        }

        if (template == null) {
            throw new Exception("Template ruolino non trovato");
        }

        PdfDocument document = new PdfDocument();

        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(
                        PDF_W,
                        PDF_H,
                        1
                ).create();

        PdfDocument.Page page =
                document.startPage(info);

        Canvas canvas = page.getCanvas();

        Paint imagePaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG |
                        Paint.FILTER_BITMAP_FLAG
                );

        canvas.drawBitmap(
                template,
                null,
                new Rect(0, 0, PDF_W, PDF_H),
                imagePaint
        );

        Paint text =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        text.setColor(Color.BLACK);
        text.setTextSize(8.5f);
        text.setTypeface(
                Typeface.create(
                        Typeface.SERIF,
                        Typeface.NORMAL
                )
        );

        drawText(canvas, text,
                d.optString("driverName", ""),
                35, 48);

        drawText(canvas, text,
                d.optString("driverCode", ""),
                210, 48);

        drawText(canvas, text,
                d.optString("plate", ""),
                400, 48);

        drawText(canvas, text,
                d.optString("date", ""),
                505, 48);

        drawText(canvas, text,
                d.optString("start", ""),
                65, 105);

        drawText(canvas, text,
                d.optString("exit", ""),
                165, 105);

        drawText(canvas, text,
                d.optString("first", ""),
                265, 105);

        drawText(canvas, text,
                d.optString("last", ""),
                365, 105);

        drawText(canvas, text,
                d.optString("return", ""),
                465, 105);

        drawText(canvas, text,
                d.optString("end", ""),
                545, 105);

        drawText(canvas, text,
                d.optString("kmOut", ""),
                65, 135);

        drawText(canvas, text,
                d.optString("kmReturn", ""),
                165, 135);

        drawText(canvas, text,
                String.valueOf(d.optInt("parcels", 0)),
                65, 190);

        drawText(canvas, text,
                String.valueOf(d.optInt("stops", 0)),
                165, 190);

        drawText(canvas, text,
                String.valueOf(d.optInt("pickups", 0)),
                265, 190);

        drawText(canvas, text,
                String.valueOf(d.optInt("picked", 0)),
                365, 190);

        drawText(canvas, text,
                String.valueOf(d.optInt("rites", 0)),
                465, 190);

        JSONObject events =
                d.optJSONObject("events");

        if (events != null) {

            drawText(canvas, text,
                    String.valueOf(events.optInt("g348", 0)),
                    65, 285);

            drawText(canvas, text,
                    String.valueOf(events.optInt("l1kx", 0)),
                    165, 285);

            drawText(canvas, text,
                    String.valueOf(events.optInt("ay49", 0)),
                    265, 285);

            drawText(canvas, text,
                    String.valueOf(events.optInt("si", 0)),
                    365, 285);

            drawText(canvas, text,
                    String.valueOf(events.optInt("kz", 0)),
                    465, 285);

            drawText(canvas, text,
                    String.valueOf(events.optInt("s2", 0)),
                    65, 315);

            drawText(canvas, text,
                    String.valueOf(events.optInt("transfer", 0)),
                    165, 315);

            drawText(canvas, text,
                    String.valueOf(events.optInt("address", 0)),
                    265, 315);

            drawText(canvas, text,
                    String.valueOf(events.optInt("recipient", 0)),
                    365, 315);

            drawText(canvas, text,
                    String.valueOf(events.optInt("refused", 0)),
                    465, 315);
        }

        /*
         * Nel ruolino l'abitacolo viene rappresentato
         * con una semplice spunta quando validato.
         * Il valore 1-5 resta solo nell'app.
         */
        if ("SI".equalsIgnoreCase(
                d.optString("cleaning", "")
        )) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(13f);

            drawText(
                    canvas,
                    text,
                    "✓",
                    475,
                    350
            );
        }

        /*
         * Firma digitale del capo.
         */
        String signature =
                d.optString("signature", "");

        if (!signature.isEmpty()
                && signature.contains(",")) {

            try {

                byte[] bytes =
                        android.util.Base64.decode(
                                signature.substring(
                                        signature.indexOf(',') + 1
                                ),
                                android.util.Base64.DEFAULT
                        );

                Bitmap sign =
                        BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.length
                        );

                if (sign != null) {

                    canvas.drawBitmap(
                            sign,
                            null,
                            new RectF(
                                    360,
                                    810,
                                    575,
                                    880
                            ),
                            imagePaint
                    );
                }

            } catch (Exception ignored) {
            }
        }

        document.finishPage(page);

        try (FileOutputStream fos =
                     new FileOutputStream(out)) {
            document.writeTo(fos);
        }

        document.close();
        template.recycle();

        return out;
    }

    private void drawText(
            Canvas canvas,
            Paint paint,
            String value,
            float x,
            float y
    ) {
        if (value != null
                && !value.trim().isEmpty()) {
            canvas.drawText(
                    value,
                    x,
                    y,
                    paint
            );
        }
    }
}
