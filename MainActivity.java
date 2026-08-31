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

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectCompatibilityFields(view);
            }
        });

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        web.addJavascriptInterface(new AndroidBridge(), "Android");

        setContentView(web);

        String saved = getPreferences(MODE_PRIVATE)
                .getString("last_pdf", null);

        if (saved != null) {
            File f = new File(saved);
            if (f.exists()) {
                lastPdfFile = f;
            }
        }

        web.loadUrl("file:///android_asset/index.html");
    }

    private void injectCompatibilityFields(WebView v) {

        String js =
                "javascript:(function(){"

                + "var cards=[...document.querySelectorAll('#today .card')];"

                + "var loadCard=cards.find(c=>"
                + "(c.querySelector('h2')||{}).textContent&&"
                + "c.querySelector('h2').textContent.includes('Carico del mattino'));"

                + "if(loadCard){"
                + "var g=loadCard.querySelector('.grid');"
                + "if(g&&!document.getElementById('loop')){"
                + "var w=document.createElement('label');"
                + "w.innerHTML='Loop<input id=\"loop\" type=\"text\">';"
                + "g.appendChild(w);"
                + "}"
                + "}"

                + "if(window.__uwCompat)return;"
                + "window.__uwCompat=true;"

                + "var oldSave=window.saveDay;"

                + "window.saveDay=function(){"
                + "oldSave();"

                + "if(typeof day!=='undefined'){"
                + "day.loop=document.getElementById('loop')?"
                + "document.getElementById('loop').value:'';"
                + "localStorage.setItem('ups_day_'+todayKey,JSON.stringify(day));"
                + "}"
                + "};"

                + "var oldLoad=window.loadDay;"

                + "window.loadDay=function(){"
                + "oldLoad();"

                + "if(typeof day!=='undefined'&&document.getElementById('loop'))"
                + "document.getElementById('loop').value=day.loop||'';"
                + "};"

                + "window.loadDay();"

                + "})();";

        v.evaluateJavascript(js, null);
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void generateRuolino(String json) {

            runOnUiThread(() -> {

                try {

                    lastPdfFile = buildRuolino(new JSONObject(json));

                    getPreferences(MODE_PRIVATE)
                            .edit()
                            .putString(
                                    "last_pdf",
                                    lastPdfFile.getAbsolutePath()
                            )
                            .apply();

                    web.evaluateJavascript(
                            "(function(){"
                            + "if(window.saveRouteToHistory)"
                            + "window.saveRouteToHistory();"
                            + "var e=document.getElementById('generateMsg');"
                            + "if(e){"
                            + "e.className='status ok';"
                            + "e.innerHTML='✓ Ruolino generato e salvato.';"
                            + "}"
                            + "})();",
                            null
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "Ruolino generato e salvato",
                            Toast.LENGTH_SHORT
                    ).show();

                } catch (Exception e) {

                    web.evaluateJavascript(
                            "(function(){"
                            + "var e=document.getElementById('generateMsg');"
                            + "if(e){"
                            + "e.className='status error';"
                            + "e.innerHTML='⚠️ Errore nella generazione del ruolino.';"
                            + "}"
                            + "})();",
                            null
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "Errore nella generazione del ruolino",
                            Toast.LENGTH_LONG
                    ).show();
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

            i.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Ruolino UpsWorkPlace"
            );

            i.putExtra(
                    Intent.EXTRA_TEXT,
                    "Ruolino di lavoro"
            );

            share(i, "Invia ruolino via email");
        }

        @JavascriptInterface
        public void shareWhatsApp() {

            Intent i = makeShareIntent();
            i.setPackage("com.whatsapp");

            if (!safeStart(i)) {
                share(
                        makeShareIntent(),
                        "Condividi ruolino"
                );
            }
        }
    }

    private boolean safeStart(Intent i) {

        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void share(Intent i, String title) {

        if (lastPdfFile == null || !lastPdfFile.exists()) {

            Toast.makeText(
                    this,
                    "Genera prima il ruolino",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            startActivity(
                    Intent.createChooser(i, title)
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Impossibile condividere il ruolino",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private Intent makeShareIntent() {

        Intent i = new Intent(Intent.ACTION_SEND);

        i.setType("application/pdf");

        Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                lastPdfFile
        );

        i.putExtra(Intent.EXTRA_STREAM, uri);

        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return i;
    }

    private File buildRuolino(JSONObject d) throws Exception {

        File dir = new File(
                getFilesDir(),
                "ruolini"
        );

        if (!dir.exists()) {
            dir.mkdirs();
        }

        String date =
                d.optString(
                        "date",
                        new SimpleDateFormat(
                                "dd-MM-yyyy",
                                Locale.ITALIAN
                        ).format(new Date())
                );

        File out = new File(
                dir,
                "ruolino_" + date.replace("/", "-") + ".pdf"
        );

        Bitmap template;

        try (InputStream is =
                     getAssets().open("ruolino_template.png")) {

            template = BitmapFactory.decodeStream(is);
        }

        if (template == null) {
            throw new Exception("Template ruolino non trovato");
        }

        PdfDocument document = new PdfDocument();

        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(
                        PDF_W,
                        PDF_H,
                        1
                ).create();

        PdfDocument.Page page =
                document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);

        Rect src = new Rect(
                0,
                0,
                template.getWidth(),
                template.getHeight()
        );

        RectF dst = new RectF(
                0,
                0,
                PDF_W,
                PDF_H
        );

        canvas.drawBitmap(
                template,
                src,
                dst,
                paint
        );

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.BLACK);
        text.setTypeface(Typeface.create(
                Typeface.SERIF,
                Typeface.NORMAL
        ));

        drawRuolino(canvas, text, d);

        document.finishPage(page);

        FileOutputStream fos =
                new FileOutputStream(out);

        document.writeTo(fos);
        fos.close();

        document.close();

        return out;
    }
        private void drawRuolino(
            Canvas canvas,
            Paint text,
            JSONObject d
    ) {

        /*
         * DATI PRINCIPALI
         */

        String driver =
                d.optString("driverName", "");

        String code =
                d.optString("driverCode", "");

        String plate =
                d.optString("plate", "");

        String date =
                d.optString("date", "");

        String start =
                d.optString("start", "");

        String exit =
                d.optString("exit", "");

        String first =
                d.optString("first", "");

        String last =
                d.optString("last", "");

        String ret =
                d.optString("return", "");

        String end =
                d.optString("end", "");

        String kmOut =
                d.optString("kmOut", "");

        String kmReturn =
                d.optString("kmReturn", "");


        /*
         * NUMERI
         */

        int parcels =
                d.optInt("parcels", 0);

        int stops =
                d.optInt("stops", 0);

        int pickups =
                d.optInt("pickups", 0);

        int picked =
                d.optInt("picked", 0);

        int rites =
                d.optInt("rites", 0);

        int pickupStops =
                d.optInt("pickupStops", 0);

        int pickupPackages =
                d.optInt("pickupPackages", 0);


        /*
         * EVENTI
         */

        JSONObject events =
                d.optJSONObject("events");

        if (events == null) {
            events = new JSONObject();
        }


        int g348 =
                events.optInt("g348", 0);

        int l1kx =
                events.optInt("l1kx", 0);

        int ay49 =
                events.optInt("ay49", 0);

        int si =
                events.optInt("si", 0);

        int kz =
                events.optInt("kz", 0);

        int s2 =
                events.optInt("s2", 0);

        int transfer =
                events.optInt("transfer", 0);

        int address =
                events.optInt("address", 0);

        int recipient =
                events.optInt("recipient", 0);

        int refused =
                events.optInt("refused", 0);


        /*
         * TESTO
         */

        text.setTextSize(8);
        text.setTypeface(
                Typeface.create(
                        Typeface.SERIF,
                        Typeface.NORMAL
                )
        );

        text.setColor(Color.BLACK);


        /*
         * INTESTAZIONE
         */

        drawText(
                canvas,
                text,
                driver,
                55,
                62
        );

        drawText(
                canvas,
                text,
                code,
                215,
                62
        );

        drawText(
                canvas,
                text,
                plate,
                390,
                62
        );

        drawText(
                canvas,
                text,
                date,
                500,
                62
        );


        /*
         * ORARI
         */

        drawText(
                canvas,
                text,
                start,
                75,
                125
        );

        drawText(
                canvas,
                text,
                exit,
                175,
                125
        );

        drawText(
                canvas,
                text,
                first,
                275,
                125
        );

        drawText(
                canvas,
                text,
                last,
                375,
                125
        );

        drawText(
                canvas,
                text,
                ret,
                475,
                125
        );

        drawText(
                canvas,
                text,
                end,
                545,
                125
        );


        /*
         * KM
         */

        drawText(
                canvas,
                text,
                kmOut,
                75,
                155
        );

        drawText(
                canvas,
                text,
                kmReturn,
                175,
                155
        );


        /*
         * PACCHI / STOP
         */

        drawText(
                canvas,
                text,
                String.valueOf(parcels),
                75,
                205
        );

        drawText(
                canvas,
                text,
                String.valueOf(stops),
                175,
                205
        );

        drawText(
                canvas,
                text,
                String.valueOf(pickups),
                275,
                205
        );

        drawText(
                canvas,
                text,
                String.valueOf(picked),
                375,
                205
        );

        drawText(
                canvas,
                text,
                String.valueOf(rites),
                475,
                205
        );


        /*
         * RITIRI
         */

        drawText(
                canvas,
                text,
                String.valueOf(pickupStops),
                75,
                235
        );

        drawText(
                canvas,
                text,
                String.valueOf(pickupPackages),
                175,
                235
        );


        /*
         * EVENTI
         */

        drawText(
                canvas,
                text,
                String.valueOf(g348),
                75,
                300
        );

        drawText(
                canvas,
                text,
                String.valueOf(l1kx),
                175,
                300
        );

        drawText(
                canvas,
                text,
                String.valueOf(ay49),
                275,
                300
        );

        drawText(
                canvas,
                text,
                String.valueOf(si),
                375,
                300
        );

        drawText(
                canvas,
                text,
                String.valueOf(kz),
                475,
                300
        );

        drawText(
                canvas,
                text,
                String.valueOf(s2),
                75,
                330
        );

        drawText(
                canvas,
                text,
                String.valueOf(transfer),
                175,
                330
        );

        drawText(
                canvas,
                text,
                String.valueOf(address),
                275,
                330
        );

        drawText(
                canvas,
                text,
                String.valueOf(recipient),
                375,
                330
        );

        drawText(
                canvas,
                text,
                String.valueOf(refused),
                475,
                330
        );


        /*
         * LOOP
         */

        String loop =
                d.optString("loop", "");

        drawText(
                canvas,
                text,
                loop,
                75,
                365
        );


        /*
         * PULIZIA ABITACOLO
         *
         * Non viene più utilizzato un voto 1-5.
         * Il campo dell'app è SI / NO.
         */

        String cleaning =
                d.optString("cleaning", "");

        if ("SI".equalsIgnoreCase(cleaning)) {

            drawText(
                    canvas,
                    text,
                    "✓",
                    475,
                    365
            );

        } else if ("NO".equalsIgnoreCase(cleaning)) {

            drawText(
                    canvas,
                    text,
                    "X",
                    475,
                    365
            );
        }


        /*
         * FIRMA
         */

        String signature =
                d.optString(
                        "signature",
                        ""
                );

        if (!signature.isEmpty()) {

            try {

                String encoded =
                        signature.substring(
                                signature.indexOf(",") + 1
                        );

                byte[] bytes =
                        android.util.Base64.decode(
                                encoded,
                                android.util.Base64.DEFAULT
                        );

                Bitmap sign =
                        BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.length
                        );

                if (sign != null) {

                    Rect src =
                            new Rect(
                                    0,
                                    0,
                                    sign.getWidth(),
                                    sign.getHeight()
                            );

                    RectF dst =
                            new RectF(
                                    365,
                                    785,
                                    565,
                                    860
                            );

                    Paint signaturePaint =
                            new Paint(
                                    Paint.ANTI_ALIAS_FLAG
                            );

                    signaturePaint.setFilterBitmap(true);

                    canvas.drawBitmap(
                            sign,
                            src,
                            dst,
                            signaturePaint
                    );
                }

            } catch (Exception ignored) {
            }
        }


        /*
         * NOTE
         */

        String notes =
                d.optString(
                        "notes",
                        ""
                );

        if (!notes.isEmpty()) {

            drawWrappedText(
                    canvas,
                    text,
                    notes,
                    55,
                    410,
                    500,
                    12
            );
        }


        /*
         * GPS PRIMO STOP
         */

        String gps =
                d.optString(
                        "firstGps",
                        ""
                );

        if (!gps.isEmpty()) {

            drawText(
                    canvas,
                    text,
                    gps,
                    55,
                    455
            );
        }


        /*
         * PAUSE
         */

        try {

            org.json.JSONArray pauses =
                    d.optJSONArray("pauses");

            if (pauses != null) {

                int y = 490;

                for (
                        int i = 0;
                        i < pauses.length();
                        i++
                ) {

                    JSONObject p =
                            pauses.optJSONObject(i);

                    if (p == null)
                        continue;

                    String ps =
                            p.optString(
                                    "start",
                                    ""
                            );

                    String pe =
                            p.optString(
                                    "end",
                                    ""
                            );

                    String pm =
                            p.optString(
                                    "minutes",
                                    ""
                            );

                    drawText(
                            canvas,
                            text,
                            "Pausa " +
                            (i + 1) +
                            ": " +
                            ps +
                            " - " +
                            pe +
                            " (" +
                            pm +
                            " min)",
                            55,
                            y
                    );

                    y += 14;

                    if (y > 550)
                        break;
                }
            }

        } catch (Exception ignored) {
        }
    }


    private void drawText(
            Canvas canvas,
            Paint paint,
            String value,
            float x,
            float y
    ) {

        if (value == null)
            return;

        if (value.trim().isEmpty())
            return;

        canvas.drawText(
                value,
                x,
                y,
                paint
        );
    }


    private void drawWrappedText(
            Canvas canvas,
            Paint paint,
            String value,
            float x,
            float y,
            float maxWidth,
            float lineHeight
    ) {

        if (value == null)
            return;

        if (value.trim().isEmpty())
            return;

        String[] words =
                value.split("\\s+");

        String line = "";

        float currentY = y;

        for (String word : words) {

            String test;

            if (line.isEmpty())
                test = word;
            else
                test = line + " " + word;

            if (
                    paint.measureText(test)
                    > maxWidth
            ) {

                canvas.drawText(
                        line,
                        x,
                        currentY,
                        paint
                );

                currentY += lineHeight;

                line = word;

            } else {

                line = test;
            }

            if (currentY > PDF_H - 20)
                break;
        }

        if (
                !line.isEmpty() &&
                currentY <= PDF_H - 20
        ) {

            canvas.drawText(
                    line,
                    x,
                    currentY,
                    paint
            );
        }
    }

}
