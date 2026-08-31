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
import android.util.Base64;

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

        String saved = getPreferences(MODE_PRIVATE).getString("last_pdf", null);
        if (saved != null) {
            File f = new File(saved);
            if (f.exists()) lastPdfFile = f;
        }

        web.loadUrl("file:///android_asset/index.html");
    }

    private void injectCompatibilityFields(WebView v) {
        String js = "javascript:(function(){"
                + "function addField(card,id,label){"
                + "if(document.getElementById(id))return;"
                + "var wrap=document.createElement('label');"
                + "wrap.style.marginTop='10px';"
                + "wrap.innerHTML=label+'<input id=\\\"'+id+'\\\" type=\\\"number\\\">';"
                + "card.appendChild(wrap);"
                + "}"
                + "var cards=[...document.querySelectorAll('#today .card')];"
                + "var loadCard=cards.find(c=>(c.querySelector('h2')||{}).textContent&&c.querySelector('h2').textContent.includes('Carico del mattino'));"
                + "if(loadCard){var g=loadCard.querySelector('.grid');if(g&&!document.getElementById('loop')){var wrap=document.createElement('label');wrap.innerHTML='Loop<input id=\\\"loop\\\" type=\\\"text\\\">';g.appendChild(wrap);}}"
                + "var eventCard=cards.find(c=>(c.querySelector('h2')||{}).textContent&&c.querySelector('h2').textContent.includes('Eventi durante'));"
                + "if(eventCard){var g=eventCard.querySelector('.grid');if(g){[['eventSciopero','Sciopero'],['eventVacanza','Vacanza'],['eventCessata','Deceduto / Cess. attività']].forEach(function(a){if(!document.getElementById(a[0])){var w=document.createElement('label');w.innerHTML=a[1]+'<input id=\\\"'+a[0]+'\\\" type=\\\"number\\\">';g.appendChild(w);}});}}"
                + "if(window.__uwCompat)return;window.__uwCompat=true;"
                + "var oldSave=window.saveDay;"
                + "window.saveDay=function(){oldSave();day.loop=value('loop');day.events=day.events||{};day.events.sciopero=numberValue('eventSciopero');day.events.vacanza=numberValue('eventVacanza');day.events.cessata=numberValue('eventCessata');localStorage.setItem('ups_day_'+todayKey,JSON.stringify(day));};"
                + "var oldLoad=window.loadDay;"
                + "window.loadDay=function(){oldLoad();setValue('loop',day.loop||'');if(day.events){setValue('eventSciopero',day.events.sciopero||0);setValue('eventVacanza',day.events.vacanza||0);setValue('eventCessata',day.events.cessata||0);}};"
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

                    getPreferences(MODE_PRIVATE).edit()
                            .putString("last_pdf", lastPdfFile.getAbsolutePath())
                            .apply();

                    web.evaluateJavascript("(function(){"
                            + "if(window.saveRouteToHistory)window.saveRouteToHistory();"
                            + "var e=document.getElementById('generateMsg');"
                            + "if(e){e.className='status ok';e.innerHTML='✓ Ruolino generato e salvato.';}"
                            + "})();", null);

                    Toast.makeText(MainActivity.this, "Ruolino generato e salvato", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    web.evaluateJavascript("(function(){var e=document.getElementById('generateMsg');if(e){e.className='status error';e.innerHTML='⚠️ Errore nella generazione del ruolino.';}})();", null);
                    Toast.makeText(MainActivity.this, "Errore nella generazione del ruolino", Toast.LENGTH_LONG).show();
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
            if (!safeStart(i)) share(makeShareIntent(), "Condividi ruolino");
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
            Toast.makeText(this, "Genera prima il ruolino", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            startActivity(Intent.createChooser(i, title));
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile condividere il ruolino", Toast.LENGTH_SHORT).show();
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
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Impossibile creare cartella ruolini");

        String date = str(d, "date");
        if (date.isEmpty()) date = new SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(new Date());
        String safeDate = date.replaceAll("[^0-9-]", "_");

        File out = new File(dir, "ruolino_" + safeDate + ".pdf");

        Bitmap template;
        try (InputStream in = getAssets().open("ruolino_template.png")) {
            template = BitmapFactory.decodeStream(in);
        }
        if (template == null) throw new Exception("Template ruolino non trovato");

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PDF_W, PDF_H, 1).create();
        PdfDocument.Page page = document.startPage(info);
        Canvas c = page.getCanvas();

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(template, null, new Rect(0, 0, PDF_W, PDF_H), p);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.BLACK);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        text.setTextSize(9.2f);

        put(c, text, str(d,"driverName"), 28, 31, 330);
        putBoxes(c, text, str(d,"plate"), 444, 30, 590, 45, 7);
        put(c, text, str(d,"start"), 72, 105, 245);
        put(c, text, str(d,"end"), 475, 105, 585);
        put(c, text, str(d,"driverCode"), 110, 137, 160);
        put(c, text, str(d,"exit"), 305, 137, 400);
        putBoxes(c, text, str(d,"kmOut"), 485, 137, 590, 151, 5);
        put(c, text, str(d,"return"), 305, 169, 400);
        putBoxes(c, text, str(d,"kmReturn"), 485, 169, 590, 183, 5);

        // Nel ruolino l'abitacolo viene validato solo con una spunta.
        if ("SI".equalsIgnoreCase(str(d,"cleaning"))) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(13);
            c.drawText("✓", 530, 188, text);
            text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            text.setTextSize(9.2f);
        }

        put(c, text, num(d,"parcels"), 150, 219, 205);
        put(c, text, num(d,"stops"), 215, 219, 260);
        put(c, text, num(d,"pickups"), 270, 219, 315);
        put(c, text, num(d,"picked"), 325, 219, 365);

        JSONObject fuel = lastObject(d, "fuelRecords");
        if (fuel != null) {
            put(c, text, num(fuel,"liters"), 385, 219, 435);
            put(c, text, money(fuel,"amount"), 440, 219, 500);
            put(c, text, num(fuel,"km"), 505, 219, 585);
        }

        JSONObject toll = lastObject(d, "tollRecords");
        if (toll != null) {
            put(c, text, money(toll,"amount"), 60, 255, 130);
            put(c, text, num(toll,"count"), 135, 255, 165);
        }

        put(c, text, str(d,"first"), 390, 350, 420);
        put(c, text, str(d,"last"), 520, 350, 585);
        put(c, text, num(d,"stops"), 390, 383, 420);
        put(c, text, num(d,"parcels"), 390, 416, 420);
        put(c, text, num(d,"pickupStops"), 390, 449, 420);
        put(c, text, num(d,"pickupPackages"), 390, 482, 420);
        put(c, text, num(d,"futureL"), 390, 515, 420);
        put(c, text, num(d,"emergencyK"), 390, 548, 420);

        JSONObject ev = d.optJSONObject("events");
        if (ev != null) {
            put(c,text,num(ev,"g348"),530,383,585);
            put(c,text,num(ev,"l1kx"),530,416,585);
            put(c,text,num(ev,"ay49"),530,449,585);
            put(c,text,num(ev,"si"),530,482,585);
            put(c,text,num(ev,"kz"),530,515,585);
            put(c,text,num(ev,"s2"),530,548,585);
            put(c,text,num(ev,"transfer"),530,581,585);
            put(c,text,num(ev,"address"),530,614,585);
            put(c,text,num(ev,"recipient"),530,647,585);
            put(c,text,num(ev,"refused"),530,680,585);
        }

        String sig = str(d, "signature");
        if (!sig.isEmpty()) {
            try {
                String b64 = sig.contains(",") ? sig.substring(sig.indexOf(',') + 1) : sig;
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                Bitmap sign = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (sign != null) {
                    RectF dst = new RectF(360, 842, 585, 884);
                    c.drawBitmap(sign, null, dst, p);
                }
            } catch (Exception ignored) { }
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

    private String str(JSONObject o, String key) {
        if (o == null) return "";
        Object v = o.opt(key);
        return v == null || v == JSONObject.NULL ? "" : String.valueOf(v);
    }

    private String num(JSONObject o, String key) {
        if (o == null) return "";
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof Number) return String.valueOf(((Number)v).intValue());
        String s = String.valueOf(v);
        return s.equals("0") ? "" : s;
    }

    private String money(JSONObject o, String key) {
        if (o == null) return "";
        double v = o.optDouble(key, 0);
        return v == 0 ? "" : String.format(Locale.ITALY, "%.2f", v);
    }

    private void put(Canvas c, Paint p, String value, float left, float y, float right) {
        if (value == null || value.trim().isEmpty()) return;
        String s = value.trim();
        float max = right - left;
        while (s.length() > 1 && p.measureText(s) > max) {
            s = s.substring(0, s.length() - 1);
        }
        c.drawText(s, left, y, p);
    }

    private void putBoxes(Canvas c, Paint p, String value, float left, float top, float right, float bottom, int boxes) {
        if (value == null || value.trim().isEmpty()) return;
        String s = value.trim().replace(" ", "");
        float width = (right - left) / boxes;
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < s.length() && i < boxes; i++) {
            c.drawText(String.valueOf(s.charAt(i)),
                    left + width * i + width / 2f,
                    top + (bottom - top) * .78f,
                    p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }
}
