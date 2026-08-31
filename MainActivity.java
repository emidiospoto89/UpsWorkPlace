package com.upsworkplace.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.util.Base64;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    /*
     * Dimensioni del modello PNG.
     * Il PNG viene usato come sfondo del ruolino:
     * non viene ricreato graficamente.
     */
    private static final int TEMPLATE_W = 1024;
    private static final int TEMPLATE_H = 1536;

    private static final int PDF_W = 612;
    private static final int PDF_H = 918;

    private WebView web;
    private File lastPdfFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.rgb(53, 28, 21)
        );

        getWindow().setNavigationBarColor(
                Color.rgb(53, 28, 21)
        );


        web = new WebView(this);

        web.setBackgroundColor(Color.WHITE);

        web.setFitsSystemWindows(true);


        web.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {
                        /*
                         * Il file HTML contiene già tutti i campi
                         * necessari. Non modifichiamo più il DOM
                         * con JavaScript "iniettato".
                         */
                    }

                }
        );


        WebSettings settings =
                web.getSettings();

        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);

        settings.setBuiltInZoomControls(false);

        settings.setDisplayZoomControls(false);


        web.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );


        setContentView(web);


        String saved =
                getPreferences(MODE_PRIVATE)
                        .getString(
                                "last_pdf",
                                null
                        );


        if(saved != null){

            File f =
                    new File(saved);

            if(f.exists())
                lastPdfFile = f;

        }


        web.loadUrl(
                "file:///android_asset/index.html"
        );

    }


    /*
     * =========================================================
     * BRIDGE JAVASCRIPT -> ANDROID
     * =========================================================
     */

    public class AndroidBridge {


        @JavascriptInterface
        public void generateRuolino(
                String json
        ){

            runOnUiThread(() -> {

                try {

                    JSONObject data =
                            new JSONObject(json);


                    lastPdfFile =
                            buildRuolino(data);


                    getPreferences(
                            MODE_PRIVATE
                    )
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


                }
                catch(Exception e){

                    e.printStackTrace();


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
        public void shareRuolino(){

            share(
                    makeShareIntent(),
                    "Condividi ruolino"
            );

        }


        @JavascriptInterface
        public void shareEmail(){

            Intent intent =
                    makeShareIntent();


            intent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Ruolino UpsWorkPlace"
            );


            intent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Ruolino di lavoro"
            );


            share(
                    intent,
                    "Invia ruolino via email"
            );

        }


        @JavascriptInterface
        public void shareWhatsApp(){

            if(
                    lastPdfFile == null ||
                    !lastPdfFile.exists()
            ){

                Toast.makeText(
                        MainActivity.this,
                        "Genera prima il ruolino",
                        Toast.LENGTH_SHORT
                ).show();

                return;

            }


            try {

                Intent intent =
                        makeShareIntent();


                intent.setPackage(
                        "com.whatsapp"
                );


                startActivity(intent);

            }
            catch(Exception e){

                share(
                        makeShareIntent(),
                        "Condividi ruolino"
                );

            }

        }

    }


    /*
     * =========================================================
     * CREAZIONE PDF
     * =========================================================
     */

    private File buildRuolino(
            JSONObject d
    ) throws Exception {


        /*
         * Carica il PNG originale.
         *
         * IMPORTANTE:
         * il file deve essere:
         *
         * app/src/main/assets/ruolino_template.png
         */

        InputStream input =
                getAssets().open(
                        "ruolino_template.png"
                );


        Bitmap template =
                BitmapFactory.decodeStream(input);


        input.close();


        if(template == null)
            throw new Exception(
                    "Impossibile caricare ruolino_template.png"
            );


        /*
         * Usiamo le dimensioni REALI del PNG.
         * In questo modo non deformiamo il modello.
         */

        int imageW =
                template.getWidth();

        int imageH =
                template.getHeight();


        /*
         * Crea documento PDF.
         */

        PdfDocument document =
                new PdfDocument();


        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(
                        PDF_W,
                        PDF_H,
                        1
                ).create();


        PdfDocument.Page page =
                document.startPage(pageInfo);


        Canvas canvas =
                page.getCanvas();


        /*
         * Sfondo del ruolino.
         */

        Rect src =
                new Rect(
                        0,
                        0,
                        imageW,
                        imageH
                );


        RectF dst =
                new RectF(
                        0,
                        0,
                        PDF_W,
                        PDF_H
                );


        Paint imagePaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG |
                        Paint.FILTER_BITMAP_FLAG
                );


        canvas.drawBitmap(
                template,
                src,
                dst,
                imagePaint
        );


        /*
         * Tutti i dati vengono scritti sopra
         * il modello originale.
         */

        Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        paint.setColor(
                Color.BLACK
        );

        paint.setTypeface(
                Typeface.create(
                        Typeface.SERIF,
                        Typeface.NORMAL
                )
        );

        paint.setTextSize(
                10
        );


        /*
         * Scala tra PNG e PDF.
         */

        float sx =
                (float) PDF_W /
                (float) imageW;

        float sy =
                (float) PDF_H /
                (float) imageH;


        /*
         * Disegniamo usando coordinate del PNG.
         */

        canvas.save();

        canvas.scale(
                sx,
                sy
        );


        /*
         * =====================================================
         * DATI PRINCIPALI
         * =====================================================
         */

        drawText(
                canvas,
                paint,
                text(d, "driverName"),
                48,
                137,
                17
        );


        drawText(
                canvas,
                paint,
                text(d, "plate"),
                752,
                138,
                15
        );


        /*
         * =====================================================
         * ORARI
         * =====================================================
         */

        drawText(
                canvas,
                paint,
                text(d, "start"),
                350,
                218,
                15
        );


        drawText(
                canvas,
                paint,
                text(d, "end"),
                795,
                218,
                15
        );


        drawText(
                canvas,
                paint,
                text(d, "driverCode"),
                230,
                274,
                14
        );


        drawText(
                canvas,
                paint,
                text(d, "exit"),
                475,
                274,
                14
        );


        drawText(
                canvas,
                paint,
                text(d, "kmOut"),
                850,
                274,
                14
        );


        /*
         * Loop.
         */

        drawText(
                canvas,
                paint,
                text(d, "loop"),
                50,
                350,
                14
        );


        /*
         * Rientro.
         */

        drawText(
                canvas,
                paint,
                text(d, "return"),
                475,
                315,
                14
        );


        drawText(
                canvas,
                paint,
                text(d, "kmReturn"),
                850,
                315,
                14
        );


        /*
         * =====================================================
         * PAUSA
         * =====================================================
         *
         * Nel ruolino viene riportata la durata totale.
         * Gli orari delle singole pause restano invece
         * nello storico dell'app.
         */

        int pauseMinutes =
                getPauseMinutes(d);


        drawText(
                canvas,
                paint,
                formatPause(pauseMinutes),
                510,
                355,
                14
        );


        /*
         * =====================================================
         * PULIZIA ABITACOLO
         * =====================================================
         *
         * ATTENZIONE:
         * nel ruolino NON riportiamo il valore 1-5.
         * Mettiamo semplicemente una spunta.
         */

        if(
                "SI".equalsIgnoreCase(
                        text(d, "cleaning")
                )
                ||
                "true".equalsIgnoreCase(
                        text(d, "cleaning")
                )
        ){

            drawCheck(
                    canvas,
                    950,
                    357
            );

        }


        /*
         * =====================================================
         * RIFORNIMENTI
         * =====================================================
         */

        JSONArray fuels =
                d.optJSONArray(
                        "fuelRecords"
                );


        if(fuels != null){

            int max =
                    Math.min(
                            fuels.length(),
                            4
                    );


            for(
                    int i = 0;
                    i < max;
                    i++
            ){

                JSONObject f =
                        fuels.optJSONObject(i);


                if(f == null)
                    continue;


                float y =
                        510 + (i * 55);


                /*
                 * Prezzo / totale
                 */

                drawText(
                        canvas,
                        paint,
                        money(
                                f.optDouble(
                                        "amount",
                                        0
                                )
                        ),
                        390,
                        y,
                        13
                );


                /*
                 * Litri
                 */

                drawText(
                        canvas,
                        paint,
                        number(
                                f.optDouble(
                                        "liters",
                                        0
                                )
                        ),
                        590,
                        y,
                        13
                );


                /*
                 * Km
                 */

                drawText(
                        canvas,
                        paint,
                        String.valueOf(
                                f.optInt(
                                        "km",
                                        0
                                )
                        ),
                        850,
                        y,
                        13
                );

            }

        }


        /*
         * =====================================================
         * PEDAGGI
         * =====================================================
         */

        JSONArray tolls =
                d.optJSONArray(
                        "tollRecords"
                );


        if(tolls != null){

            int max =
                    Math.min(
                            tolls.length(),
                            5
                    );


            for(
                    int i = 0;
                    i < max;
                    i++
            ){

                JSONObject t =
                        tolls.optJSONObject(i);


                if(t == null)
                    continue;


                float y =
                        510 + (i * 55);


                drawText(
                        canvas,
                        paint,
                        money(
                                t.optDouble(
                                        "amount",
                                        0
                                )
                        ),
                        42,
                        y,
                        13
                );

            }

        }


        /*
         * =====================================================
         * UPS
         * =====================================================
         */

        drawText(
                canvas,
                paint,
                text(d, "first"),
                665,
                666,
                14
        );


        drawText(
                canvas,
                paint,
                text(d, "last"),
                850,
                666,
                14
        );


        /*
         * Stop consegna.
         */

        drawText(
                canvas,
                paint,
                integer(
                        d.optInt(
                                "stops",
                                0
                        )
                ),
                665,
                724,
                14
        );


        /*
         * Pacchi in consegna.
         */

        drawText(
                canvas,
                paint,
                integer(
                        d.optInt(
                                "parcels",
                                0
                        )
                ),
                665,
                778,
                14
        );


        /*
         * Stop pickup.
         */

        drawText(
                canvas,
                paint,
                integer(
                        d.optInt(
                                "pickupStops",
                                0
                        )
                ),
                665,
                834,
                14
        );


        /*
         * Pacchi in ritiro.
         */

        drawText(
                canvas,
                paint,
                integer(
                        d.optInt(
                                "pickupPackages",
                                0
                        )
                ),
                665,
                888,
                14
        );


        /*
         * =====================================================
         * EVENTI DI CONSEGNA
         * =====================================================
         */

        JSONObject events =
                d.optJSONObject(
                        "events"
                );


        if(events != null){

            /*
             * Colonna sinistra.
             */

            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "g348",
                                    0
                            )
                    ),
                    965,
                    724,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "l1kx",
                                    0
                            )
                    ),
                    965,
                    778,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "ay49",
                                    0
                            )
                    ),
                    965,
                    834,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "si",
                                    0
                            )
                    ),
                    965,
                    888,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "kz",
                                    0
                            )
                    ),
                    965,
                    942,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "s2",
                                    0
                            )
                    ),
                    965,
                    996,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "transfer",
                                    0
                            )
                    ),
                    965,
                    1050,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "address",
                                    0
                            )
                    ),
                    965,
                    1104,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "recipient",
                                    0
                            )
                    ),
                    965,
                    1158,
                    13
            );


            drawText(
                    canvas,
                    paint,
                    integer(
                            events.optInt(
                                    "refused",
                                    0
                            )
                    ),
                    965,
                    1212,
                    13
            );

        }


        /*
         * =====================================================
         * TOTALE ORE
         * =====================================================
         */

        drawText(
                canvas,
                paint,
                formatWorkHours(
                        text(d, "start"),
                        text(d, "end"),
                        pauseMinutes
                ),
                205,
                1280,
                16
        );


        /*
         * =====================================================
         * TOTALE STOP
         * =====================================================
         */

        drawText(
                canvas,
                paint,
                integer(
                        d.optInt(
                                "stops",
                                0
                        )
                ),
                665,
                1300,
                15
        );


        /*
         * =====================================================
         * TOTALE PACCHI ECC.
         * =====================================================
         *
         * Gli espressi NON vengono sommati.
         *
         * Totale = pacchi normali.
         */

        drawText(
                canvas,
                paint,
                integer(
                        d.optInt(
                                "parcels",
                                0
                        )
                ),
                850,
                1300,
                15
        );


        /*
         * =====================================================
         * DATA
         * =====================================================
         */

        String date =
                formatDate(
                        text(d, "date")
                );


        drawText(
                canvas,
                paint,
                date,
                185,
                1435,
                15
        );


        /*
         * =====================================================
         * FIRMA
         * =====================================================
         */

        String signature =
                text(
                        d,
                        "signature"
                );


        if(
                signature != null &&
                !signature.isEmpty()
        ){

            drawSignature(
                    canvas,
                    signature
            );

        }


        canvas.restore();


        document.finishPage(
                page
        );


        /*
         * =====================================================
         * SALVATAGGIO
         * =====================================================
         */

        File directory =
                new File(
                        getFilesDir(),
                        "ruolini"
                );


        if(!directory.exists())
            directory.mkdirs();


        String fileName =
                "Ruolino_"
                + safeFileDate(
                        text(d, "date")
                )
                + ".pdf";


        File output =
                new File(
                        directory,
                        fileName
                );


        /*
         * Se esiste già il ruolino della stessa giornata,
         * lo sostituiamo.
         */

        if(output.exists())
            output.delete();


        FileOutputStream fos =
                new FileOutputStream(
                        output
                );


        document.writeTo(
                fos
        );


        fos.flush();

        fos.close();

        document.close();


        return output;

    }


    /*
     * =========================================================
     * TESTO
     * =========================================================
     */

    private void drawText(
            Canvas canvas,
            Paint paint,
            String value,
            float x,
            float y,
            float size
    ){

        if(value == null)
            value = "";


        if(value.trim().isEmpty())
            return;


        paint.setTextSize(size);

        paint.setColor(
                Color.BLACK
        );

        paint.setTypeface(
                Typeface.create(
                        Typeface.SERIF,
                        Typeface.NORMAL
                )
        );


        canvas.drawText(
                value,
                x,
                y,
                paint
        );

    }


    /*
     * =========================================================
     * SPUNTA PULIZIA ABITACOLO
     * =========================================================
     */

    private void drawCheck(
            Canvas canvas,
            float x,
            float y
    ){

        Paint p =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        p.setColor(
                Color.BLACK
        );


        p.setStyle(
                Paint.Style.STROKE
        );


        p.setStrokeWidth(
                3.2f
        );


        p.setStrokeCap(
                Paint.Cap.ROUND
        );


        Path path =
                new Path();


        path.moveTo(
                x - 10,
                y
        );


        path.lineTo(
                x - 2,
                y + 9
        );


        path.lineTo(
                x + 15,
                y - 12
        );


        canvas.drawPath(
                path,
                p
        );

    }


    /*
     * =========================================================
     * FIRMA DIGITALE
     * =========================================================
     */

    private void drawSignature(
            Canvas canvas,
            String base64
    ){

        try {

            String clean =
                    base64;


            if(
                    clean.contains(",")
            ){

                clean =
                        clean.substring(
                                clean.indexOf(",") + 1
                        );

            }


            byte[] bytes =
                    Base64.decode(
                            clean,
                            Base64.DEFAULT
                    );


            Bitmap signature =
                    BitmapFactory.decodeByteArray(
                            bytes,
                            0,
                            bytes.length
                    );


            if(signature == null)
                return;


            /*
             * Zona firma in fondo al modello.
             */

            Rect src =
                    new Rect(
                            0,
                            0,
                            signature.getWidth(),
                            signature.getHeight()
                    );


            RectF dst =
                    new RectF(
                            380,
                            1390,
                            750,
                            1480
                    );


            Paint p =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG |
                            Paint.FILTER_BITMAP_FLAG
                    );


            canvas.drawBitmap(
                    signature,
                    src,
                    dst,
                    p
            );


        }
        catch(Exception e){

            e.printStackTrace();

        }

    }


    /*
     * =========================================================
     * PAUSE
     * =========================================================
     */

    private int getPauseMinutes(
            JSONObject d
    ){

        int total = 0;


        JSONArray pauses =
                d.optJSONArray(
                        "pauses"
                );


        if(pauses == null)
            return 0;


        for(
                int i = 0;
                i < pauses.length();
                i++
        ){

            JSONObject p =
                    pauses.optJSONObject(i);


            if(p == null)
                continue;


            total +=
                    p.optInt(
                            "minutes",
                            0
                    );

        }


        return total;

    }


    /*
     * =========================================================
     * ORE LAVORATE
     * =========================================================
     */

    private String formatWorkHours(
            String start,
            String end,
            int pauseMinutes
    ){

        int minutes =
                diffMinutes(
                        start,
                        end
                );


        minutes =
                Math.max(
                        0,
                        minutes - pauseMinutes
                );


        int hours =
                minutes / 60;


        int mins =
                minutes % 60;


        return hours
                + ":"
                + String.format(
                        Locale.US,
                        "%02d",
                        mins
                );

    }


    private int diffMinutes(
            String a,
            String b
    ){

        if(
                a == null ||
                b == null ||
                a.isEmpty() ||
                b.isEmpty()
        )
            return 0;


        try {

            String[] pa =
                    a.split(":");


            String[] pb =
                    b.split(":");


            int ma =
                    Integer.parseInt(pa[0]) * 60
                    +
                    Integer.parseInt(pa[1]);


            int mb =
                    Integer.parseInt(pb[0]) * 60
                    +
                    Integer.parseInt(pb[1]);


            if(mb < ma)
                mb += 1440;


            return mb - ma;

        }
        catch(Exception e){

            return 0;

        }

    }


    private String formatPause(
            int minutes
    ){

        int hours =
                minutes / 60;


        int mins =
                minutes % 60;


        if(hours == 0)
            return String.format(
                    Locale.US,
                    "%02d",
                    mins
            );


        return hours
                + ":"
                + String.format(
                        Locale.US,
                        "%02d",
                        mins
                );

    }


    /*
     * =========================================================
     * CONDIVISIONE PDF
     * =========================================================
     */

    private Intent makeShareIntent(){

        Intent intent =
                new Intent(
                        Intent.ACTION_SEND
                );


        intent.setType(
                "application/pdf"
        );


        Uri uri =
                FileProvider.getUriForFile(
                        this,
                        getPackageName()
                                + ".fileprovider",
                        lastPdfFile
                );


        intent.putExtra(
                Intent.EXTRA_STREAM,
                uri
        );


        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );


        return intent;

    }


    private void share(
            Intent intent,
            String title
    ){

        if(
                lastPdfFile == null ||
                !lastPdfFile.exists()
        ){

            Toast.makeText(
                    this,
                    "Genera prima il ruolino",
                    Toast.LENGTH_SHORT
            ).show();


            return;

        }


        try {

            startActivity(
                    Intent.createChooser(
                            intent,
                            title
                    )
            );

        }
        catch(Exception e){

            Toast.makeText(
                    this,
                    "Impossibile condividere il ruolino",
                    Toast.LENGTH_LONG
            ).show();

        }

    }


    /*
     * =========================================================
     * UTILITÀ JSON
     * =========================================================
     */

    private String text(
            JSONObject obj,
            String key
    ){

        if(
                obj == null ||
                !obj.has(key) ||
                obj.isNull(key)
        )
            return "";


        return String.valueOf(
                obj.opt(key)
        );

    }


    private String integer(
            int value
    ){

        return String.valueOf(
                value
        );

    }


    private String number(
            double value
    ){

        if(
                value == Math.rint(value)
        ){

            return String.valueOf(
                    (int)value
            );

        }


        return String.format(
                Locale.US,
                "%.2f",
                value
        );

    }


    private String money(
            double value
    ){

        return String.format(
                Locale.US,
                "%.2f",
                value
        );

    }


    /*
     * =========================================================
     * DATA
     * =========================================================
     */

    private String formatDate(
            String value
    ){

        if(
                value == null ||
                value.isEmpty()
        ){

            return new SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.ITALIAN
            ).format(
                    new Date()
            );

        }


        try {

            Date date =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    ).parse(value);


            return new SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.ITALIAN
            ).format(date);

        }
        catch(Exception e){

            return value;

        }

    }


    private String safeFileDate(
            String value
    ){

        if(
                value == null ||
                value.isEmpty()
        ){

            return new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            ).format(
                    new Date()
            );

        }


        return value.replace(
                "/",
                "-"
        );

    }

}
