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

    private static final int W = 1052;
    private static final int H = 1536;

    private static final int PDF_W = 612;
    private static final int PDF_H = 894;

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

                        injectCompatibilityFields(view);

                    }

                }
        );


        WebSettings settings =
                web.getSettings();

        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);


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



    private void injectCompatibilityFields(
            WebView v
    ){

        String js =

                "javascript:(function(){"

                +

                "function addField(card,id,label){"

                +
                "if(document.getElementById(id))return;"

                +
                "var wrap=document.createElement('label');"

                +
                "wrap.style.marginTop='10px';"

                +
                "wrap.innerHTML=label+'<input id=\\\"'+id+'\\\" type=\\\"number\\\">';"

                +
                "card.appendChild(wrap);"

                +
                "}"

                +

                "var cards=[...document.querySelectorAll('#today .card')];"

                +

                "var loadCard=cards.find(c=>"

                +
                "(c.querySelector('h2')||{}).textContent&&"

                +
                "c.querySelector('h2').textContent.includes('Carico del mattino'));"

                +

                "if(loadCard){"

                +
                "var g=loadCard.querySelector('.grid');"

                +
                "if(g&&!document.getElementById('loop')){"

                +
                "var wrap=document.createElement('label');"

                +
                "wrap.innerHTML='Loop<input id=\\\"loop\\\" type=\\\"text\\\">';"

                +
                "g.appendChild(wrap);"

                +
                "}"

                +
                "}"

                +

                "var eventCard=cards.find(c=>"

                +
                "(c.querySelector('h2')||{}).textContent&&"

                +
                "c.querySelector('h2').textContent.includes('Eventi durante'));"

                +

                "if(eventCard){"

                +
                "var g=eventCard.querySelector('.grid');"

                +
                "if(g){"

                +
                "[['eventSciopero','Sciopero'],"

                +
                "['eventVacanza','Vacanza'],"

                +
                "['eventCessata','Deceduto / Cess. attività']]"

                +
                ".forEach(function(a){"

                +
                "if(!document.getElementById(a[0])){"

                +
                "var w=document.createElement('label');"

                +
                "w.innerHTML=a[1]+'<input id=\\\"'+a[0]+'\\\" type=\\\"number\\\">';"

                +
                "g.appendChild(w);"

                +
                "}"

                +
                "});"

                +
                "}"

                +
                "}"

                +

                "if(window.__uwCompat)return;"

                +
                "window.__uwCompat=true;"

                +

                "var oldSave=window.saveDay;"

                +

                "window.saveDay=function(){"

                +
                "oldSave();"

                +
                "day.loop=value('loop');"

                +
                "day.events=day.events||{};"

                +
                "day.events.sciopero=numberValue('eventSciopero');"

                +
                "day.events.vacanza=numberValue('eventVacanza');"

                +
                "day.events.cessata=numberValue('eventCessata');"

                +
                "localStorage.setItem('ups_day_'+todayKey,JSON.stringify(day));"

                +
                "};"

                +

                "var oldLoad=window.loadDay;"

                +

                "window.loadDay=function(){"

                +
                "oldLoad();"

                +
                "setValue('loop',day.loop||'');"

                +
                "if(day.events){"

                +
                "setValue('eventSciopero',day.events.sciopero||0);"

                +
                "setValue('eventVacanza',day.events.vacanza||0);"

                +
                "setValue('eventCessata',day.events.cessata||0);"

                +
                "}"

                +
                "};"

                +

                "window.loadDay();"

                +

                "})();";


        v.evaluateJavascript(
                js,
                null
        );

    }



    public class AndroidBridge {


        @JavascriptInterface
        public void generateRuolino(
                String json
        ){

            runOnUiThread(() -> {

                try {

                    lastPdfFile =
                            buildRuolino(
                                    new JSONObject(json)
                            );


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

                            +
                            "if(window.saveRouteToHistory)"

                            +
                            "window.saveRouteToHistory();"

                            +

                            "var e=document.getElementById('generateMsg');"

                            +

                            "if(e){"

                            +
                            "e.className='status ok';"

                            +
                            "e.innerHTML='✓ Ruolino generato e salvato.';"

                            +
                            "}"

                            +
                            "})();",

                            null

                    );


                    Toast.makeText(
                            MainActivity.this,
                            "Ruolino generato e salvato",
                            Toast.LENGTH_SHORT
                    ).show();


                }
                catch(Exception e){

                    web.evaluateJavascript(

                            "(function(){"

                            +
                            "var e=document.getElementById('generateMsg');"

                            +
                            "if(e){"

                            +
                            "e.className='status error';"

                            +
                            "e.innerHTML='⚠️ Errore nella generazione del ruolino.';"

                            +
                            "}"

                            +
                            "})();",

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

            Intent i =
                    makeShareIntent();

            i.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Ruolino UpsWorkPlace"
            );

            i.putExtra(
                    Intent.EXTRA_TEXT,
                    "Ruolino di lavoro"
            );

            share(
                    i,
                    "Invia ruolino via email"
            );

        }



        @JavascriptInterface
        public void shareWhatsApp(){

            Intent i =
                    makeShareIntent();

            i.setPackage(
                    "com.whatsapp"
            );


            if(!safeStart(i)){

                share(
                        makeShareIntent(),
                        "Condividi ruolino"
                );

            }

        }

    }



    private boolean safeStart(
            Intent i
    ){

        try{

            startActivity(i);

            return true;

        }
        catch(Exception e){

            return false;

        }

    }



    private void share(
            Intent i,
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


        try{

            startActivity(
                    Intent.createChooser(
                            i,
                            title
                    )
            );

        }
        catch(Exception e){

            Toast.makeText(
                    this,
                    "N
