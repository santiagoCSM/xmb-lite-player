"use strict";
// The LLM edits DEFAULTS and SCREENS in source control; visitors review, not configure.
const W = 540, H = 1170;
const $ = id => document.getElementById(id);
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
  rows:{x:83,y:["songs","albums","artists","group","appearance","effects","motion","permission"].includes(key)?475:490,scale:1},
  card:{x:20,y:key==="player"||key==="error"?347:89,scale:1}
 }]))
};

const model=structuredClone(DEFAULTS);
let currentScreen="music",theme="blue",playing=false;
let selectedRow=0;
let navigationHistory=[];
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
 if(currentScreen==="player"||currentScreen==="error")return ["heading","card"];
 return ["heading","rows",...(playing?["card"]:[])];
}
function layerName(name){return ({categories:"Categorías",heading:"Título",back:"Atrás",rows:"Lista",card:"Reproductor"})[name];}

function comp(name,html,klass=""){
 const cfg=model.layouts[currentScreen][name];
 const el=document.createElement("div");
 el.className="component "+klass;
 el.dataset.layer=name;
 el.style.left=cfg.x+"px";el.style.top=cfg.y+"px";el.style.transform="scale("+cfg.scale+")";
 el.innerHTML=html;
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
  stage.append(comp("heading",'<div class="heading">'+escapeHTML(page.title)+"</div>"));
 }
 if(page.rows){
  const rowHtml=page.rows.map(([label,ic],i)=>'<div class="screen-row '+(i===selectedRow?'active':'')+'" style="height:'+model.rowGap+'px">'+
   glyph(ic)+'<strong>'+escapeHTML(label)+'</strong>'+(page.check===i?'<span class="check">✓</span>':'')+'</div>').join("");
  stage.append(comp("rows",'<div class="rows">'+rowHtml+'</div>'));
  if(page.message){
   // Annotation for context; deliberately not a movable/layout-exported element.
   const note=document.createElement("div");note.className="info";
   note.style.cssText="position:absolute;left:135px;top:415px";
   note.textContent=page.message;stage.append(note);
  }
 }
 if(showPlaybackCard()){
  const noSong=currentScreen==="error";
  const card='<div class="player-card"><div class="track">'+(noSong?"XMB-Gallery-Corrupt":"XMB QA 01")+'</div><div class="artist">'+(noSong?"Archivo inaccesible o incompatible":"XMB QA Artista")+'</div>'+
   '<div class="progress-line"><span>0:13</span><div class="rail"></div><span>2:00</span></div>'+
   '<div class="transport">'+glyph("rewind")+glyph("previous")+glyph("pause")+glyph("next")+glyph("forward")+'</div></div>';
  stage.append(comp("card",card));
 }
}
function escapeHTML(s){return String(s).replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;");}

// Captures remain the reference, even when the web concept displays an
// unrecorded combination of theme and mini-player. No screenshot is fabricated.
const CAPTURES = {
 music: {base:"musica-azul.webp", blue:{off:"musica-azul.webp",on:"tarjeta-en-inicio.webp"},violet:{off:"musica-violeta.webp"}},
 video: {base:"video.webp"},
 settings: {base:"ajustes.webp",blue:{on:"tarjeta-en-ajustes.webp"}},
 songs: {base:"canciones.webp"},
 albums: {base:"albumes.webp"},
 artists: {base:"artistas.webp"},
 group: {base:"canciones-del-album.webp",blue:{on:"tarjeta-en-biblioteca.webp"}},
 appearance: {base:"apariencia.webp"},
 effects: {base:"selector-de-fondo.webp"},
 motion: {base:"rendimiento-ahorro.webp"},
 info: {base:"informacion.webp"},
 player: {base:"reproduciendo.webp"},
 permission: {base:"acceso-a-musica.webp"},
 error: {base:"archivo-incompatible.webp"}
};
function showPlaybackCard(){
 return playing || currentScreen==="player" || currentScreen==="error";
}
function sourceImage(){
 const entry=CAPTURES[currentScreen];
 const state=showPlaybackCard()?"on":"off";
 const baseState=(currentScreen==="player"||currentScreen==="error")?"on":"off";
 const exact=entry[theme]?.[state] || (theme==="blue" && state===baseState ? entry.base : undefined);
 return {file:exact||entry.base, exact:Boolean(exact)};
}
function refresh(){
 const page=SCREENS[currentScreen];
 $("page-name").textContent=page.label;
 const image=sourceImage();
 const reference=$("reference");
 const note=$("reference-note");
 reference.src="../docs/capturas-ui/"+image.file;
 reference.alt=(image.exact?"Captura exacta":"Captura base de referencia")+" del Redmi 9T: "+page.label;
 note.hidden=image.exact;
 note.textContent=image.exact?"":"La captura original corresponde al estado base. Los cambios de fondo y tarjeta se muestran en la propuesta web.";
 renderStage();
 document.querySelectorAll("[data-screen]").forEach(el=>{
  const active=el.dataset.screen===currentScreen;
  el.classList.toggle("active",active);
  if(active)el.setAttribute("aria-current","page");else el.removeAttribute("aria-current");
 });
 document.querySelectorAll("[data-theme]").forEach(el=>{
  const active=el.dataset.theme===theme;
  el.classList.toggle("active",active);
  el.setAttribute("aria-pressed",String(active));
 });
 document.querySelectorAll("[data-playing]").forEach(el=>{
  const locked=currentScreen==="player"||currentScreen==="error";
  const active=(el.dataset.playing==="on")===showPlaybackCard();
  el.classList.toggle("active",active);
  el.setAttribute("aria-pressed",String(active));
  el.disabled=locked;
  el.title=locked?"En esta pantalla la tarjeta es parte del reproductor":"";
 });
}
function navigation(){
 const nav=document.getElementById("screen-list");
 let lastGroup="";
 for(const [key,item] of Object.entries(SCREENS)){
  const group=key==="music"||key==="video"||key==="settings"?"Inicio":
   ["songs","albums","artists","group"].includes(key)?"Biblioteca":
   ["appearance","effects","motion","info"].includes(key)?"Ajustes":
   ["player"].includes(key)?"Reproductor":"Estados";
  if(group!==lastGroup){
   const label=document.createElement("div");
   label.className="screen-group-label";label.textContent=group;nav.append(label);
   lastGroup=group;
  }
  const button=document.createElement("button");
  button.type="button";button.dataset.screen=key;
  button.textContent=item.label.replace(/^.*? · /,"");
  button.addEventListener("click",()=>{navigateTo(key,false);});
  nav.append(button);
 }
}

const ROOT_SCREENS=["music","video","settings"];
function navigateTo(key,remember=true){
 if(!SCREENS[key]||key===currentScreen)return;
 if(remember)navigationHistory.push(currentScreen);else navigationHistory=[];
 currentScreen=key;selectedRow=SCREENS[key].selected??0;refresh();
}
function navigateBack(){
 if(ROOT_SCREENS.includes(currentScreen))return false;
 const parents={appearance:"settings",effects:"appearance",motion:"appearance",info:"settings",
 songs:"music",albums:"music",artists:"music",group:"albums",player:"music",permission:"songs",error:"player"};
 currentScreen=navigationHistory.pop()||parents[currentScreen]||"music";
 selectedRow=SCREENS[currentScreen].selected??0;refresh();return true;
}
function openSelected(){
 const targets={music:["songs","albums","artists","player"],settings:["appearance","info"],
 appearance:["effects","motion"],albums:["group"],artists:["group"]};
 const next=targets[currentScreen]?.[selectedRow];
 if(next)navigateTo(next);
}
function rootSwipe(dir){
 const index=ROOT_SCREENS.indexOf(currentScreen);
 navigateTo(ROOT_SCREENS[Math.min(2,Math.max(0,index+dir))],false);
}
const viewport=$("preview-viewport");
let pointerStart=null;
viewport.style.touchAction="none";
viewport.addEventListener("pointerdown",event=>{
 if(event.isPrimary===false)return;
 pointerStart={x:event.clientX,y:event.clientY};
 viewport.setPointerCapture?.(event.pointerId);
});
viewport.addEventListener("pointercancel",()=>{pointerStart=null;});
viewport.addEventListener("pointerup",event=>{
 if(!pointerStart)return;
 const dx=event.clientX-pointerStart.x,dy=event.clientY-pointerStart.y;
 pointerStart=null;
 if(Math.abs(dx)>28&&Math.abs(dx)>Math.abs(dy)*1.15){
  if(ROOT_SCREENS.includes(currentScreen))rootSwipe(dx<0?1:-1);
  else if(dx<0)navigateBack();
  return;
 }
 if(Math.abs(dy)>28&&Math.abs(dy)>Math.abs(dx)*1.15){
  const rows=SCREENS[currentScreen].rows;
  if(rows?.length){
   selectedRow=Math.max(0,Math.min(rows.length-1,selectedRow+(dy<0?1:-1)));
   renderStage();
  }
  return;
 }
 if(Math.abs(dx)<12&&Math.abs(dy)<12){
  const rect=viewport.getBoundingClientRect();
  const x=(event.clientX-rect.left)/(rect.width/W),y=(event.clientY-rect.top)/(rect.width/W);
  if(ROOT_SCREENS.includes(currentScreen)&&y<465){
   const idx=Math.round((x-145)/115);
   if(idx>=0&&idx<ROOT_SCREENS.length)navigateTo(ROOT_SCREENS[idx],false);
  }else{
   const rows=SCREENS[currentScreen].rows;
   if(rows?.length&&y>=model.layouts[currentScreen].rows.y){
    const index=Math.floor((y-model.layouts[currentScreen].rows.y)/model.rowGap);
    if(index>=0&&index<rows.length){
     if(index===selectedRow)openSelected();
     else {selectedRow=index;renderStage();}
    }
   }
  }
 }
});
viewport.addEventListener("keydown",event=>{
 if(event.key==="ArrowLeft"&&navigateBack())event.preventDefault();
 if(event.key==="Enter"){openSelected();event.preventDefault();}
});

for(const button of document.querySelectorAll("[data-theme]"))
 button.addEventListener("click",()=>{theme=button.dataset.theme;refresh();});
for(const button of document.querySelectorAll("[data-playing]"))
 button.addEventListener("click",()=>{playing=button.dataset.playing==="on";refresh();});
function fitPreview(){
 const viewport=document.getElementById("preview-viewport");
 const scale=viewport.clientWidth/W;
 document.getElementById("preview").style.transform="scale("+scale+")";
}
navigation();
selectedRow=SCREENS[currentScreen].selected??0;
refresh();
new ResizeObserver(fitPreview).observe(document.getElementById("preview-viewport"));
fitPreview();
