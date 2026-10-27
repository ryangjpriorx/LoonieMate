/* Web check: loads web/index.html's scripts with a tiny fake DOM and renders every tab and Settings page for each parity household. */
const fs=require('fs'),vm=require('vm');
const html=fs.readFileSync(process.argv[2]||require('path').join(__dirname,'../web/index.html'),'utf8');const fx=require(process.argv[3]||'./fixtures.json');
const scripts=[...html.matchAll(/<script(?![^>]*src)[^>]*>([\s\S]*?)<\/script>/g)].map(m=>m[1]);
function el(id){const o={id,innerHTML:'',textContent:'',hidden:false,value:'',dataset:{},style:{setProperty(){},removeProperty(){}},children:[],
  classList:{add(){},remove(){},toggle(){},contains(){return false}},setAttribute(){},getAttribute(){return null},removeAttribute(){},addEventListener(){},
  querySelector(){return el('q')},querySelectorAll(){return[]},closest(){return null},appendChild(){},remove(){},focus(){},getBoundingClientRect(){return{top:0,bottom:0,left:0,right:0}},
  contains(){return false},scrollIntoView(){},insertAdjacentHTML(){}};o.querySelector=()=>null;return o}
const els={};const document={getElementById:id=>els[id]||(els[id]=el(id)),querySelector:()=>el('q'),querySelectorAll:()=>[],addEventListener(){},createElement:()=>el('c'),
  documentElement:Object.assign(el('html'),{dataset:{}}),body:el('body'),activeElement:null,head:el('head'),fonts:{ready:Promise.resolve()}};
const store={};const ctx={document,console,setTimeout:()=>0,clearTimeout(){},setInterval:()=>0,requestAnimationFrame(){},performance:{now:()=>0},
  localStorage:{getItem:k=>store[k]??null,setItem:(k,v)=>store[k]=String(v),removeItem:k=>delete store[k]},navigator:{userAgent:'node',platform:'x',maxTouchPoints:0,storage:{}},
  matchMedia:()=>({matches:false,addEventListener(){},addListener(){}}),location:{hash:'',search:'',href:'http://x/'},history:{replaceState(){}},
  indexedDB:undefined,crypto:require('crypto').webcrypto,TextEncoder,TextDecoder,URL,Blob:class{},CSS:{escape:s=>s},
  addEventListener(){},scrollTo(){},innerWidth:400,scrollX:0,scrollY:0,Intl,Date,Math,JSON,Promise,Object,Array,Number,String,Set,Map};
ctx.window=ctx;ctx.self=ctx;ctx.globalThis=ctx;vm.createContext(ctx);
for(const sc of scripts){try{vm.runInContext(sc.replace(/\ninit\(\);\s*\}\)\(\);\s*$/,'\nwindow.__T={S,render,st,todayStr,evRow,whyText,expLabel,DEFAULTS,C};\n})();'),ctx)}catch(e){console.log('load error',e.message)}}
let fails=0;
for(const sc of fx.scenarios){const docs=sc.docs;
  vm.runInContext(`(function(d){const {S,DEFAULTS}=__T;S.settings=Object.assign({},DEFAULTS,d['settings/main']||{});S.bills={};S.months={};S.expenses={};
    for(const [k,v] of Object.entries(d)){if(k.startsWith('bills/'))S.bills[k.slice(6)]=v;else if(/^months\\/\\d{4}-\\d\\d$/.test(k))S.months[k.slice(7)]=v;else if(/^months\\/(\\d{4}-\\d\\d)\\/expenses\\/(.+)$/.test(k)){const m=k.match(/^months\\/(\\d{4}-\\d\\d)\\/expenses\\/(.+)$/);(S.expenses[m[1]]=S.expenses[m[1]]||{})[m[2]]=v}}
    S.loaded={settings:true,bills:true,months:true};S.activeYm=null;S.lastKey=null})`,ctx)(docs);
  const pages=[null,'household','accounts','income','food','quick','reminders','look','access','backup','sync','updates','ontrack','about','reset'];
  const tabs=['today','month','fixed','spending','savings'].map(t=>[t,null]).concat(pages.map(p=>['settings',p]));
  for(const [t,p] of tabs){try{vm.runInContext(`__T.S.tab=${JSON.stringify(t)};__T.S.setPage=${JSON.stringify(p)};__T.render();`,ctx);
      const out=els.main?els.main.innerHTML:'';if(/undefined|NaN|\[object Object\]/.test(out.replace(/data-items="[^"]*"/g,''))){const m=out.match(/.{60}(undefined|NaN|\[object Object\]).{40}/);console.log('suspicious',sc.name,t,p,m&&m[0]);}
    }catch(e){fails++;console.log('FAIL',sc.name,t,p,e.stack.split('\n').slice(0,3).join(' | '))}}
  /* the Take out cash menus */
  try{vm.runInContext(`{const {C,st,todayStr,expLabel,evRow,whyText}=__T;for(const e of C.upcoming(st(),todayStr(),40,expLabel)){if(e.kind==='move'){whyText(e);evRow(e)}else evRow(e)}}`,ctx)}catch(e){fails++;console.log('FAIL rows',sc.name,e.message)}
}
console.log(fails?fails+" failures":"Web check: every screen rendered for every household.");if(fails)process.exit(1);
