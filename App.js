import React, { useState } from "react";
import { SafeAreaView, StatusBar, StyleSheet, Text, TouchableOpacity, View, ScrollView } from "react-native";
import { WebView } from "react-native-webview";

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

const DAY_HTML = `<!doctype html><html lang="it"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>UpsWorkPlace</title>
<style>
:root{font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#171717;background:#f4f4f4}
*{box-sizing:border-box}body{margin:0}.wrap{max-width:780px;margin:auto;padding:12px}
header{background:#351c15;color:#fff;padding:14px}.brand{font-size:21px;font-weight:900}
.card{background:#fff;border-radius:16px;padding:15px;margin:12px 0;box-shadow:0 1px 6px #0001}
h2{font-size:17px;margin:0 0 12px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
label{font-size:12px;font-weight:700;color:#555;display:block}
input,select,textarea{width:100%;margin-top:5px;border:1px solid #d2d2d2;border-radius:11px;padding:11px;font-size:16px;background:#fff}
textarea{min-height:80px}button{border:0;border-radius:12px;padding:12px 14px;font-weight:750;font-size:15px;background:#351c15;color:#fff}
.secondary{background:#eee;color:#222}.ok{background:#e7f5ea;color:#155b28}.danger{background:#ffe7e7;color:#8a1010}
.actions{display:flex;gap:8px;flex-wrap:wrap}.status{padding:10px;border-radius:11px;background:#f1f1f1;margin-top:10px;font-size:13px}
.check{display:flex;align-items:center;gap:8px;margin:10px 0}.check input{width:auto;margin:0}
.sectionTitle{font-size:19px;font-weight:900}
@media(max-width:520px){.grid{grid-template-columns:1fr 1fr}}
</style></head><body>
<header><div class="wrap" style="padding:0"><div class="brand">UpsWorkPlace</div><div style="opacity:.75;font-size:12px">La mia giornata</div></div></header>
<div class="wrap">

<div class="card"><div class="sectionTitle">☀️ Giornata di oggi</div>
<div class="status"><b>Data:</b> <span id="today"></span><br><b>Stato:</b> giornata in corso</div></div>

<div class="card"><h2>👤 Dati driver e mezzo</h2>
<div class="grid">
<label>Driver<input id="driver"></label>
<label>Codice driver<input id="code"></label>
<label>Targa<input id="plate"></label>
<label>Data<input id="date" type="date"></label>
</div></div>

<div class="card"><h2>🚚 Partenza dal magazzino</h2>
<div class="grid">
<label>Ora inizio lavoro<input id="start" type="time"></label>
<label>Ora uscita magazzino<input id="exit" type="time"></label>
<label>Km uscita<input type="number"></label>
<label>Pacchi in consegna<input type="number"></label>
<label>Stop di consegna<input type="number"></label>
<label>Espressi in consegna<input type="number"></label>
</div>
<label class="check"><input id="finalized" type="checkbox"> Itinerario finalizzato</label>
<div class="actions">
<button onclick="now('start')">⏱ Registra inizio</button>
<button onclick="warehouseExit()">🚚 Registra uscita</button>
</div>
<div id="exitMsg" class="status">Prima dell'uscita finalizza l'itinerario.</div>
</div>

<div class="card"><h2>📍 Durante il giro</h2>
<div class="actions">
<button onclick="now('first')">📍 Registra primo stop</button>
<button onclick="now('last')">🏁 Registra ultimo stop</button>
</div>
<div class="grid" style="margin-top:10px">
<label>Primo stop<input id="first" type="time"></label>
<label>Ultimo stop<input id="last" type="time"></label>
</div>
<div class="status">Il primo stop dovrà registrare automaticamente anche la posizione GPS.</div>
</div>

<div class="card"><h2>⏸ Pause</h2>
<div class="actions"><button onclick="startPause()">▶ Inizio pausa</button><button class="secondary" onclick="endPause()">■ Fine pausa</button></div>
<div id="pauseMsg" class="status">Le pause verranno conservate con ora di inizio e ora di fine.</div>
</div>

<div class="card"><h2>📦 Ritiri e consegne aggiuntive</h2>
<div class="grid">
<label>Stop ritiri<input type="number"></label><label>Pacchi ritirati<input type="number"></label>
<label>Stop aggiuntivi<input type="number"></label><label>Pacchi aggiuntivi<input type="number"></label>
</div>
<p class="status">Gli espressi restano separati dal totale pacchi. Le consegne aggiuntive e i ritiri hanno contatori distinti.</p>
</div>

<div class="card"><h2>⚠️ Mancate consegne</h2><textarea placeholder="Registra gli eventi e le motivazioni"></textarea></div>

<div class="card"><h2>⛽ Rifornimenti</h2>
<div class="grid">
<label>Litri<input type="number" step="0.01"></label><label>Prezzo/litro €<input type="number" step="0.001"></label>
<label>Importo €<input type="number" step="0.01"></label><label>Km<input type="number"></label>
</div>
<button class="secondary" style="margin-top:10px">📷 Foto scontrino</button>
</div>

<div class="card"><h2>🛣️ Pedaggi</h2>
<div class="grid"><label>Pedaggi<input type="number"></label><label>Mancati pedaggi<input type="number"></label></div>
</div>

<div class="card"><h2>🏁 Rientro e fine giornata</h2>
<div class="grid">
<label>Ora rientro<input id="return" type="time"></label><label>Km rientro<input type="number"></label>
<label>Ora fine lavoro<input id="end" type="time"></label><label>Pulizia abitacolo<input type="number" min="1" max="5"></label>
</div>
<div class="actions" style="margin-top:10px">
<button onclick="now('return')">🏁 Registra rientro</button><button onclick="now('end')">⏱ Registra fine lavoro</button>
</div>
</div>

<div class="card"><h2>🔧 Guasti e danni</h2><textarea placeholder="Descrizione guasti / danni"></textarea>
<p class="status">📷 Foto danni e guasti: la fotocamera sarà collegata nella versione funzionale completa.</p></div>

</div>
<script>
const d=new Date(), pad=n=>String(n).padStart(2,'0');
document.getElementById('today').textContent=d.toLocaleDateString('it-IT');
document.getElementById('date').value=d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate());
function now(id){const x=new Date();document.getElementById(id).value=pad(x.getHours())+':'+pad(x.getMinutes());}
function warehouseExit(){
 const msg=document.getElementById('exitMsg');
 if(!document.getElementById('finalized').checked){msg.textContent='⚠️ Itinerario non finalizzato. Spunta la casella prima di registrare l’uscita.';return;}
 now('exit');msg.textContent='✓ Uscita dal magazzino registrata.';
}
let ps=null;
function startPause(){ps=new Date();document.getElementById('pauseMsg').textContent='▶ Pausa iniziata alle '+ps.toLocaleTimeString('it-IT',{hour:'2-digit',minute:'2-digit'});}
function endPause(){if(!ps){document.getElementById('pauseMsg').textContent='⚠️ Nessuna pausa in corso.';return;}const pe=new Date();document.getElementById('pauseMsg').textContent='✓ Pausa: '+ps.toLocaleTimeString('it-IT',{hour:'2-digit',minute:'2-digit'})+' → '+pe.toLocaleTimeString('it-IT',{hour:'2-digit',minute:'2-digit'});ps=null;}
</script></body></html>`;

