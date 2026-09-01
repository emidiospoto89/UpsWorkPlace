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
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PDF_W=612, PDF_H=894;
    private static final int REQ_CAMERA=2001, REQ_LOCATION=2002, REQ_FILE_CHOOSER=2003;
    private WebView web; private File lastPdfFile; private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraOutputUri; private GeolocationPermissions.Callback geoCallback; private String geoOrigin;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(53,28,21)); getWindow().setNavigationBarColor(Color.rgb(53,28,21));
        web=new WebView(this); web.setBackgroundColor(Color.WHITE); web.setFitsSystemWindows(true); web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params){
                if(filePathCallback!=null) filePathCallback.onReceiveValue(null);
                filePathCallback=callback;
                if(params.isCaptureEnabled()){
                    if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
                    else openCameraForFileChooser();
                } else openDocumentForFileChooser(params);
                return true;
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback){
                geoOrigin=origin; geoCallback=callback;
                boolean granted=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
                if(granted) callback.invoke(origin,true,false); else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
            }
        });
        WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setAllowFileAccess(true); settings.setAllowContentAccess(true); settings.setGeolocationEnabled(true);
        web.addJavascriptInterface(new AndroidBridge(),"Android"); setContentView(web);
        String saved=getPreferences(MODE_PRIVATE).getString("last_pdf",null); if(saved!=null){File f=new File(saved);if(f.exists())lastPdfFile=f;}
        web.loadUrl("file:///android_asset/index.html");
    }
    private void openCameraForFileChooser(){
        try{
            Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE); ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,"UpsWorkPlace_"+System.currentTimeMillis()+".jpg"); values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
            cameraOutputUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(cameraOutputUri!=null){camera.putExtra(MediaStore.EXTRA_OUTPUT,cameraOutputUri);camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(camera,REQ_FILE_CHOOSER);} else openDocumentForFileChooser(null);
        }catch(Exception e){openDocumentForFileChooser(null);}
    }
    private void openDocumentForFileChooser(WebChromeClient.FileChooserParams params){
        try{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            String[] types=params!=null?params.getAcceptTypes():new String[]{"*/*"}; String type="*/*";
            if(types!=null && types.length>0){for(String t:types){if(t!=null&&!t.trim().isEmpty()){type=t;break;}}}
            intent.setType(type); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(intent,REQ_FILE_CHOOSER);
        }catch(Exception e){if(filePathCallback!=null){filePathCallback.onReceiveValue(null);filePathCallback=null;} Toast.makeText(this,"Impossibile aprire il selettore file",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data); if(requestCode!=REQ_FILE_CHOOSER||filePathCallback==null)return;
        Uri[] results=null; if(resultCode==RESULT_OK){if(data!=null&&data.getData()!=null)results=new Uri[]{data.getData()}; else if(cameraOutputUri!=null)results=new Uri[]{cameraOutputUri};}
        filePathCallback.onReceiveValue(results); filePathCallback=null; cameraOutputUri=null;
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_CAMERA){boolean granted=grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED;if(granted)openCameraForFileChooser();else if(filePathCallback!=null){filePathCallback.onReceiveValue(null);filePathCallback=null;Toast.makeText(this,"Permesso fotocamera negato",Toast.LENGTH_SHORT).show();}}
        if(requestCode==REQ_LOCATION){boolean granted=false;for(int r:grantResults)if(r==PackageManager.PERMISSION_GRANTED){granted=true;break;}if(geoCallback!=null&&geoOrigin!=null)geoCallback.invoke(geoOrigin,granted,false);geoCallback=null;geoOrigin=null;}
    }
    public class AndroidBridge{
        @JavascriptInterface public void generateRuolino(String json){runOnUiThread(()->{try{lastPdfFile=buildRuolino(new JSONObject(json));getPreferences(MODE_PRIVATE).edit().putString("last_pdf",lastPdfFile.getAbsolutePath()).apply();Toast.makeText(MainActivity.this,"Ruolino generato e salvato",Toast.LENGTH_SHORT).show();}catch(Exception e){e.printStackTrace();Toast.makeText(MainActivity.this,"Errore nella generazione del ruolino: "+e.getMessage(),Toast.LENGTH_LONG).show();}});}
        @JavascriptInterface public void shareRuolino(){share(makeShareIntent(),"Condividi ruolino");}
        @JavascriptInterface public void shareEmail(){Intent i=makeShareIntent();i.putExtra(Intent.EXTRA_SUBJECT,"Ruolino UpsWorkPlace");i.putExtra(Intent.EXTRA_TEXT,"Ruolino di lavoro");share(i,"Invia ruolino via email");}
        @JavascriptInterface public void shareWhatsApp(){Intent i=makeShareIntent();i.setPackage("com.whatsapp");try{startActivity(i);}catch(Exception e){share(makeShareIntent(),"Condividi ruolino");}}
    }
    private void share(Intent intent,String title){if(lastPdfFile==null||!lastPdfFile.exists()){Toast.makeText(this,"Genera prima il ruolino",Toast.LENGTH_SHORT).show();return;}try{startActivity(Intent.createChooser(intent,title));}catch(Exception e){Toast.makeText(this,"Impossibile condividere il ruolino",Toast.LENGTH_SHORT).show();}}
    private Intent makeShareIntent(){Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");if(lastPdfFile!=null&&lastPdfFile.exists()){Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",lastPdfFile);i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}return i;}

    private File buildRuolino(JSONObject d)throws Exception{
        File dir=new File(getFilesDir(),"ruolini"); if(!dir.exists()&&!dir.mkdirs())throw new Exception("Impossibile creare cartella ruolini");
        String date=d.optString("date",new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).format(new Date()));
        File out=new File(dir,"ruolino_"+date.replace("/","-")+".pdf"); Bitmap template;
        try(InputStream in=getAssets().open("ruolino_template.png")){template=BitmapFactory.decodeStream(in);} if(template==null)throw new Exception("Template ruolino non trovato");
        PdfDocument doc=new PdfDocument(); PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(PDF_W,PDF_H,1).create(); PdfDocument.Page page=doc.startPage(info); Canvas c=page.getCanvas();
        Paint ip=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG); c.drawBitmap(template,null,new Rect(0,0,PDF_W,PDF_H),ip);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.BLACK);text.setTextSize(8.5f);text.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));
        drawText(c,text,d.optString("driverName",""),55,76);drawBoxes(c,text,d.optString("plate",""),427,52,576,89);drawText(c,text,d.optString("start",""),170,124);drawText(c,text,d.optString("end",""),455,124);drawText(c,text,d.optString("driverCode",""),110,156);drawBoxes(c,text,d.optString("exit",""),285,134,377,165);drawBoxes(c,text,d.optString("kmOut",""),462,134,593,165);drawBoxes(c,text,d.optString("return",""),285,165,377,197);drawBoxes(c,text,d.optString("kmReturn",""),462,165,593,197);
        if("SI".equalsIgnoreCase(d.optString("cleaning",""))){text.setTypeface(Typeface.DEFAULT_BOLD);text.setTextSize(13f);drawText(c,text,"✓",465,222);text.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));text.setTextSize(8.5f);}
        JSONObject toll=lastObject(d,"tollRecords");if(toll!=null){drawText(c,text,money(toll,"amount"),55,342);drawText(c,text,num(toll,"count"),142,342);} JSONObject fuel=lastObject(d,"fuelRecords");if(fuel!=null){drawText(c,text,money(fuel,"amount"),250,380);drawText(c,text,num(fuel,"liters"),365,380);drawText(c,text,num(fuel,"km"),500,380);}
        drawText(c,text,d.optString("first",""),420,414);drawText(c,text,d.optString("last",""),505,414);drawText(c,text,num(d,"stops"),420,447);drawText(c,text,num(d,"parcels"),420,479);drawText(c,text,num(d,"pickupStops"),420,512);drawText(c,text,num(d,"pickupPackages"),420,545);drawText(c,text,num(d,"futureL"),420,578);drawText(c,text,num(d,"emergencyK"),420,611);
        JSONObject events=d.optJSONObject("events");if(events!=null){drawText(c,text,num(events,"g348"),580,447);drawText(c,text,num(events,"l1kx"),580,479);drawText(c,text,num(events,"ay49"),580,512);drawText(c,text,num(events,"si"),580,545);drawText(c,text,num(events,"kz"),580,578);drawText(c,text,num(events,"s2"),580,611);drawText(c,text,num(events,"transfer"),580,644);drawText(c,text,num(events,"address"),580,677);drawText(c,text,num(events,"recipient"),580,710);drawText(c,text,num(events,"refused"),580,743);}
        drawText(c,text,num(d,"stops"),420,788);drawText(c,text,num(d,"parcels"),520,788);String printable=date;try{Date parsed=new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).parse(date);if(parsed!=null)printable=new SimpleDateFormat("dd/MM/yyyy",Locale.ITALY).format(parsed);}catch(Exception ignored){}drawText(c,text,printable,55,820);
        String sig=d.optString("signature","");if(!sig.isEmpty()&&sig.contains(",")){try{byte[] bytes=android.util.Base64.decode(sig.substring(sig.indexOf(',')+1),android.util.Base64.DEFAULT);Bitmap sign=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(sign!=null)c.drawBitmap(sign,null,new RectF(235,798,445,832),ip);}catch(Exception ignored){}}
        doc.finishPage(page);try(FileOutputStream fos=new FileOutputStream(out)){doc.writeTo(fos);}doc.close();template.recycle();return out;
    }
    private JSONObject lastObject(JSONObject d,String key){try{org.json.JSONArray a=d.optJSONArray(key);return a==null||a.length()==0?null:a.optJSONObject(a.length()-1);}catch(Exception e){return null;}}
    private String num(JSONObject o,String key){if(o==null)return "";Object v=o.opt(key);if(v==null||v==JSONObject.NULL)return "";if(v instanceof Number){int n=((Number)v).intValue();return n==0?"":String.valueOf(n);}String s=String.valueOf(v);return "0".equals(s)?"":s;}
    private String money(JSONObject o,String key){if(o==null)return "";double v=o.optDouble(key,0);return v==0?"":String.format(Locale.ITALY,"%.2f",v);}
    private void drawBoxes(Canvas c,Paint p,String value,float l,float t,float r,float b){if(value==null||value.trim().isEmpty())return;String s=value.trim().replace(" ","");int boxes=5;float w=(r-l)/boxes;p.setTextAlign(Paint.Align.CENTER);for(int i=0;i<s.length()&&i<boxes;i++)c.drawText(String.valueOf(s.charAt(i)),l+w*i+w/2f,t+(b-t)*.78f,p);p.setTextAlign(Paint.Align.LEFT);}
    private void drawText(Canvas c,Paint p,String value,float x,float y){if(value!=null&&!value.trim().isEmpty())c.drawText(value,x,y,p);}
}
