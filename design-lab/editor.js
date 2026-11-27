"use strict";

// Browser-only visual lab. The Android renderer remains the source of truth.
// Coordinates use the 540 × 1170 reference screenshot as their design space.
const W = 540, H = 1170, STORE = "xmb-design-lab-v1";
const ASSET = "../app/src/main/res/drawable/ic_xmb_";
const ICONS = {
 music:"category_music",video:"category_video",settings:"category_settings",
 songs:"songs",albums:"albums",artists:"artists",now:"now_playing",
 local:"local_videos",folders:"folders",appearance:"appearance",
 background:"animated_background",performance:"performance",information:"information",
 play:"play",pause:"pause",previous:"previous",next:"next",
 rewind:"rewind",forward:"fast_forward",back:"back",applied:"applied",
 access:"access",retry:"retry"
};
const ICON_CACHE = new Map();
const ROOT_CATEGORIES = [
 {label:"Música", icon:"music"}, {label:"Video",icon:"video"}, {label:"Ajustes",icon:"settings"}
];
const SCREENS = {
 music:{label:"Inicio · Música",photo:"musica-azul.webp",category:0,rows:[["Canciones","songs"],["Álbumes","albums"],["Artistas","artists"],["En reproducción","now"]],selected:0},
 video:{label:"Inicio · Video",photo:"video.webp",category:1,rows:[["Videos locales","local"],["Carpetas","folders"]],selected:0},
 settings:{label:"Inicio · Ajustes",photo:"ajustes.webp",category:2,rows:[["Apariencia","appearance"],["Información","information"]],selected:0},
 songs:{label:"Biblioteca · Canciones",photo:"canciones.webp",title:"Canciones",rows:[["XMB QA 01","songs"],["XMB QA 02","songs"],["XMB QA 03","songs"],["XMB QA 04","songs"],["XMB QA 05","songs"],["XMB QA 06","songs"],["XMB QA 07","songs"],["XMB QA 08","songs"]],selected:2},
 albums:{label:"Biblioteca · Álbumes",photo:"albumes.webp",title:"Álbumes",rows:[["XMB QA Álbum","albums"],["Otro álbum","albums"],["Colección","albums"],["Directo","albums"],["Archivo","albums"]],selected:2},
 artists:{label:"Biblioteca · Artistas",photo:"artistas.webp",title:"Artistas",rows:[["XMB QA Artista","artists"],["Otro artista","artists"],["Varios artistas","artists"]],selected:0},
 group:{label:"Biblioteca · Dentro de álbum",photo:"tarjeta-en-biblioteca.webp",title:"XMB QA Álbum · XMB QA Artista",rows:[["XMB QA 01","songs"],["XMB QA 02","songs"]],selected:0},
 appearance:{label:"Ajustes · Apariencia",photo:"apariencia.webp",title:"Apariencia",rows:[["Fondo animado","background"],["Rendimiento","performance"]],selected:0},
 effects:{label:"Ajustes · Fondo animado",photo:"selector-de-fondo.webp",title:"Fondo animado",rows:[["Ondas azules","background"],["Onda violeta","background"]],selected:0,check:0},
 motion:{label:"Ajustes · Rendimiento",photo:"rendimiento-ahorro.webp",title:"Rendimiento",rows:[["Fluido","performance"],["Ahorro · aprox. 30 FPS","performance"],["Estático","performance"]],selected:1,check:1},
 info:{label:"Ajustes · Información",photo:"informacion.webp",title:"Información",rows:[["XMB Lite Player · Versión 0.2","information"]],selected:0},
 player:{label:"Reproducción · Activa",photo:"reproduciendo.webp",title:"En reproducción",selected:0},
 permission:{label:"Estados · Sin permiso",photo:"acceso-a-musica.webp",title:"Canciones",rows:[["Permitir acceso a música","access"]],selected:0,message:"Acceso necesario para ver tu música"},
 error:{label:"Estados · Archivo incompatible",photo:"archivo-incompatible.webp",title:"En reproducción",selected:0}
};
const DEFAULTS = {
 focus:.19,rowGap:86,
 layouts:Object.fromEntries(Object.keys(SCREENS).map(key=>[key,{
  categories:{x:100,y:322,scale:1},heading:{x:135,y:300,scale:1},
  back:{x:28,y:55,scale:1},rows:{x:83,y:key==="songs"||key==="albums"||key==="artists"?319:490,scale:1},
  card:{x:20,y:key==="player"||key==="error"?347:89,scale:1}
 }]))
};
let model = deepCopy(DEFAULTS);
let currentScreen = "music", theme = "blue", playing = false, selected = "rows";
let undoStack = [], dragging = null, viewportScale = 1;
const $ = id => document.getElementById(id);
const deepCopy = value => JSON.parse(JSON.stringify(value));
const clamp = (v,min,max) => Math.min(max,Math.max(min,v));
function remember(){undoStack.push(deepCopy(model));if(undoStack.length>60)undoStack.shift();}
function persist(){try{localStorage.setItem(STORE,JSON.stringify(model));}catch{}}
function validNumber(n,min,max,fallback){return Number.isFinite(n)?clamp(n,min,max):fallback;}
function validate(raw){
 if(!raw||typeof raw!=="object")throw Error("Archivo inválido");
 const candidate=raw.layout||raw;
 const clean=deepCopy(DEFAULTS);
 clean.focus=validNumber(Number(candidate.focus),.06,.45,DEFAULTS.focus);
 clean.rowGap=validNumber(Number(candidate.rowGap),55,140,DEFAULTS.rowGap);
 for(const key of Object.keys(SCREENS)){
  const input=candidate.layouts?.[key];
  if(!input)continue;
  for(const el of Object.keys(clean.layouts[key])){
   const next=input[el]||{};
   const prior=clean.layouts[key][el];
   prior.x=validNumber(Number(next.x),-180,680,prior.x);
   prior.y=validNumber(Number(next.y),-100,1250,prior.y);
   prior.scale=validNumber(Number(next.scale),.6,1.6,prior.scale);
  }
 }
 return clean;
}
try {const stored=localStorage.getItem(STORE);if(stored)model=validate(JSON.parse(stored));}catch{}

