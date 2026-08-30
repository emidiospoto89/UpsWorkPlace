import React, { useEffect, useMemo, useState } from "react";
import { SafeAreaView, StatusBar, StyleSheet, Text, TouchableOpacity, View, ScrollView, Alert } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { WebView } from "react-native-webview";

const STORAGE_KEY = "upsworkplace.v3.days";
const PROFILE_KEY = "upsworkplace.v3.profile";

const MENU = [
  ["home","🏠","Giornata di oggi"],
  ["driver","👤","Driver"],
  ["history","📚","Storico giornate"],
  ["hours","⏱️","Ore lavorate"],
  ["leave","🏖️","Ferie / Permessi / Malattia"],
  ["vehicle","🚐","Veicolo"],
  ["damage","🔧","Guasti e danni"],
  ["fuel","💶","Rifornimenti e pedaggi"],
  ["ruolino","📄","Ruolino"],
  ["payslips","💰","Buste paga"],
  ["stats","📊","Statistiche"],
  ["settings","⚙️","Impostazioni"],
];

function isoDate(d=new Date()){
  const p=n=>String(n).padStart(2,"0");
  return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}`;
}
function todayLabel(){
  return new Date().toLocaleDateString("it-IT",{weekday:"long",day:"2-digit",month:"long",year:"numeric"});
}
function blankDay(){
  return {
    date: isoDate(),
    driver:"", code:"", plate:"",
    startWork:null, warehouseExit:null, itineraryFinalized:false,
    kmOut:"", parcels:"", stops:"", express:"",
    firstStop:null, firstStopGps:null, lastStop:null,
    pauses:[], pickups:0, pickedParcels:0, extraStops:0, extraParcels:0,
    failedDeliveries:[], fuelings:[], tolls:0, missedTolls:0,
    returnTime:null, kmReturn:"", cleaning:"", endWork:null,
    damages:[], notes:"",
    updatedAt:new Date().toISOString()
  };
}
const timeNow=()=>new Date().toISOString();

const DAY_HTML = `<!doctype html><html lang="it"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>UpsWorkPlace</title>
<style>
:root{font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#171717;background:#f3f3f3}
*{box-sizing:border-box}body{margin:0}.wrap{max-width:780px;margin:auto;padding:12px}
header{background:#351c15;color:#fff;padding:15px;border-radius:0 0 14px 14px}.brand{font-size:22px;font-weight:900}.sub{font-size:12px;opacity:.75}
.card{background:#fff;border-radius:16px;padding:15px;margin:12px 0;box-shadow:0 1px 6px #0001}
h2{font-size:17px;margin:0 0 12px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
label{font-size:12px;font-weight:750;color:#555;display:block}
input,textarea,select{width:100%;margin-top:5px;border:1px solid #d2d2d2;border-radius:11px;padding:11px;font-size:16px;background:#fff}
textarea{min-height:80px}button{border:0;border-radius:12px;padding:12px 14px;font-weight:800;font-size:15px;background:#351c15;color:#fff}
.secondary{background:#eee;color:#222}.actions{display:flex;gap:8px;flex-wrap:wrap}
.status{padding:10px;border-radius:11px;background:#f1f1f1;margin-top:10px;font-size:13px}
.check{display:flex;align-items:center;gap:8px;margin:10px 0}.check input{width:auto;margin:0}
.big{font-size:19px;font-weight:900}.warn{background:#fff1d8}.ok{background:#eaf7ed}
.small{font-size:12px;color:#666}
</style></head><body>
<header><div class="wrap" style="padding:0"><div class="brand">UpsWorkPlace</div><div class="sub">La mia giornata</div></div></header>
<div class="wrap">

<div class="card"><div class="big">☀️ Giornata di oggi</div>
<div class="status"><b>Data:</b> <span id="today"></span><br><b>Stato:</b> <span id="saveState">salvataggio automatico</span></div></div>

<div class="card"><h2>👤 Driver e veicolo</h2><div class="grid">
<label>Driver<input id="driver" data-key="driver" placeholder="Nome e cognome"></label>
<label>Codice driver<input id="code" data-key="code" placeholder="Codice"></label>
<label>Targa<input id="plate" data-key="plate" placeholder="Targa"></label>
<label>Data<input id="date" data-key="date" type="date"></label>
</div></div>

<div class="card"><h2>🚚 Uscita dal magazzino</h2><div class="grid">
<label>Ora inizio lavoro<input id="start" data-key="startWork" type="time"></label>
<label>Ora uscita magazzino<input id="exit" data-key="warehouseExit" type="time"></label>
<label>Km uscita<input id="kmOut" data-key="kmOut" type="number"></label>
<label>Pacchi in consegna<input id="parcels" data-key="parcels" type="number"></label>
<label>Stop di consegna<input id="stops" data-key="stops" type="number"></label>
<label>Espressi in consegna<input id="express" data-key="express" type="number"></label>
</div>
<label class="check"><input id="finalized" data-key="itineraryFinalized" type="checkbox"> Itinerario finalizzato</label>
<div class="actions"><button onclick="recordNow('start')">⏱ Registra inizio</button><button onclick="warehouseExit()">🚚 Registra uscita</button></div>
<div id="exitMsg" class="status warn">L'uscita richiede la finalizzazione dell'itinerario.</div></div>

<div class="card"><h2>📍 Giro di consegna</h2>
<div class="actions"><button onclick="firstStop()">📍 Primo stop + GPS</button><button onclick="recordNow('last')">🏁 Ultimo stop</button></div>
<div class="grid" style="margin-top:10px">
<label>Ora primo stop<input id="first" data-key="firstStop" type="time"></label>
<label>Ora ultimo stop<input id="last" data-key="lastStop" type="time"></label>
</div><div id="gpsMsg" class="status">Il primo stop registra l'orario. Il collegamento GPS nativo verrà associato a questo evento nella fase fotocamera/GPS.</div></div>

<div class="card"><h2>⏸ Pause</h2>
<div class="actions"><button onclick="startPause()">▶ Inizio pausa</button><button class="secondary" onclick="endPause()">■ Fine pausa</button></div>
<div id="pauseMsg" class="status">Le pause vengono salvate come intervalli inizio/fine.</div></div>

<div class="card"><h2>📦 Ritiri e consegne aggiuntive</h2><div class="grid">
<label>Stop ritiri<input data-key="pickups" type="number"></label>
<label>Pacchi ritirati<input data-key="pickedParcels" type="number"></label>
<label>Stop aggiuntivi<input data-key="extraStops" type="number"></label>
<label>Pacchi aggiuntivi<input data-key="extraParcels" type="number"></label>
</div></div>

<div class="card"><h2>⚠️ Mancate consegne</h2>
<label>Note / motivazione<textarea data-key="failedDeliveries" placeholder="Evento, motivazione, destinatario, ecc."></textarea></label></div>

<div class="card"><h2>⛽ Rifornimenti</h2><div class="grid">
<label>Litri<input id="fuelLiters" data-key="fuelLiters" type="number" step="0.01"></label>
<label>Prezzo/litro €<input id="fuelPrice" data-key="fuelPrice" type="number" step="0.001"></label>
<label>Importo €<input id="fuelAmount" data-key="fuelAmount" type="number" step="0.01"></label>
<label>Km<input id="fuelKm" data-key="fuelKm" type="number"></label>
</div><button class="secondary" style="margin-top:10px">📷 Fotografa scontrino</button>
<div class="small">La foto verrà associata al rifornimento quando il modulo fotocamera sarà collegato.</div></div>

<div class="card"><h2>🛣️ Pedaggi</h2><div class="grid">
<label>Pedaggi<input data-key="tolls" type="number"></label>
<label>Mancati pedaggi<input data-key="missedTolls" type="number"></label>
</div></div>

<div class="card"><h2>🏁 Rientro</h2><div class="grid">
<label>Ora rientro<input id="return" data-key="returnTime" type="time"></label>
<label>Km rientro<input data-key="kmReturn" type="number"></label>
<label>Ora fine lavoro<input id="end" data-key="endWork" type="time"></label>
<label>Pulizia abitacolo<select data-key="cleaning"><option value="">Seleziona</option><option value="1">1</option><option value="2">2</option><option value="3">3</option><option value="4">4</option><option value="5">5</option></select></label>
</div><div class="actions" style="margin-top:10px">
<button onclick="recordNow('return')">🏁 Registra rientro</button><button onclick="recordNow('end')">⏱ Fine lavoro</button></div></div>

<div class="card"><h2>🔧 Guasti e danni</h2>
<textarea data-key="damages" placeholder="Descrizione del guasto/danno"></textarea>
<button class="secondary" style="margin-top:10px">📷 Foto danno</button></div>

<div class="card"><h2>📝 Note</h2><textarea data-key="notes" placeholder="Note della giornata"></textarea></div>
</div>
<script>
const pad=n=>String(n).padStart(2,'0');
const nowISO=()=>new Date().toISOString();
const timeString=()=>{const x=new Date();return pad(x.getHours())+':'+pad(x.getMinutes())};
const today=new Date();
document.getElementById('today').textContent=today.toLocaleDateString('it-IT');
document.getElementById('date').value=today.getFullYear()+'-'+pad(today.getMonth()+1)+'-'+pad(today.getDate());

function readFields(){
  const data={};
  document.querySelectorAll('[data-key]').forEach(el=>{
    data[el.dataset.key]=el.type==='checkbox'?el.checked:el.value;
  });
  data.updatedAt=nowISO();
  return data;
}
function send(){
  try{
    window.ReactNativeWebView.postMessage(JSON.stringify({type:'dayPatch',data:readFields()}));
    document.getElementById('saveState').textContent='✓ salvato automaticamente';
    document.getElementById('saveState').parentElement.className='status ok';
  }catch(e){}
}
document.querySelectorAll('[data-key]').forEach(el=>{
  el.addEventListener('input',send);
  el.addEventListener('change',send);
});
function recordNow(id){
  document.getElementById(id).value=timeString();
  send();
}
function warehouseExit(){
  const m=document.getElementById('exitMsg');
  if(!document.getElementById('finalized').checked){
    m.textContent='⚠️ Itinerario non finalizzato: spunta la casella prima di registrare l’uscita.';
    return;
  }
  document.getElementById('exit').value=timeString();
  m.textContent='✓ Uscita dal magazzino registrata.';
  m.className='status ok';
  send();
}
function firstStop(){
  document.getElementById('first').value=timeString();
  document.getElementById('gpsMsg').textContent='✓ Primo stop registrato. Evento salvato.';
  send();
}
let pauseStart=null;
function startPause(){
  pauseStart=new Date();
  document.getElementById('pauseMsg').textContent='▶ Inizio pausa: '+pauseStart.toLocaleTimeString('it-IT',{hour:'2-digit',minute:'2-digit'});
}
function endPause(){
  if(!pauseStart){document.getElementById('pauseMsg').textContent='⚠️ Nessuna pausa in corso.';return;}
  const e=new Date();
  const p=pauseStart.toLocaleTimeString('it-IT',{hour:'2-digit',minute:'2-digit'})+' → '+e.toLocaleTimeString('it-IT',{hour:'2-digit',minute:'2-digit'});
  document.getElementById('pauseMsg').textContent='✓ Pausa registrata: '+p;
  const hidden=readFields(); hidden.pauseInterval=p;
  try{window.ReactNativeWebView.postMessage(JSON.stringify({type:'pause',data:{start:pauseStart.toISOString(),end:e.toISOString()}}));}catch(_){}
  pauseStart=null;
}
</script></body></html>`;


function Placeholder({icon,title,description}) {
  return <ScrollView contentContainerStyle={styles.scroll}><View style={styles.placeholder}>
    <Text style={styles.icon}>{icon}</Text><Text style={styles.ptitle}>{title}</Text>
    <Text style={styles.ptext}>{description}</Text>
  </View></ScrollView>;
}

export default function App(){
  const [drawer,setDrawer]=useState(false), [section,setSection]=useState("home");
  const [days,setDays]=useState({}), [profile,setProfile]=useState(null), [loaded,setLoaded]=useState(false);
  const selected=useMemo(()=>MENU.find(x=>x[0]===section),[section]);

  useEffect(()=>{(async()=>{
    try{
      const [d,p]=await Promise.all([AsyncStorage.getItem(STORAGE_KEY),AsyncStorage.getItem(PROFILE_KEY)]);
      setDays(d?JSON.parse(d):{});
      setProfile(p?JSON.parse(p):null);
    }catch(e){Alert.alert("UpsWorkPlace","Impossibile leggere l'archivio locale.");}
    finally{setLoaded(true);}
  })()},[]);

  const saveToday=(patch)=>{
    const key=isoDate(), current=days[key]||blankDay();
    const normalized={...patch};
    if(normalized.itineraryFinalized!==undefined)
      normalized.itineraryFinalized=!!normalized.itineraryFinalized;
    const next={...current,...normalized,updatedAt:new Date().toISOString()};
    const all={...days,[key]:next};
    setDays(all);
    AsyncStorage.setItem(STORAGE_KEY,JSON.stringify(all)).catch(()=>{});
    if(next.driver || next.code || next.plate){
      const p={driver:next.driver||"",code:next.code||"",plate:next.plate||""};
      setProfile(p);
      AsyncStorage.setItem(PROFILE_KEY,JSON.stringify(p)).catch(()=>{});
    }
  };

  if(!loaded) return <SafeAreaView style={styles.safe}><View style={styles.loading}><Text>UpsWorkPlace</Text><Text>Caricamento archivio…</Text></View></SafeAreaView>;

  const descriptions={
    driver:"Profilo permanente del driver: dati, codice, foto del tesserino e targa associata.",
    history:"Archivio permanente delle giornate, con tutti i dati registrati e accesso al Ruolino.",
    hours:"Riepilogo mensile delle ore lavorate, straordinari e differenze.",
    leave:"Inserimento e storico di ferie, permessi, malattia e altre assenze.",
    vehicle:"Dati del mezzo, chilometri, consumi, rifornimenti, manutenzioni e controlli fotografici.",
    damage:"Storico dei guasti e dei danni associati al veicolo.",
    fuel:"Rifornimenti, prezzi/litro, scontrini, pedaggi e mancati pedaggi.",
    ruolino:"Archivio dei Ruolini: un elemento per ogni giornata, con data e condivisione del documento.",
    payslips:"Archivio delle buste paga, confronto con i dati di UpsWorkPlace e segnalazione delle differenze da verificare.",
    stats:"Statistiche personali giornaliere, settimanali e mensili.",
    settings:"Impostazioni dell'app e gestione dei dati locali."
  };

  return <SafeAreaView style={styles.safe}>
    <StatusBar barStyle="light-content"/>
    <View style={styles.top}><TouchableOpacity style={styles.menuBtn} onPress={()=>setDrawer(true)}><Text style={styles.menu}>☰</Text></TouchableOpacity><Text style={styles.topTitle}>UpsWorkPlace</Text></View>
    {section==="home"
      ? <WebView originWhitelist={["*"]} source={{html:DAY_HTML}} style={styles.web}
          onMessage={e=>{try{
            const m=JSON.parse(e.nativeEvent.data);
            if(m.type==="dayPatch") saveToday(m.data);
            if(m.type==="pause"){
              const key=isoDate(), current=days[key]||blankDay();
              const pauses=[...(current.pauses||[]),m.data];
              saveToday({pauses});
            }
          }catch(_){}}}/>
      : <Placeholder icon={selected[1]} title={selected[2]} description={descriptions[section]}/>}
    {drawer && <View style={styles.overlay}><TouchableOpacity style={styles.backdrop} onPress={()=>setDrawer(false)}/><View style={styles.drawer}>
      <View style={styles.drawerHead}><Text style={styles.drawerTitle}>UpsWorkPlace</Text><Text style={styles.drawerSub}>{profile?.driver||"Gestione del lavoro del driver"}</Text></View>
      <ScrollView>{MENU.map(([key,icon,label])=><TouchableOpacity key={key} style={[styles.item,section===key&&styles.active]} onPress={()=>{setSection(key);setDrawer(false)}}>
        <Text style={styles.itemIcon}>{icon}</Text><Text style={styles.itemText}>{label}</Text></TouchableOpacity>)}</ScrollView>
    </View></View>}
  </SafeAreaView>;
}

const styles=StyleSheet.create({
 safe:{flex:1,backgroundColor:"#f4f4f4"},top:{height:58,backgroundColor:"#351c15",flexDirection:"row",alignItems:"center",paddingHorizontal:8},
 menuBtn:{width:48,height:48,alignItems:"center",justifyContent:"center"},menu:{fontSize:29,color:"#fff"},topTitle:{fontSize:20,fontWeight:"900",color:"#fff"},web:{flex:1},
 scroll:{padding:20,flexGrow:1},placeholder:{backgroundColor:"#fff",borderRadius:18,padding:25,alignItems:"center",marginTop:20},
 icon:{fontSize:48},ptitle:{fontSize:24,fontWeight:"900",marginTop:10},ptext:{fontSize:15,color:"#666",textAlign:"center",marginTop:12,lineHeight:22},
 loading:{flex:1,alignItems:"center",justifyContent:"center",gap:8},overlay:{...StyleSheet.absoluteFillObject,flexDirection:"row",zIndex:50},
 backdrop:{flex:1,backgroundColor:"rgba(0,0,0,0.45)"},drawer:{width:"82%",maxWidth:360,backgroundColor:"#fff",elevation:20},
 drawerHead:{backgroundColor:"#351c15",paddingTop:38,paddingBottom:22,paddingHorizontal:20},drawerTitle:{color:"#fff",fontSize:24,fontWeight:"900"},
 drawerSub:{color:"#fff",opacity:.75,marginTop:4},item:{flexDirection:"row",alignItems:"center",paddingVertical:14,paddingHorizontal:18,borderBottomWidth:1,borderBottomColor:"#eee"},
 active:{backgroundColor:"#f1e9e5"},itemIcon:{fontSize:22,width:38},itemText:{fontSize:16,fontWeight:"650",color:"#222"}
});