function Placeholder({ icon, title }) {
  return <ScrollView contentContainerStyle={styles.scroll}>
    <View style={styles.placeholder}>
      <Text style={styles.icon}>{icon}</Text>
      <Text style={styles.placeholderTitle}>{title}</Text>
      <Text style={styles.placeholderText}>
        Sezione già prevista nell'architettura di UpsWorkPlace. Verrà collegata ai dati
        permanenti della giornata e allo storico.
      </Text>
    </View>
  </ScrollView>;
}

export default function App() {
  const [drawer,setDrawer]=useState(false);
  const [section,setSection]=useState("home");
  const selected=MENU.find(x=>x[0]===section);

  return <SafeAreaView style={styles.safe}>
    <StatusBar barStyle="light-content" />
    <View style={styles.top}>
      <TouchableOpacity style={styles.menuBtn} onPress={()=>setDrawer(true)}>
        <Text style={styles.menu}>☰</Text>
      </TouchableOpacity>
      <Text style={styles.topTitle}>UpsWorkPlace</Text>
    </View>

    {section==="home"
      ? <WebView originWhitelist={["*"]} source={{html:DAY_HTML}} style={styles.web}/>
      : <Placeholder icon={selected[1]} title={selected[2]}/>}

    {drawer && <View style={styles.overlay}>
      <TouchableOpacity style={styles.backdrop} onPress={()=>setDrawer(false)}/>
      <View style={styles.drawer}>
        <View style={styles.drawerHead}>
          <Text style={styles.drawerTitle}>UpsWorkPlace</Text>
          <Text style={styles.drawerSub}>Gestione del lavoro del driver</Text>
        </View>
        <ScrollView>
          {MENU.map(([key,icon,label]) =>
            <TouchableOpacity key={key}
              style={[styles.item, section===key && styles.active]}
              onPress={()=>{setSection(key);setDrawer(false);}}>
              <Text style={styles.itemIcon}>{icon}</Text>
              <Text style={styles.itemText}>{label}</Text>
            </TouchableOpacity>
          )}
        </ScrollView>
      </View>
    </View>}
  </SafeAreaView>;
}

const styles=StyleSheet.create({
 safe:{flex:1,backgroundColor:"#f4f4f4"},
 top:{height:58,backgroundColor:"#351c15",flexDirection:"row",alignItems:"center",paddingHorizontal:8},
 menuBtn:{width:48,height:48,alignItems:"center",justifyContent:"center"},
 menu:{fontSize:29,color:"#fff"},topTitle:{fontSize:20,fontWeight:"900",color:"#fff"},
 web:{flex:1},scroll:{padding:20,flexGrow:1},
 placeholder:{backgroundColor:"#fff",borderRadius:18,padding:25,alignItems:"center",marginTop:20},
 icon:{fontSize:48},placeholderTitle:{fontSize:24,fontWeight:"900",marginTop:10},
 placeholderText:{fontSize:15,color:"#666",textAlign:"center",marginTop:12,lineHeight:22},
 overlay:{...StyleSheet.absoluteFillObject,flexDirection:"row",zIndex:50},
 backdrop:{flex:1,backgroundColor:"rgba(0,0,0,0.45)"},
 drawer:{width:"82%",maxWidth:360,backgroundColor:"#fff",elevation:20},
 drawerHead:{backgroundColor:"#351c15",paddingTop:38,paddingBottom:22,paddingHorizontal:20},
 drawerTitle:{color:"#fff",fontSize:24,fontWeight:"900"},
 drawerSub:{color:"#fff",opacity:.75,marginTop:4},
 item:{flexDirection:"row",alignItems:"center",paddingVertical:14,paddingHorizontal:18,borderBottomWidth:1,borderBottomColor:"#eee"},
 active:{backgroundColor:"#f1e9e5"},itemIcon:{fontSize:22,width:38},itemText:{fontSize:16,fontWeight:"650",color:"#222"}
});