const screenControl=$("screen");
for(const [id,item] of Object.entries(SCREENS)){
 const opt=document.createElement("option");opt.value=id;opt.textContent=item.label;screenControl.append(opt);
}
function sourceImage(){
 if(currentScreen==="music"&&theme==="violet"&&!playing)return "musica-violeta.webp";
 if(playing){
  if(currentScreen==="music")return "tarjeta-en-inicio.webp";
  if(currentScreen==="appearance"||currentScreen==="settings")return "tarjeta-en-ajustes.webp";
  if(currentScreen==="group"||currentScreen==="songs"||currentScreen==="albums"||currentScreen==="artists")return "tarjeta-en-biblioteca.webp";
 }
 return SCREENS[currentScreen].photo;
}
function iconSVG(key){
 const file=ICONS[key];
 if(!file)return "";
 if(!ICON_CACHE.has(key)){
  const fallback='<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8" stroke="currentColor" fill="none" stroke-width="2"/></svg>';
  ICON_CACHE.set(key,fallback);
  fetch(ASSET+file+".xml").then(r=>{if(!r.ok)throw Error("Icon missing");return r.text();})
   .then(xml=>{
    const doc=new DOMParser().parseFromString(xml,"application/xml");
    if(doc.querySelector("parsererror"))throw Error("Invalid XML");
    const android="http://schemas.android.com/apk/res/android";
    const out=['<svg viewBox="0 0 24 24" aria-hidden="true">'];
    for(const p of doc.querySelectorAll("path")){
     const d=p.getAttributeNS(android,"pathData");
     if(!d)continue;
     const stroke=p.getAttributeNS(android,"strokeColor"),fill=p.getAttributeNS(android,"fillColor");
     const width=p.getAttributeNS(android,"strokeWidth")||"1.85";
     const linecap=p.getAttributeNS(android,"strokeLineCap")||"round";
     const linejoin=p.getAttributeNS(android,"strokeLineJoin")||"round";
     // Trusted source files inside this same repository; attribute contents escaped.
     const safe=s=>String(s).replaceAll("&","&amp;").replaceAll('"',"&quot;").replaceAll("<","&lt;");
     out.push('<path d="'+safe(d)+'" fill="'+(fill?"currentColor":"none")+'" stroke="'+(stroke?"currentColor":"none")+'" stroke-width="'+safe(width)+'" stroke-linecap="'+safe(linecap)+'" stroke-linejoin="'+safe(linejoin)+'"/>');
    }
    out.push("</svg>");ICON_CACHE.set(key,out.join(""));renderStage();
   }).catch(()=>{});
 }
 return ICON_CACHE.get(key);
}
const glyph=key=>'<span class="glyph">'+iconSVG(key)+'</span>';
function sections(){
 const entry=SCREENS[currentScreen];
 if(["music","video","settings"].includes(currentScreen))return ["categories","rows",...(playing?["card"]:[])];
 if(currentScreen==="player"||currentScreen==="error")return ["back","heading","card"];
 return ["back","heading","rows",...(playing?["card"]:[])];
}
function layerName(name){return ({categories:"Categorías",heading:"Título",back:"Atrás",rows:"Lista",card:"Reproductor"})[name];}
function renderLayers(){
 $("layers").replaceChildren();
 for(const name of sections()){
  const btn=document.createElement("button");
  btn.type="button";btn.className="layer"+(selected===name?" active":"");
  btn.textContent=layerName(name);
  btn.addEventListener("click",()=>{selected=name;renderLayers();renderStage();syncControls();});
  $("layers").append(btn);
 }
}
function comp(name,html,klass=""){
 const cfg=model.layouts[currentScreen][name];
 const el=document.createElement("div");
 el.className="component "+klass+(selected===name?" selected":"");
 el.dataset.layer=name;
 el.style.left=cfg.x+"px";el.style.top=cfg.y+"px";el.style.transform="scale("+cfg.scale+")";
 el.innerHTML=html;
 el.addEventListener("pointerdown",startDrag);
 return el;
}
function renderStage(){
 const stage=$("stage");stage.replaceChildren();
 const page=SCREENS[currentScreen];const root=["music","video","settings"].includes(currentScreen);
 $("preview").classList.toggle("violet",theme==="violet");
 $("preview").style.setProperty("--focus-alpha",model.focus);
 if(root){
  stage.append(comp("categories",'<div class="categories">'+ROOT_CATEGORIES.map((c,i)=>
   '<div class="category '+(page.category===i?'chosen':'')+'">'+glyph(c.icon)+'<span>'+c.label+'</span></div>').join("")+"</div>"));
 } else {
  stage.append(comp("back",'<div class="back">'+glyph("back")+'<span>Atrás</span></div>'));
  stage.append(comp("heading",'<div class="heading">'+escapeHTML(page.title)+"</div>"));
 }
 if(page.rows){
  const rowHtml=page.rows.map(([label,ic],i)=>'<div class="screen-row '+(i===page.selected?'active':'')+'" style="height:'+model.rowGap+'px">'+
   glyph(ic)+'<strong>'+escapeHTML(label)+'</strong>'+(page.check===i?'<span class="check">✓</span>':'')+'</div>').join("");
  stage.append(comp("rows",'<div class="rows">'+rowHtml+'</div>'));
  if(page.message){
   // Annotation for context; deliberately not a movable/layout-exported element.
   const note=document.createElement("div");note.className="info";
   note.style.cssText="position:absolute;left:135px;top:415px";
   note.textContent=page.message;stage.append(note);
  }
 }
 if(playing||currentScreen==="player"||currentScreen==="error"){
  const noSong=currentScreen==="error";
  const card='<div class="player-card"><div class="track">'+(noSong?"XMB-Gallery-Corrupt":"XMB QA 01")+'</div><div class="artist">'+(noSong?"Archivo inaccesible o incompatible":"XMB QA Artista")+'</div>'+
   '<div class="progress-line"><span>0:13</span><div class="rail"></div><span>2:00</span></div>'+
   '<div class="transport">'+glyph("rewind")+glyph("previous")+glyph("pause")+glyph("next")+glyph("forward")+'</div></div>';
  stage.append(comp("card",card));
 }
}
function escapeHTML(s){return String(s).replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;");}
function renderReference(){$("reference").src="../docs/capturas-ui/"+sourceImage();}
function syncControls(){
 if(!sections().includes(selected))selected=sections()[0];
 const v=model.layouts[currentScreen][selected];
 const sliders={x:v.x,y:v.y,scale:v.scale,rowGap:model.rowGap,focus:model.focus};
 for(const [name,val] of Object.entries(sliders)){$(name).value=val;$(name+"Out").textContent=name==="scale"?val.toFixed(2)+"×":name==="focus"?Math.round(val*100)+"%":Math.round(val)+(name==="rowGap"?" px":"");}
}
function draw(){renderReference();renderLayers();renderStage();syncControls();}
function fitPreview(){
 const viewport=$("preview-viewport");
 viewportScale=viewport.clientWidth/W;
 $("preview").style.transform="scale("+viewportScale+")";
}
function startDrag(event){
 if(event.button!==0)return;
 const name=event.currentTarget.dataset.layer;
 selected=name;renderLayers();syncControls();
 document.querySelectorAll(".component").forEach(el=>el.classList.toggle("selected",el.dataset.layer===name));
 const cfg=model.layouts[currentScreen][name];
 remember();
 dragging={name,x:event.clientX,y:event.clientY,startX:cfg.x,startY:cfg.y};
 event.currentTarget.setPointerCapture(event.pointerId);
 event.currentTarget.addEventListener("pointermove",moveDrag);
 event.currentTarget.addEventListener("pointerup",endDrag,{once:true});
 event.currentTarget.addEventListener("pointercancel",endDrag,{once:true});
}
function moveDrag(event){
 if(!dragging)return;
 const cfg=model.layouts[currentScreen][dragging.name];
 cfg.x=clamp(Math.round(dragging.startX+(event.clientX-dragging.x)/viewportScale),-180,680);
 cfg.y=clamp(Math.round(dragging.startY+(event.clientY-dragging.y)/viewportScale),-100,1250);
 const node=event.currentTarget;
 node.style.left=cfg.x+"px";node.style.top=cfg.y+"px";
 syncControls();
}
function endDrag(event){
 event.currentTarget.removeEventListener("pointermove",moveDrag);
 dragging=null;persist();renderStage();
}
screenControl.addEventListener("change",e=>{currentScreen=e.target.value;selected="rows";draw();});
$("theme").addEventListener("change",e=>{theme=e.target.value;renderReference();renderStage();});
$("playing").addEventListener("change",e=>{playing=e.target.value==="on";draw();});
for(const name of ["x","y","scale","rowGap","focus"]){
 $(name).addEventListener("pointerdown",()=>remember());
 $(name).addEventListener("change",()=>persist());
 $(name).addEventListener("input",e=>{
  const value=Number(e.target.value);
  if(name==="focus"||name==="rowGap")model[name]=value;
  else model.layouts[currentScreen][selected][name]=value;
  renderStage();syncControls();persist();
 });
}
$("undo").addEventListener("click",()=>{if(!undoStack.length)return;model=undoStack.pop();persist();draw();});
$("reset").addEventListener("click",()=>{if(!confirm("¿Restablecer todos los ajustes del laboratorio?"))return;remember();model=deepCopy(DEFAULTS);persist();draw();});
$("export").addEventListener("click",()=>{
 const payload={format:"xmb-lite-design-lab",version:1,reference:{width:W,height:H},notes:"Referencia visual: no aplicar automáticamente en Kotlin sin validar dimensiones/dp y recortes.",layout:model,normalized:Object.fromEntries(Object.entries(model.layouts).map(([key,positions])=>[key,Object.fromEntries(Object.entries(positions).map(([name,v])=>[name,{x:+(v.x/W).toFixed(5),y:+(v.y/H).toFixed(5),scale:v.scale}]))]))};
 const data=new Blob([JSON.stringify(payload,null,2)],{type:"application/json"});
 const url=URL.createObjectURL(data);const a=document.createElement("a");
 a.href=url;a.download="xmb-layout-v1.json";a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
});
$("import").addEventListener("click",()=>$("file").click());
$("file").addEventListener("change",async e=>{
 const file=e.target.files[0];if(!file)return;
 try{if(file.size>200000)throw Error("JSON demasiado grande");const read=JSON.parse(await file.text());remember();model=validate(read);persist();draw();}
 catch(err){alert("No se pudo importar: "+err.message);}
 e.target.value="";
});
new ResizeObserver(fitPreview).observe($("preview-viewport"));
draw();fitPreview();
