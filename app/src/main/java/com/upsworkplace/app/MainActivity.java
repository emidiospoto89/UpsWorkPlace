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
                new SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(new Date())
        );

        File out = new File(
                dir,
                "ruolino_" + date.replace("/", "-") + ".pdf"
        );

        Bitmap template;
        try (InputStream in = getAssets().open("ruolino_template.png")) {
            template = BitmapFactory.decodeStream(in);
        }

        if (template == null) {
            throw new Exception("Template ruolino non trovato");
        }

        PdfDocument document = new PdfDocument();

        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(PDF_W, PDF_H, 1).create();

        PdfDocument.Page page = document.startPage(info);
        Canvas canvas = page.getCanvas();

        Paint imagePaint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
        );

        canvas.drawBitmap(
                template,
                null,
                new Rect(0, 0, PDF_W, PDF_H),
                imagePaint
        );

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.BLACK);
        text.setTextSize(8.5f);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));

        /*
         * Coordinate ricavate direttamente dal modello ruolino_template.png.
         * Il modello viene prima ridimensionato a 612x894 e poi i dati
         * vengono scritti dentro i relativi riquadri.
         */

        // Intestazione
        drawText(canvas, text, d.optString("driverName", ""), 55, 76);
        drawBoxes(canvas, text, d.optString("plate", ""), 427, 52, 576, 89);

        // Prima riga: inizio/fine lavoro
        drawText(canvas, text, d.optString("start", ""), 170, 124);
        drawText(canvas, text, d.optString("end", ""), 455, 124);

        // Seconda riga: codice, uscita, km uscita
        drawText(canvas, text, d.optString("driverCode", ""), 110, 156);
        drawBoxes(canvas, text, d.optString("exit", ""), 285, 134, 377, 165);
        drawBoxes(canvas, text, d.optString("kmOut", ""), 462, 134, 593, 165);

        // Terza riga: rientro e km rientro
        drawBoxes(canvas, text, d.optString("return", ""), 285, 165, 377, 197);
        drawBoxes(canvas, text, d.optString("kmReturn", ""), 462, 165, 593, 197);

        // Pulizia abitacolo
        if ("SI".equalsIgnoreCase(d.optString("cleaning", ""))) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(13f);
            drawText(canvas, text, "✓", 465, 222);
            text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            text.setTextSize(8.5f);
        }

        // Pedaggi
        JSONObject toll = lastObject(d, "tollRecords");
        if (toll != null) {
            drawText(canvas, text, money(toll, "amount"), 55, 342);
            drawText(canvas, text, num(toll, "count"), 142, 342);
        }

        // Ultimo rifornimento: riga dedicata nel blocco rifornimenti.
        JSONObject fuel = lastObject(d, "fuelRecords");
        if (fuel != null) {
            drawText(canvas, text, money(fuel, "amount"), 250, 380);
            drawText(canvas, text, num(fuel, "liters"), 365, 380);
            drawText(canvas, text, num(fuel, "km"), 500, 380);
        }

        // UPS - primo/ultimo stop e conteggi
        drawText(canvas, text, d.optString("first", ""), 420, 414);
        drawText(canvas, text, d.optString("last", ""), 505, 414);

        drawText(canvas, text, num(d, "stops"), 420, 447);
        drawText(canvas, text, num(d, "parcels"), 420, 479);
        drawText(canvas, text, num(d, "pickupStops"), 420, 512);
        drawText(canvas, text, num(d, "pickupPackages"), 420, 545);
        drawText(canvas, text, num(d, "futureL"), 420, 578);
        drawText(canvas, text, num(d, "emergencyK"), 420, 611);

        JSONObject events = d.optJSONObject("events");

        if (events != null) {
            // Le caselle evento sono le piccole caselle sul margine destro.
            drawText(canvas, text, num(events, "g348"), 580, 447);
            drawText(canvas, text, num(events, "l1kx"), 580, 479);
            drawText(canvas, text, num(events, "ay49"), 580, 512);
            drawText(canvas, text, num(events, "si"), 580, 545);
            drawText(canvas, text, num(events, "kz"), 580, 578);
            drawText(canvas, text, num(events, "s2"), 580, 611);
            drawText(canvas, text, num(events, "transfer"), 580, 644);
            drawText(canvas, text, num(events, "address"), 580, 677);
            drawText(canvas, text, num(events, "recipient"), 580, 710);
            drawText(canvas, text, num(events, "refused"), 580, 743);
        }

        // Totali in fondo al modulo
        drawText(canvas, text, num(d, "stops"), 420, 788);
        drawText(canvas, text, num(d, "parcels"), 520, 788);

        // Data
        String printableDate = date;
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).parse(date);
            if (parsed != null) {
                printableDate = new SimpleDateFormat(
                        "dd/MM/yyyy",
                        Locale.ITALY
                ).format(parsed);
            }
        } catch (Exception ignored) {
        }

        drawText(canvas, text, printableDate, 55, 820);

        // Firma digitale del capo: dentro il riquadro Firma del modello.
        String signature = d.optString("signature", "");

        if (!signature.isEmpty() && signature.contains(",")) {
            try {
                byte[] bytes = android.util.Base64.decode(
                        signature.substring(signature.indexOf(',') + 1),
                        android.util.Base64.DEFAULT
                );

                Bitmap sign = BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.length
                );

                if (sign != null) {
                    canvas.drawBitmap(
                            sign,
                            null,
                            new RectF(235, 798, 445, 832),
                            imagePaint
                    );
                }
            } catch (Exception ignored) {
            }
        }

        document.finishPage(page);

        try (FileOutputStream fos = new FileOutputStream(out)) {
            document.writeTo(fos);
        }

        document.close();
        template.recycle();

        return out;
    }

    private JSONObject lastObject(JSONObject d, String key) {
        try {
            org.json.JSONArray a = d.optJSONArray(key);
            if (a == null || a.length() == 0) return null;
            return a.optJSONObject(a.length() - 1);
        } catch (Exception e) {
            return null;
        }
    }

    private String num(JSONObject o, String key) {
        if (o == null) return "";
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof Number) {
            int n = ((Number) v).intValue();
            return n == 0 ? "" : String.valueOf(n);
        }
        String s = String.valueOf(v);
        return "0".equals(s) ? "" : s;
    }

    private String money(JSONObject o, String key) {
        if (o == null) return "";
        double v = o.optDouble(key, 0);
        return v == 0
                ? ""
                : String.format(Locale.ITALY, "%.2f", v);
    }

    private void drawBoxes(
            Canvas canvas,
            Paint paint,
            String value,
            float left,
            float top,
            float right,
            float bottom
    ) {
        if (value == null || value.trim().isEmpty()) return;

        String s = value.trim().replace(" ", "");
        int boxes = 5;
        float width = (right - left) / boxes;

        paint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < s.length() && i < boxes; i++) {
            canvas.drawText(
                    String.valueOf(s.charAt(i)),
                    left + width * i + width / 2f,
                    top + (bottom - top) * 0.78f,
                    paint
            );
        }

        paint.setTextAlign(Paint.Align.LEFT);
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
