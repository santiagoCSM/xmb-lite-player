"use strict";
// Dependency-free interaction smoke test. Run with: node --test design-lab/smoke.test.cjs
const test=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const path=require("node:path");

function fixture(){
 const all=new Map();
 function el(){
  const attributes={};
  const classes=new Set();
  return {
   dataset:{},style:{},children:[],hidden:false,disabled:false,
   classList:{
    toggle(name,on){if(on)classes.add(name);else classes.delete(name);return classes.has(name)},
    contains(name){return classes.has(name)}
   },
   setAttribute(name,value){attributes[name]=value},
   removeAttribute(name){delete attributes[name]},
   getAttribute(name){return attributes[name]},
   addEventListener(type,handler){this["on_"+type]=handler},
   append(...children){this.children.push(...children)},
   replaceChildren(...children){this.children=[...children]},
   clientWidth:270,
   click(){if(!this.disabled)this.on_click?.()}
  };
 }
 for(const name of ["stage","preview","page-name","reference","reference-note","screen-list","preview-viewport"])all.set(name,el());
 const buttons=[...["blue","violet"].map(v=>Object.assign(el(),{dataset:{theme:v}})),
  ...["off","on"].map(v=>Object.assign(el(),{dataset:{playing:v}}))];
 const document={
  getElementById(id){return all.get(id)||null},
  createElement:el,
  querySelectorAll(selector){
   if(selector==="[data-theme]")return buttons.filter(b=>b.dataset.theme);
   if(selector==="[data-playing]")return buttons.filter(b=>b.dataset.playing);
   if(selector==="[data-screen]")return all.get("screen-list").children.filter(b=>b.dataset.screen);
   return [];
  }
 };
 const context=vm.createContext({
  document,structuredClone,DOMParser:class{},ResizeObserver:class{observe(){}},
  fetch:()=>Promise.reject(new Error("No network in smoke tests")),
  console,Promise,Map,Set
 });
 const source=fs.readFileSync(path.join(__dirname,"editor.js"),"utf8");
 vm.runInContext(source,context,{filename:"editor.js"});
 const click=(kind,value)=>{const btn=buttons.find(b=>b.dataset[kind]===value);assert.ok(btn);btn.click();return btn};
 const navigate=key=>{
  const button=all.get("screen-list").children.find(b=>b.dataset.screen===key);
  assert.ok(button,"missing screen "+key);button.click();
 };
 const visibleCard=()=>all.get("stage").children.some(x=>x.dataset.layer==="card");
 return {context,all,buttons,click,navigate,visibleCard};
}

test("loads and navigates with no runtime error",()=>{
 const x=fixture();
 assert.equal(x.all.get("page-name").textContent,"Inicio · Música");
 assert.ok(x.all.get("stage").children.length>0);
 x.navigate("albums");
 assert.equal(x.all.get("page-name").textContent,"Biblioteca · Álbumes");
});

test("theme changes the web background and keeps real reference",()=>{
 const x=fixture();
 x.click("theme","violet");
 assert.equal(x.all.get("preview").classList.contains("violet"),true);
 assert.match(x.all.get("reference").src,/musica-violeta\.webp$/);
 assert.equal(x.all.get("reference-note").hidden,true);
 x.navigate("albums");
 assert.equal(x.all.get("preview").classList.contains("violet"),true);
 assert.match(x.all.get("reference").src,/albumes\.webp$/);
 assert.equal(x.all.get("reference-note").hidden,false);
 x.click("theme","blue");
 assert.equal(x.all.get("reference-note").hidden,true);
});

test("card toggle affects preview and matching captured image",()=>{
 const x=fixture();
 assert.equal(x.visibleCard(),false);
 x.click("playing","on");
 assert.equal(x.visibleCard(),true);
 assert.match(x.all.get("reference").src,/tarjeta-en-inicio\.webp$/);
 assert.equal(x.all.get("reference-note").hidden,true);
 x.click("playing","off");
 assert.equal(x.visibleCard(),false);
 assert.match(x.all.get("reference").src,/musica-azul\.webp$/);
});

test("unrecorded states do not blank or mislabel the original",()=>{
 const x=fixture();
 x.click("theme","violet");
 x.click("playing","on");
 assert.equal(x.visibleCard(),true);
 assert.match(x.all.get("reference").src,/musica-azul\.webp$/);
 assert.equal(x.all.get("reference-note").hidden,false);
 assert.match(x.all.get("reference").alt,/Captura base/);
});

test("full player always has its card and locks card controls",()=>{
 const x=fixture();
 x.navigate("player");
 assert.equal(x.visibleCard(),true);
 assert.ok(x.buttons.filter(b=>b.dataset.playing).every(b=>b.disabled));
 x.navigate("music");
 assert.ok(x.buttons.filter(b=>b.dataset.playing).every(b=>!b.disabled));
});

test("swipe left returns from subpage and keeps root carousel",()=>{
 const x=fixture();
 const surface=x.all.get("preview-viewport");
 const swipe=(dx,dy=0)=>{
  surface.on_pointerdown({isPrimary:true,clientX:200,clientY:250,pointerId:1});
  surface.on_pointerup({clientX:200+dx,clientY:250+dy});
 };
 x.navigate("appearance");
 assert.equal(x.all.get("page-name").textContent,"Ajustes · Apariencia");
 swipe(-110);
 assert.equal(x.all.get("page-name").textContent,"Inicio · Ajustes");
 swipe(-110);
 assert.equal(x.all.get("page-name").textContent,"Inicio · Ajustes");
 swipe(110);
 assert.equal(x.all.get("page-name").textContent,"Inicio · Video");
});
test("vertical swipe changes focus without changing screens",()=>{
 const x=fixture();
 const surface=x.all.get("preview-viewport");
 const swipe=dy=>{
  surface.on_pointerdown({isPrimary:true,clientX:260,clientY:400,pointerId:1});
  surface.on_pointerup({clientX:260,clientY:400+dy});
 };
 const before=x.all.get("stage").children.find(c=>c.dataset.layer==="rows").innerHTML;
 swipe(-80);
 const after=x.all.get("stage").children.find(c=>c.dataset.layer==="rows").innerHTML;
 assert.notEqual(before,after);
 assert.equal(x.all.get("page-name").textContent,"Inicio · Música");
});
