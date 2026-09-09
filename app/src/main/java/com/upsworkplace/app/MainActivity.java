package com.upsworkplace.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
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
    private static final int PDF_W = 612;
    private static final int PDF_H = 894;
    private static final int TEMPLATE_W = 1037;
    private static final int TEMPLATE_H = 1516;
    private static final int REQ_CAMERA = 2001;
    private static final int REQ_LOCATION = 2002;
    private static final int REQ_FILE_CHOOSER = 2003;

    private WebView web;
    private File lastPdfFile;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraOutputUri;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    private float X(float px) { return px * PDF_W / (float) TEMPLATE_W; }
    private float Y(float py) { return py * PDF_H / (float) TEMPLATE_H; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(53,28,21));
        getWindow().setNavigationBarColor(Color.rgb(53,28,21));
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setFitsSystemWindows(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                if (params.isCaptureEnabled()) {
                    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    else openCameraForFileChooser();
                } else openDocumentForFileChooser(params);
                return true;
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                geoOrigin = origin; geoCallback = callback;
                boolean granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (granted) callback.invoke(origin, true, false);
                else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(true);
        web.addJavascriptInterface(new AndroidBridge(), "Android");
        setContentView(web);
        String saved = getPreferences(MODE_PRIVATE).getString("last_pdf", null);
        if (saved != null) { File f = new File(saved); if (f.exists()) lastPdfFile = f; }
        web.loadUrl("file:///android_asset/index.html");
    }

    private void openCameraForFileChooser() {
        try {
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "UpsWorkPlace_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cameraOutputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (cameraOutputUri != null) {
                camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
                camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(camera, REQ_FILE_CHOOSER);
            } else openDocumentForFileChooser(null);
        } catch (Exception e) { openDocumentForFileChooser(null); }
    }

    private void openDocumentForFileChooser(WebChromeClient.FileChooserParams params) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            String type = "*/*";
            if (params != null) {
                String[] types = params.getAcceptTypes();
                if (types != null) for (String t : types) if (t != null && !t.trim().isEmpty()) { type = t; break; }
            }
            intent.setType(type);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQ_FILE_CHOOSER);
        } catch (Exception e) {
            if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
            Toast.makeText(this, "Impossibile aprire il selettore file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE_CHOOSER || filePathCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) results = new Uri[]{data.getData()};
            else if (cameraOutputUri != null) results = new Uri[]{cameraOutputUri};
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        cameraOutputUri = null;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) openCameraForFileChooser();
            else if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; Toast.makeText(this,"Permesso fotocamera negato",Toast.LENGTH_SHORT).show(); }
        }
        if (requestCode == REQ_LOCATION) {
            boolean granted = false; for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            if (geoCallback != null && geoOrigin != null) geoCallback.invoke(geoOrigin, granted, false);
            geoCallback = null; geoOrigin = null;
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public void generateRuolino(String json) {
            new Thread(() -> {
                try {
                    File generated = buildRuolino(new JSONObject(json));
                    lastPdfFile = generated;
                    getPreferences(MODE_PRIVATE).edit().putString("last_pdf", generated.getAbsolutePath()).apply();
                    runOnUiThread(() -> { notifyRuolinoResult(true, "Ruolino generato e salvato.", generated.getName()); Toast.makeText(MainActivity.this,"Ruolino generato e salvato",Toast.LENGTH_SHORT).show(); });
                } catch (Exception e) {
                    e.printStackTrace();
                    final String msg = (e.getMessage() == null || e.getMessage().trim().isEmpty()) ? e.getClass().getSimpleName() : e.getMessage();
                    runOnUiThread(() -> { notifyRuolinoResult(false,msg,""); Toast.makeText(MainActivity.this,"Errore nella generazione del ruolino: "+msg,Toast.LENGTH_LONG).show(); });
                }
            }).start();
        }
        @JavascriptInterface public void shareRuolino() { share(makeShareIntent(lastPdfFile),"Condividi ruolino"); }
        @JavascriptInterface public void shareEmail() { Intent i=makeShareIntent(lastPdfFile); if(i!=null){i.putExtra(Intent.EXTRA_SUBJECT,"Ruolino UpsWorkPlace");i.putExtra(Intent.EXTRA_TEXT,"Ruolino di lavoro");share(i,"Invia ruolino via email");} }
        @JavascriptInterface public void shareWhatsApp() { Intent i=makeShareIntent(lastPdfFile); if(i==null)return; i.setPackage("com.whatsapp"); try{startActivity(i);}catch(Exception e){share(makeShareIntent(lastPdfFile),"Condividi ruolino");} }
        @JavascriptInterface public void openRuolino(String fileName) {
            File f=getRuolinoFile(fileName); if(f==null||!f.exists()){Toast.makeText(MainActivity.this,"PDF del ruolino non trovato",Toast.LENGTH_SHORT).show();return;}
            try{Uri u=FileProvider.getUriForFile(MainActivity.this,getPackageName()+".fileprovider",f);Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,"application/pdf");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){Toast.makeText(MainActivity.this,"Nessuna app disponibile per aprire il PDF",Toast.LENGTH_SHORT).show();}
        }
        @JavascriptInterface public void shareSavedRuolino(String fileName) {
            File f=getRuolinoFile(fileName); if(f==null||!f.exists()){Toast.makeText(MainActivity.this,"PDF del ruolino non trovato",Toast.LENGTH_SHORT).show();return;}
            Intent i=makeShareIntent(f); if(i!=null) share(i,"Condividi ruolino");
        }
    }

    private void notifyRuolinoResult(boolean success,String message,String fileName){
        if(web==null)return;
        String js="if(typeof onRuolinoGenerated==='function'){onRuolinoGenerated("+(success?"true":"false")+","+JSONObject.quote(message==null?"":message)+","+JSONObject.quote(fileName==null?"":fileName)+");}";
        web.evaluateJavascript(js,null);
    }

    private File getRuolinoFile(String fileName){
        if(fileName==null||fileName.trim().isEmpty()||!fileName.toLowerCase(Locale.ITALY).endsWith(".pdf"))return null;
        File dir=new File(getFilesDir(),"ruolini");File f=new File(dir,fileName);
        try{if(!f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))return null;}catch(Exception e){return null;}
        return f;
    }
    private Intent makeShareIntent(File pdf){
        if(pdf==null||!pdf.exists())return null;
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",pdf);i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);return i;
    }
    private void share(Intent i,String title){
        if(i==null){Toast.makeText(this,"Genera prima il ruolino",Toast.LENGTH_SHORT).show();return;}
        try{startActivity(Intent.createChooser(i,title));}catch(Exception e){Toast.makeText(this,"Impossibile condividere il ruolino",Toast.LENGTH_SHORT).show();}
    }

    private File buildRuolino(JSONObject d)throws Exception{
        File dir=new File(getFilesDir(),"ruolini");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Impossibile creare cartella ruolini");
        String date=d.optString("date",new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).format(new Date()));
        File out=new File(dir,"ruolino_"+date.replace("/","-")+".pdf");
        Bitmap template;try(InputStream in=getAssets().open("ruolino_template.png")){template=BitmapFactory.decodeStream(in);}if(template==null)throw new Exception("Template ruolino non trovato");
        PdfDocument doc=new PdfDocument();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(PDF_W,PDF_H,1).create());Canvas c=page.getCanvas();
        Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);c.drawBitmap(template,null,new Rect(0,0,PDF_W,PDF_H),imagePaint);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));p.setTextSize(8.5f);
        drawRuolino(c,p,d);
        doc.finishPage(page);try(FileOutputStream fos=new FileOutputStream(out)){doc.writeTo(fos);}doc.close();template.recycle();return out;
    }

    private void drawRuolino(Canvas c,Paint p,JSONObject d){
        // Dati compilati: leggermente piu grandi e in grassetto.
        p.setTextSize(10.5f);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);

        drawText(c,p,d.optString("driverName",""),X(190),Y(138));
        drawBoxes(c,p,d.optString("plate",""),754,88,1003,151,7,false);

        // Prima riga: il valore va nella casella a destra della relativa dicitura.
        drawCentered(c,p,d.optString("start",""),268,191,566,248);
        drawCentered(c,p,d.optString("end",""),801,191,1003,248);

        // Seconda/terza riga: campi con caselle dedicate.
        drawBoxes(c,p,d.optString("driverCode",""),169,248,268,305,3,false);
        drawBoxes(c,p,d.optString("exit",""),473,248,641,305,5,false);
        drawBoxesRightAligned(c,p,num(d,"kmOut"),829,248,1003,305,6);
        drawBoxes(c,p,d.optString("return",""),473,305,641,364,5,false);
        drawBoxesRightAligned(c,p,num(d,"kmReturn"),829,305,1003,364,6);
        drawCentered(c,p,d.optString("loop",""),16,248,268,433);
        drawBoxes(c,p,pauseText(d),473,364,641,433,5,false);

        String cleaning=d.optString("cleaning","");
        if(cleaning.matches("[1-5]")){
            p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(14f);
            drawCentered(c,p,"✓",946,364,1003,433);
            p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(10.5f);
        }

        drawTolls(c,p,d.optJSONArray("tollRecords"));
        drawFuel(c,p,d.optJSONArray("fuelRecords"));

        // U.P.S.: orari nelle rispettive caselle, eventi nelle caselle numeriche.
        drawCentered(c,p,d.optString("first",""),638,725,723,774);
        drawCentered(c,p,d.optString("last",""),922,725,1003,774);

        JSONObject e=d.optJSONObject("events");if(e==null)e=new JSONObject();
        float[] rowTops={774,827,879,931,983,1034,1085,1135,1187,1237};
        float[] rowBottoms={827,879,931,983,1034,1085,1135,1187,1237,1287};
        String[] leftKeys={"","deliveryPackages","pickupStops","pickupPackages","futureL","emergencyK","strike","vacation","deceased",""};
        for(int i=0;i<leftKeys.length;i++){
            String value=i<4?num(d,leftKeys[i]):i<6?num(d,leftKeys[i]):num(e,leftKeys[i]);
            drawBoxesRightAligned(c,p,value,638,rowTops[i],723,rowBottoms[i],4);
        }
        String[] rightKeys={"g348","l1kx","ay49","si","kz","s2","transfer","address","recipient","refused"};
        for(int i=0;i<rightKeys.length;i++){
            drawBoxesRightAligned(c,p,num(e,rightKeys[i]),922,rowTops[i],1003,rowBottoms[i],3);
        }

        drawWrapped(c,p,e.optString("notes",""),110,735,215,768,10.5f,3);
        drawWrapped(c,p,damageText(d),35,960,480,1040,10.5f,6);
        drawWrapped(c,p,d.optString("notes",""),35,1115,480,1260,10.5f,10);

        drawCentered(c,p,formatWorkMinutes(d),16,1305,370,1389);
        drawBoxesRightAligned(c,p,totalStops(d),646,1337,723,1389,3);
        drawBoxesRightAligned(c,p,totalExceptionPackages(e),936,1337,1003,1389,3);

        // Data e firma sulle rispettive righe tratteggiate.
        drawText(c,p,formatDate(d.optString("date","")),X(100),Y(1458));
        drawSignature(c,d.optString("signature",""));
    }

    private void drawTolls(Canvas c,Paint p,JSONArray a){
        if(a==null)return;
        int n=Math.min(5,a.length());
        for(int i=0;i<n;i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;
            float[] tops={488,535,582,630,678};
            float top=tops[i];
            // Importo tra il simbolo € e la sigla T.; numero pedaggi nella casella superiore.
            drawCentered(c,p,money(o,"amount"),50,top,190,top+47);
            drawCentered(c,p,num(o,"count"),267,433,363,488);
        }
    }
    private void drawFuel(Canvas c,Paint p,JSONArray a){
        if(a==null)return;
        int n=Math.min(3,a.length());
        for(int i=0;i<n;i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;
            float[] tops={488,535,582,630};
            float top=tops[i];
            drawCentered(c,p,money(o,"amount"),369,top,551,top+47);
            drawBoxesRightAligned(c,p,num(o,"liters"),609,top,779,top+47,5);
            drawBoxesRightAligned(c,p,num(o,"km"),848,top,1003,top+47,5);
        }
    }

    private String pauseText(JSONObject d){
        try{JSONArray a=d.optJSONArray("pauses");if(a==null||a.length()==0)return "";int total=0;for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null)total+=p.optInt("minutes",0);}return total>0?formatMinutes(total):"";}catch(Exception e){return "";}
    }
    private String formatWorkMinutes(JSONObject d){int m=diffMinutes(d.optString("start",""),d.optString("end",""));try{JSONArray a=d.optJSONArray("pauses");if(a!=null)for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null)m-=p.optInt("minutes",0);}}catch(Exception ignored){}return m>0?formatMinutes(m):"";}
    private int diffMinutes(String a,String b){try{if(a==null||b==null||a.length()<4||b.length()<4)return 0;int ah=Integer.parseInt(a.substring(0,2)),am=Integer.parseInt(a.substring(3,5)),bh=Integer.parseInt(b.substring(0,2)),bm=Integer.parseInt(b.substring(3,5));int x=ah*60+am,y=bh*60+bm;if(y<x)y+=1440;return Math.max(0,y-x);}catch(Exception e){return 0;}}
    private String formatMinutes(int m){return (m/60)+":"+String.format(Locale.ITALY,"%02d",m%60);}
    private String deliveryStops(JSONObject d){String s=num(d,"deliveryStops");return s.isEmpty()?"":s;}
    private String deliveryPackages(JSONObject d){String s=num(d,"deliveryPackages");return s.isEmpty()?"":s;}
    private String totalStops(JSONObject d){int total=d.optInt("deliveryStops",0)+d.optInt("pickupStops",0);return total==0?"":String.valueOf(total);}
    private String totalExceptionPackages(JSONObject e){String[] keys={"g348","l1kx","ay49","si","kz","s2","transfer","address","recipient","refused","futureL","emergencyK","strike","vacation","deceased"};int total=0;for(String key:keys)total+=e.optInt(key,0);return total==0?"":String.valueOf(total);}
    private String damageText(JSONObject d){String s=d.optString("damage","");try{JSONArray a=d.optJSONArray("damages");if(a!=null&&a.length()>0){StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("text","").length()>0){if(b.length()>0)b.append(" • ");b.append(o.optString("text",""));}}if(b.length()>0)s=b.toString();}}catch(Exception ignored){}return s;}
    private String num(JSONObject o,String key){if(o==null)return "";Object v=o.opt(key);if(v==null||v==JSONObject.NULL)return "";if(v instanceof Number){int n=((Number)v).intValue();return n==0?"":String.valueOf(n);}String s=String.valueOf(v);return "0".equals(s)?"":s;}
    private String money(JSONObject o,String key){if(o==null)return "";double v=o.optDouble(key,0);return v==0?"":String.format(Locale.ITALY,"%.2f",v);}
    private void drawBoxes(Canvas c,Paint p,String value,float l,float t,float r,float b,int boxes,boolean rightAligned){
        if(value==null||value.trim().isEmpty())return;
        String s=value.trim().replace(" ","");
        float w=(r-l)/boxes;
        int start=rightAligned?Math.max(0,boxes-s.length()):0;
        p.setTextAlign(Paint.Align.CENTER);
        float y=Y((t+b)/2f)-(p.ascent()+p.descent())/2f;
        for(int i=0;i<s.length()&&i<boxes;i++){
            int box=rightAligned?start+i:i;
            c.drawText(String.valueOf(s.charAt(i)),X(l+w*box+w/2f),y,p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }
    private void drawBoxesRightAligned(Canvas c,Paint p,String value,float l,float t,float r,float b,int boxes){drawBoxes(c,p,value,l,t,r,b,boxes,true);}
    private void drawCentered(Canvas c,Paint p,String value,float l,float t,float r,float b){if(value==null||value.trim().isEmpty())return;p.setTextAlign(Paint.Align.CENTER);float y=Y((t+b)/2f)-(p.ascent()+p.descent())/2f;c.drawText(value,X((l+r)/2f),y,p);p.setTextAlign(Paint.Align.LEFT);}
    private void drawRight(Canvas c,Paint p,String value,float x,float y){if(value==null||value.trim().isEmpty())return;p.setTextAlign(Paint.Align.RIGHT);c.drawText(value,X(x),Y(y),p);p.setTextAlign(Paint.Align.LEFT);}
    private void drawRightCentered(Canvas c,Paint p,String value,float x,float t,float b){if(value==null||value.trim().isEmpty())return;p.setTextAlign(Paint.Align.RIGHT);float y=Y((t+b)/2f)-(p.ascent()+p.descent())/2f;c.drawText(value,X(x),y,p);p.setTextAlign(Paint.Align.LEFT);}
    private void drawText(Canvas c,Paint p,String value,float x,float y){if(value!=null&&!value.trim().isEmpty())c.drawText(value,x,y,p);}
    private void drawWrapped(Canvas c,Paint p,String value,float l,float t,float r,float b,float size,int maxLines){if(value==null||value.trim().isEmpty())return;p.setTextSize(size);p.setTextAlign(Paint.Align.LEFT);float max=X(r-l);String[] words=value.replace("\n"," ").split("\\s+");String line="";int lines=0;float yy=Y(t)+p.getTextSize();for(String w:words){String test=line.isEmpty()?w:line+" "+w;if(p.measureText(test)>max&&!line.isEmpty()){if(lines++>=maxLines)break;c.drawText(line,X(l),yy,p);yy+=Y(12);line=w;}else line=test;}if(lines<maxLines&&!line.isEmpty())c.drawText(line,X(l),yy,p);}
    private void drawSignature(Canvas c,String sig){if(sig==null||sig.isEmpty()||!sig.contains(","))return;try{byte[] bytes=android.util.Base64.decode(sig.substring(sig.indexOf(',')+1),android.util.Base64.DEFAULT);Bitmap sign=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(sign!=null){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);c.drawBitmap(sign,null,new RectF(X(400),Y(1405),X(750),Y(1475)),p);sign.recycle();}}catch(Exception ignored){}}
    private String formatDate(String date){try{Date d=new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).parse(date);return new SimpleDateFormat("dd/MM/yyyy",Locale.ITALY).format(d);}catch(Exception e){return date==null?"":date;}}
}
