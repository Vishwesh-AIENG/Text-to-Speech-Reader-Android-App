/* OmniLingo — React page (mounts into #page). All interactive mini-demos.
   No branded UI — all original design inspired by a teal/aurora aura. */

const { useState, useEffect, useRef, useMemo, useCallback } = React;

// ------------------------------------------------------------
// Shared Web Speech API helper. All phones speak through this.
// ------------------------------------------------------------
const synth = typeof window !== 'undefined' ? window.speechSynthesis : null;

function useSpeak(){
  const [speakingId, setSpeakingId] = useState(null); // which phone is playing
  const [wordIdx, setWordIdx] = useState(-1);
  const utterRef = useRef(null);

  const stop = useCallback(() => {
    if (synth){ try { synth.cancel(); } catch(e){} }
    setSpeakingId(null);
    setWordIdx(-1);
  }, []);

  const speak = useCallback((id, text, opts = {}) => {
    if (!synth || !text) return;
    // toggle off if same
    if (speakingId === id){ stop(); return; }
    try { synth.cancel(); } catch(e){}
    const u = new SpeechSynthesisUtterance(text);
    u.rate = opts.rate ?? 1;
    u.pitch = opts.pitch ?? 1;
    u.lang = opts.lang ?? 'en-US';
    // try to pick a matching voice
    const voices = synth.getVoices();
    if (voices && voices.length){
      const match = voices.find(v => v.lang && v.lang.toLowerCase().startsWith(u.lang.toLowerCase().slice(0,2)));
      if (match) u.voice = match;
    }
    u.onstart = () => { setSpeakingId(id); setWordIdx(0); };
    u.onend   = () => { setSpeakingId(s => s===id ? null : s); setWordIdx(-1); };
    u.onerror = () => { setSpeakingId(s => s===id ? null : s); setWordIdx(-1); };
    u.onboundary = (ev) => {
      if (ev.name !== 'word' && ev.name !== undefined) return;
      // derive index from charIndex
      const upto = text.slice(0, ev.charIndex || 0);
      const i = upto.trim().length ? upto.trim().split(/\s+/).length : 0;
      setWordIdx(i);
    };
    utterRef.current = u;
    synth.speak(u);
  }, [speakingId, stop]);

  // ensure voice list loads (Chrome lazy-loads)
  useEffect(() => {
    if (!synth) return;
    const load = () => synth.getVoices();
    load();
    synth.addEventListener?.('voiceschanged', load);
    return () => {
      synth.removeEventListener?.('voiceschanged', load);
      try { synth.cancel(); } catch(e){}
    };
  }, []);

  return { speak, stop, speakingId, wordIdx };
}

// Global speak instance (shared across all phones)
const SpeakCtx = React.createContext(null);
function SpeakProvider({ children }){
  const api = useSpeak();
  return <SpeakCtx.Provider value={api}>{children}</SpeakCtx.Provider>;
}
function useSpeakCtx(){ return React.useContext(SpeakCtx); }

// ------------------------------------------------------------
// Brand mark — an original mark: concentric speech waves + stroke
// ------------------------------------------------------------
function BrandMark(){
  return (
    <svg viewBox="0 0 40 40" fill="none">
      <defs>
        <linearGradient id="bmg" x1="0" y1="0" x2="40" y2="40">
          <stop offset="0" stopColor="#7cf5d4"/>
          <stop offset="1" stopColor="#2d9cdb"/>
        </linearGradient>
      </defs>
      <circle cx="20" cy="20" r="4" fill="url(#bmg)"/>
      <path d="M20 8 a12 12 0 0 1 12 12" stroke="url(#bmg)" strokeWidth="1.6" strokeLinecap="round"/>
      <path d="M20 32 a12 12 0 0 1 -12 -12" stroke="url(#bmg)" strokeWidth="1.6" strokeLinecap="round" opacity=".75"/>
      <path d="M20 2 a18 18 0 0 1 18 18" stroke="#7cf5d4" strokeWidth="1" strokeLinecap="round" opacity=".45"/>
      <path d="M20 38 a18 18 0 0 1 -18 -18" stroke="#2d9cdb" strokeWidth="1" strokeLinecap="round" opacity=".45"/>
    </svg>
  );
}

// ------------------------------------------------------------
// Top bar
// ------------------------------------------------------------
function TopBar(){
  return (
    <header className="topbar">
      <div className="brand">
        <div className="brand-mark"><BrandMark/></div>
        <div className="brand-name">Omni<span>Lingo</span></div>
      </div>
      <nav className="nav">
        <a href="#modes">Modes</a>
        <a href="#tech">Tech</a>
        <a href="#languages">Languages</a>
        <a href="#privacy">Privacy</a>
      </nav>
      <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App" target="_blank" rel="noopener noreferrer" className="cta">⌥ view source ↗</a>
    </header>
  );
}

// ------------------------------------------------------------
// Hero phone — tab-switchable between all 5 modes, real TTS.
// ------------------------------------------------------------
const HERO_MODES = [
  { key:'BABEL',   label:'Babel',    color:'#7cf5d4' },
  { key:'CLASSIC', label:'Classic',  color:'#5fd7ff' },
  { key:'FOCUS',   label:'Focus',    color:'#9ef0ff' },
  { key:'LENS',    label:'AR Lens',  color:'#ff8bb3' },
  { key:'READER',  label:'Reader',   color:'#ffc872' },
];

function HeroPhoneClassic({ playing, wordIdx, onPlay, setReadout }){
  const srcEn = "The library will close at nine o'clock tonight.";
  const tgtEs = "La biblioteca cerrará a las nueve esta noche.";
  const [lang, setLang] = useState('EN'); // EN or ES
  const text = lang === 'EN' ? srcEn : tgtEs;
  const words = text.split(' ');
  useEffect(() => { setReadout(text); }, [text]);
  return (
    <>
      {/* paper */}
      <div style={{margin:'0 14px', height:180, borderRadius:18, overflow:'hidden', position:'relative',
        background:'linear-gradient(135deg,#0b2a3c,#03101a)', border:'1px solid rgba(95,215,255,.2)'}}>
        <div style={{position:'absolute', left:22, top:32, right:22, padding:14, borderRadius:6,
          background:'linear-gradient(180deg,#f5ebd8,#e8d9bd)', color:'#2a1f10',
          fontFamily:'Fraunces, serif', fontSize:11, lineHeight:1.35, transform:'rotate(-2deg)',
          boxShadow:'0 12px 30px rgba(0,0,0,.6)'}}>
          The library will close<br/>at nine o'clock tonight.<br/>
          <span style={{fontSize:9, opacity:.6}}>— please return your books.</span>
        </div>
        <svg style={{position:'absolute', inset:0, width:'100%', height:'100%'}} viewBox="0 0 260 180" preserveAspectRatio="none">
          <rect x="10" y="10" width="240" height="160" fill="none" stroke="rgba(124,245,212,.7)" strokeWidth="1.2" strokeDasharray="6 4"/>
          {playing && <rect x="18" y="26" width="224" height="80" fill="rgba(124,245,212,.12)" stroke="#7cf5d4" strokeWidth="1.5"/>}
        </svg>
        <div style={{position:'absolute', top:8, left:10, fontFamily:'JetBrains Mono', fontSize:9, color:'#7cf5d4', letterSpacing:'.1em'}}>● LIVE OCR</div>
        <div style={{position:'absolute', bottom:8, right:10, fontFamily:'JetBrains Mono', fontSize:9, color:'#9ef0ff'}}>
          {playing ? '♪ speaking' : 'ready'}
        </div>
        {/* lang toggle inside viewport */}
        <div style={{position:'absolute', top:6, right:8, display:'flex', gap:4}}>
          {['EN','ES'].map(l => (
            <button key={l} onClick={()=>setLang(l)} style={{
              fontFamily:'JetBrains Mono', fontSize:9, padding:'3px 7px', borderRadius:6,
              border:'1px solid rgba(95,215,255,.3)', cursor:'pointer',
              background: lang===l ? 'rgba(124,245,212,.2)' : 'transparent',
              color: lang===l ? '#7cf5d4' : '#9ef0ff'
            }}>{l}</button>
          ))}
        </div>
      </div>
      {/* readout */}
      <div style={{margin:'10px 14px 0', padding:'10px', borderRadius:14,
        background:'rgba(10,30,48,.6)', border:'1px solid rgba(95,215,255,.18)', overflow:'hidden'}}>
        <div style={{fontFamily:'JetBrains Mono', fontSize:9, color:'#7cf5d4', letterSpacing:'.14em', marginBottom:5}}>
          {lang === 'EN' ? 'SOURCE · EN' : 'TRANSLATED · ES'}
        </div>
        <div style={{fontSize:11, lineHeight:1.4, color:'#eaf4f8', overflow:'hidden', maxHeight:54,
          display:'-webkit-box', WebkitLineClamp:3, WebkitBoxOrient:'vertical', wordBreak:'break-word'}}>
          {words.map((w,i)=>(
            <span key={i} style={{
              background: playing && i===wordIdx ? '#7cf5d4' : 'transparent',
              color: playing && i===wordIdx ? '#051320' : (playing && i<wordIdx ? '#7cf5d4' : '#eaf4f8'),
              padding:'1px 3px', borderRadius:3, marginRight:2, transition:'all .15s'
            }}>{w}</span>
          ))}
        </div>
      </div>
    </>
  );
}

function HeroPhoneBabel({ playing, setReadout, setReadoutLang }){
  const [side, setSide] = useState('A');
  const speakCtx = useSpeakCtx();
  const lineA = "Where is the train station?";
  const lineB = "駅はどこですか？";
  // NO auto-flip — user controls which speaker is active
  useEffect(() => {
    setReadout(side==='A' ? lineA : lineB);
    setReadoutLang(side==='A' ? 'en-US' : 'ja-JP');
  }, [side]);
  return (
    <div style={{padding:'0 14px'}}>
      <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:8}}>
        {['A','B'].map(s => (
          <button key={s} onClick={()=>setSide(s)} style={{
            padding:10, borderRadius:12, cursor:'pointer',
            border: `1px solid ${side===s ? '#7cf5d4' : 'rgba(143,185,204,.15)'}`,
            background: side===s ? 'rgba(124,245,212,.1)' : 'rgba(10,30,48,.4)',
            color:'inherit', textAlign:'left'
          }}>
            <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.1em'}}>SPEAKER {s}</div>
            <div style={{fontSize:11, marginTop:4, color:'#eaf4f8'}}>{s==='A' ? 'EN' : 'JA'}</div>
          </button>
        ))}
      </div>
      <div style={{marginTop:12, padding:12, borderRadius:12, background:'rgba(10,30,48,.7)', border:'1px solid rgba(124,245,212,.3)', minHeight:130}}>
        <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.14em'}}>
          LISTENING · {side==='A'?'EN':'JA'}
        </div>
        <div style={{display:'flex',gap:2,marginTop:10,alignItems:'center',height:28}}>
          {Array.from({length:28}).map((_,i)=>(
            <div key={i} style={{
              width:3,
              height: 4 + (playing ? Math.abs(Math.sin(Date.now()/120 + i*0.4))*22 : Math.random()*8),
              background:'#7cf5d4', borderRadius:1.5, opacity:.4+Math.random()*.5
            }}/>
          ))}
        </div>
        <div style={{marginTop:10, fontSize:11, color:'#cde4ee', lineHeight:1.4, overflow:'hidden', maxHeight:40, display:'-webkit-box', WebkitLineClamp:2, WebkitBoxOrient:'vertical'}}>
          "{side==='A' ? lineA : lineB}"
        </div>
        <button onClick={()=>speakCtx.speak('hero-babel-'+side, side==='A'?lineA:lineB, { lang: side==='A'?'en-US':'ja-JP' })} style={{
          marginTop:10, padding:'5px 10px', borderRadius:6,
          border:'1px solid rgba(124,245,212,.4)', background:'rgba(124,245,212,.1)',
          color:'#9ef0ff', fontFamily:'JetBrains Mono', fontSize:9, cursor:'pointer'
        }}>▶ play translation</button>
      </div>
    </div>
  );
}

function HeroPhoneFocus({ playing, wordIdx, setReadout }){
  const lines = ['The quiet river carried', 'the small wooden boat', 'toward the rising sun.'];
  const [row, setRow] = useState(0);
  useEffect(()=>{
    setReadout(lines[row]);
  }, [row]);
  useEffect(()=>{
    const id = setInterval(()=>setRow(r=>(r+1)%lines.length), 1800);
    return ()=>clearInterval(id);
  },[]);
  return (
    <div style={{padding:'10px 14px'}}>
      {lines.map((l,i)=>{
        const active = i===row;
        return (
          <div key={i} style={{
            padding:'12px 12px', borderRadius:10, margin:'6px 0',
            background: active ? 'linear-gradient(90deg, rgba(124,245,212,.2), rgba(95,215,255,.08))' : 'transparent',
            border: active ? '1px solid rgba(124,245,212,.4)' : '1px solid transparent',
            fontFamily:'Fraunces, serif', fontSize:14, lineHeight:1.4,
            color: active ? '#eaf4f8' : 'rgba(143,185,204,.3)',
            filter: active ? 'none' : 'blur(.5px)', transition:'all .3s'
          }}>{l}</div>
        );
      })}
      <div style={{display:'flex', gap:6, justifyContent:'center', marginTop:12}}>
        {[{l:'◀',i:-1},{l:'▶',i:1}].map((b,k)=>(
          <button key={k} onClick={()=>setRow(r=>{
            const next=(r+b.i+lines.length)%lines.length;
            setReadout(lines[next]);
            return next;
          })} style={{
            width:40,height:30,borderRadius:8, cursor:'pointer',
            background:'rgba(124,245,212,.15)', border:'1px solid rgba(124,245,212,.3)',
            fontSize:10, color:'#9ef0ff', fontFamily:'JetBrains Mono'
          }}>{b.l}</button>
        ))}
      </div>
    </div>
  );
}

function HeroPhoneLens({ setReadout }){
  const [flip, setFlip] = useState(true);
  const labels = [
    { x:10, y:18, src:'CAFÉ', tr:'COFFEE' },
    { x:18, y:52, src:'PANADERÍA', tr:'BAKERY' },
    { x:58, y:34, src:'ABIERTO', tr:'OPEN' },
  ];
  useEffect(()=>{
    setReadout('Coffee. Bakery. Open.');
  }, []);
  return (
    <div style={{padding:'0 14px'}}>
      <div style={{height:220, borderRadius:14, position:'relative', overflow:'hidden',
        background:'linear-gradient(180deg,#1a2f3a 0%, #0e1d28 60%, #061420 100%)',
        border:'1px solid rgba(95,215,255,.2)'}}>
        <div style={{position:'absolute', left:8, top:8, right:8, bottom:80, borderRadius:6,
          background:'repeating-linear-gradient(90deg,#1b3447 0 14px,#173042 14px 28px)', opacity:.75}}/>
        <div style={{position:'absolute', left:8, bottom:10, right:8, height:68, borderRadius:6,
          background:'linear-gradient(180deg,#2a1f16,#19110b)'}}/>
        {labels.map((l,i)=>(
          <div key={i} style={{
            position:'absolute', left:`${l.x}%`, top:`${l.y}%`,
            fontFamily:'JetBrains Mono', fontSize:10, padding:'4px 8px', borderRadius:6,
            background:'rgba(5,19,32,.85)',
            color: flip ? '#cde4ee' : '#7cf5d4',
            border:`1px solid ${flip?'rgba(143,185,204,.3)':'rgba(124,245,212,.6)'}`,
            boxShadow: flip ? 'none' : '0 0 10px rgba(124,245,212,.35)',
            transition:'all .4s'
          }}>{flip?l.src:l.tr}</div>
        ))}
      </div>
      <button onClick={()=>setFlip(f=>!f)} style={{
        marginTop:14, width:'100%', padding:'10px', borderRadius:10, cursor:'pointer',
        background:'linear-gradient(135deg,#2d9cdb,#5fd7ff)', color:'#051320',
        fontFamily:'JetBrains Mono', fontSize:10, letterSpacing:'.1em', fontWeight:600,
        border:0
      }}>{flip ? 'TRANSLATE →' : '← SHOW ORIGINAL'}</button>
    </div>
  );
}

function HeroPhoneReader({ setReadout, setReadoutLang }){
  const [tab, setTab] = useState(0);
  const tabs = [
    { n:'EN', text:'The sea was calm that morning. A thin mist rose from the water.', lang:'en-US' },
    { n:'FR', text:"La mer était calme ce matin-là. Une brume légère s'élevait de l'eau.", lang:'fr-FR' },
    { n:'SUM', list:['Opens on a calm sea','Thin mist rising from water','An old fisherman appears','He hums while mending nets'], lang:'en-US' },
  ];
  useEffect(() => {
    if (tab < 2){
      setReadout(tabs[tab].text);
      setReadoutLang(tabs[tab].lang);
    } else {
      setReadout(tabs[2].list.join('. '));
      setReadoutLang('en-US');
    }
  }, [tab]);
  return (
    <div style={{padding:'0 14px'}}>
      <div style={{display:'flex', gap:4, marginBottom:10}}>
        {['ORIGINAL','FRANÇAIS','AI SUMMARY'].map((t,i)=>(
          <button key={t} onClick={()=>setTab(i)} style={{
            flex:1, padding:'7px 4px', borderRadius:8, cursor:'pointer',
            fontFamily:'JetBrains Mono', fontSize:9, letterSpacing:'.08em',
            background: i===tab ? 'rgba(124,245,212,.18)' : 'rgba(143,185,204,.05)',
            border:`1px solid ${i===tab ? '#7cf5d4' : 'rgba(143,185,204,.12)'}`,
            color: i===tab ? '#9ef0ff' : '#6aa2b5'
          }}>{t}</button>
        ))}
      </div>
      <div style={{padding:14, borderRadius:12, background:'rgba(10,30,48,.6)', border:'1px solid rgba(143,185,204,.14)', minHeight:160}}>
        {tab < 2 ? (
          <div style={{fontFamily:'Fraunces, serif', fontSize:12, lineHeight:1.55, color:'#eaf4f8'}}>
            {tabs[tab].text}
          </div>
        ) : (
          <div>
            <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.14em',marginBottom:8}}>⌬ GEMMA · ON-DEVICE</div>
            {tabs[2].list.map((s,i)=>(
              <div key={i} style={{display:'flex',gap:8, marginBottom:6, fontSize:11, color:'#cde4ee'}}>
                <span style={{color:'#7cf5d4'}}>◆</span><span>{s}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function HeroPhone(){
  const [mode, setMode] = useState('BABEL');
  const [readout, setReadout] = useState('Where is the train station?');
  const [readoutLang, setReadoutLang] = useState('en-US');
  const { speak, stop, speakingId, wordIdx } = useSpeakCtx();
  const playing = speakingId === 'hero-main';

  const onPlay = () => {
    if (mode === 'LENS'){
      speak('hero-main', 'Coffee. Bakery. Open.', { lang:'en-US' });
    } else {
      speak('hero-main', readout, { lang: readoutLang });
    }
  };

  return (
    <div className="phone-wrap">
      <div className="phone">
        <div className="notch"/>
        <div className="phone-screen">
          {/* status bar */}
          <div style={{display:'flex', justifyContent:'space-between', padding:'14px 22px 8px', fontFamily:'JetBrains Mono', fontSize:11, color:'#9ef0ff', letterSpacing:'.08em'}}>
            <span>9:41</span>
            <span style={{opacity:.6}}>●●●● 5G</span>
          </div>
          {/* mode tabs — pill switcher, no overflow */}
          <div style={{display:'flex', gap:3, padding:'0 10px 10px'}}>
            {HERO_MODES.map(m => (
              <button key={m.key} onClick={()=>{ stop(); setMode(m.key); setReadoutLang('en-US'); }} style={{
                fontFamily:'JetBrains Mono', fontSize:7, letterSpacing:'.04em',
                padding:'4px 0', borderRadius:6, cursor:'pointer', flex:'1 1 0', minWidth:0,
                border:`1px solid ${mode===m.key ? m.color : 'rgba(95,215,255,.18)'}`,
                background: mode===m.key ? `${m.color}28` : 'rgba(10,30,48,.4)',
                color: mode===m.key ? m.color : '#6aa2b5',
                transition:'all .2s', whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis'
              }}>{m.label.toUpperCase()}</button>
            ))}
          </div>
          {/* mode body */}
          <div style={{minHeight:340}}>
            {mode==='CLASSIC' && <HeroPhoneClassic playing={playing} wordIdx={wordIdx} setReadout={setReadout} onPlay={onPlay}/>}
            {mode==='BABEL'   && <HeroPhoneBabel playing={playing} setReadout={setReadout} setReadoutLang={setReadoutLang}/>}
            {mode==='FOCUS'   && <HeroPhoneFocus playing={playing} wordIdx={wordIdx} setReadout={setReadout}/>}
            {mode==='LENS'    && <HeroPhoneLens setReadout={setReadout}/>}
            {mode==='READER'  && <HeroPhoneReader setReadout={setReadout} setReadoutLang={setReadoutLang}/>}
          </div>
          {/* bottom controls — real TTS for ALL modes */}
          <div style={{position:'absolute', bottom:18, left:0, right:0, display:'flex', justifyContent:'center', gap:14, alignItems:'center'}}>
            <button onClick={stop} title="stop" style={{
              width:40,height:40,borderRadius:20,background:'rgba(143,185,204,.1)',
              border:'1px solid rgba(143,185,204,.2)',display:'flex',alignItems:'center',
              justifyContent:'center',fontSize:13,color:'#9ef0ff',cursor:'pointer'
            }}>■</button>
            <button onClick={onPlay} title={playing ? 'pause' : 'play'} style={{
              width:58,height:58,borderRadius:29,
              background: playing
                ? 'linear-gradient(135deg,#7cf5d4,#2d9cdb)'
                : 'linear-gradient(135deg,#2d9cdb,#5fd7ff)',
              display:'flex',alignItems:'center',justifyContent:'center',
              fontSize:20,color:'#051320',cursor:'pointer',border:0,
              boxShadow: playing
                ? '0 0 0 6px rgba(124,245,212,.2), 0 8px 24px rgba(95,215,255,.4)'
                : '0 8px 24px rgba(95,215,255,.4)',
              transform: playing ? 'scale(1.08)' : 'scale(1)',
              transition:'all .2s'
            }}>{playing ? '❙❙' : '▶'}</button>
            <button onClick={()=>setMode(HERO_MODES[(HERO_MODES.findIndex(x=>x.key===mode)+1)%HERO_MODES.length].key)} title="next mode" style={{
              width:40,height:40,borderRadius:20,background:'rgba(143,185,204,.1)',
              border:'1px solid rgba(143,185,204,.2)',display:'flex',alignItems:'center',
              justifyContent:'center',fontSize:14,color:'#9ef0ff',cursor:'pointer'
            }}>⇆</button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------
// Hero
// ------------------------------------------------------------
function Hero(){
  return (
    <section className="hero">
      <div>
        <div className="eyebrow"><span className="dot"/> ON-DEVICE · OFFLINE-FIRST · PRIVATE</div>
        <h1 className="display">
          Point. Read. <em>Speak</em><br/>
          any language,<br/>
          <em>anywhere.</em>
        </h1>
        <p className="lede">
          OmniLingo turns your camera into a universal language engine. Six purpose-built
          modes — OCR reader, live conversation, dyslexia focus, AR overlay, bilingual
          e-reader, and a model manager — all running on-device with zero cloud round-trips.
        </p>
        <div className="hero-ctas">
          <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App" target="_blank" rel="noopener noreferrer" className="btn-primary">View Source ↗</a>
          <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App" target="_blank" rel="noopener noreferrer" className="btn-ghost">Build it yourself ⌥</a>
        </div>
        <div className="hero-stats">
          <div><b>50+</b>languages</div>
          <div><b>6</b>isolated modes</div>
          <div><b>60</b>fps AR overlay</div>
          <div><b>0</b>cloud calls</div>
        </div>
      </div>
      <HeroPhone/>
    </section>
  );
}

// ------------------------------------------------------------
// Pipeline strip
// ------------------------------------------------------------
function Pipeline(){
  const steps = [
    { lbl:'01 · CAPTURE', ttl:'Point', desc:'CameraX delivers a live frame buffer. No preview lag.' },
    { lbl:'02 · RECOGNIZE', ttl:'Read', desc:'ML Kit OCR extracts printed or handwritten text on-device.' },
    { lbl:'03 · TRANSLATE', ttl:'Translate', desc:'ML Kit on-device translator, 50+ pairs, fully offline.' },
    { lbl:'04 · VOICE', ttl:'Speak', desc:'Native Android TTS with word-level highlighting and pitch control.' },
  ];
  return (
    <section className="pad" id="modes">
      <div className="kicker">THE LOOP</div>
      <h2 className="section-h">Four steps. <em>Zero</em> cloud.</h2>
      <p className="section-sub">
        Every mode in OmniLingo is a remix of the same four-step pipeline. Background
        isolation means switching modes instantly tears down the camera, mic and TTS — so
        battery and privacy stay yours.
      </p>
      <div className="pipeline">
        {steps.map(s => (
          <div className="step" key={s.lbl}>
            <div className="lbl">{s.lbl}</div>
            <div className="ttl">{s.ttl}</div>
            <div className="desc">{s.desc}</div>
          </div>
        ))}
      </div>
    </section>
  );
}

// ------------------------------------------------------------
// DEMO 1 — Classic TTS: live OCR highlight
// ------------------------------------------------------------
function DemoClassic(){
  const samples = [
    "Light travels in waves, bending gently through every medium it meets.",
    "The library will close at nine o'clock tonight. Please return your books.",
    "Mix the flour and sugar in a large bowl, then add warm water slowly.",
  ];
  const [sIdx, setSIdx] = useState(0);
  const [rate, setRate] = useState(1);
  const sentence = samples[sIdx];
  const words = sentence.split(' ');
  const { speak, speakingId, wordIdx } = useSpeakCtx();
  const playing = speakingId === 'demo-classic';
  const idx = playing ? wordIdx : -1;
  return (
    <div className="mini-phone">
      <div className="screen" style={{padding:'14px'}}>
        <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.16em',marginBottom:10}}>● CLASSIC TTS</div>
        <div style={{height:120, borderRadius:12, border:'1px dashed rgba(95,215,255,.3)', position:'relative', overflow:'hidden',
          background:'repeating-linear-gradient(0deg,rgba(143,185,204,.04) 0 8px, transparent 8px 16px)'}}>}
          <div style={{position:'absolute', inset:10, fontFamily:'Fraunces',fontSize:11, lineHeight:1.4, color:'#cde4ee', transform:'rotate(-1deg)', overflow:'hidden', maxHeight:100}}>
            {sentence}
          </div>
          <div style={{position:'absolute', top:6, left:8, fontFamily:'JetBrains Mono',fontSize:8, color:'#7cf5d4'}}>CAM</div>
        </div>
        <div style={{marginTop:14, padding:12, borderRadius:12, background:'rgba(10,30,48,.7)', border:'1px solid rgba(95,215,255,.18)', overflow:'hidden'}}>
          <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:6}}>
            <span style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#9ef0ff',letterSpacing:'.14em'}}>LIVE · EN</span>
            <button onClick={()=>{setSIdx((sIdx+1)%samples.length);}} style={{fontFamily:'JetBrains Mono',fontSize:8,padding:'3px 7px',borderRadius:6,border:'1px solid rgba(95,215,255,.3)',background:'transparent',color:'#9ef0ff',cursor:'pointer',whiteSpace:'nowrap'}}>↻ new scan</button>
          </div>
          <div style={{fontSize:11, lineHeight:1.45, color:'#eaf4f8', wordBreak:'break-word', overflowWrap:'break-word', overflow:'hidden'}}>
            {words.map((w,i)=>(
              <span key={i} style={{
                color: i===idx ? '#051320' : (i < idx ? '#7cf5d4' : '#cde4ee'),
                background: i===idx ? '#7cf5d4' : 'transparent',
                padding: i===idx ? '1px 4px' : '1px 0',
                borderRadius:4, marginRight:3, transition:'all .15s'
              }}>{w}</span>
            ))}
          </div>
        </div>
        <div style={{display:'flex',gap:10,marginTop:14,alignItems:'center'}}>
          <button onClick={()=>speak('demo-classic', sentence, { rate })} style={{
            width:38,height:38,borderRadius:19,border:0,cursor:'pointer',
            background:'linear-gradient(135deg,#2d9cdb,#5fd7ff)',color:'#051320',
            fontSize:14,boxShadow:'0 4px 14px rgba(95,215,255,.4)'
          }}>{playing ? '❙❙' : '▶'}</button>
          <div style={{flex:1}}>
            <input type="range" min="0.5" max="2" step="0.1" value={rate} onChange={e=>setRate(+e.target.value)} style={{width:'100%',accentColor:'#7cf5d4'}}/>
          </div>
          <div style={{fontFamily:'JetBrains Mono',fontSize:10,color:'#9ef0ff',minWidth:32,textAlign:'right'}}>{rate.toFixed(1)}×</div>
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------
// DEMO 2 — Babel engine: waveform + flipping speaker
// ------------------------------------------------------------
function DemoBabel(){
  const [side, setSide] = useState('A');
  const [bars, setBars] = useState(()=>Array.from({length:28},()=>Math.random()));
  const { speak, speakingId } = useSpeakCtx();
  useEffect(()=>{
    const wave = setInterval(()=>setBars(Array.from({length:28},()=>Math.random())), 120);
    return ()=>clearInterval(wave);
  },[]);
  const phrases = { A:{ lang:'en-US', text:'Where is the train station?' }, B:{ lang:'ja-JP', text:'駅はどこですか？' } };
  const A = { lang:'EN · English', flag:'A', color:'#5fd7ff' };
  const B = { lang:'JA · 日本語', flag:'B', color:'#7cf5d4' };
  const active = side === 'A' ? A : B;
  return (
    <div className="mini-phone">
      <div className="screen" style={{padding:'14px', display:'flex', flexDirection:'column'}}>
        <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.16em'}}>● BABEL ENGINE</div>
        <div style={{marginTop:10, display:'grid', gridTemplateColumns:'1fr 1fr', gap:8}}>
          {[A,B].map(sp => (
            <button key={sp.flag} onClick={()=>setSide(sp.flag)} style={{
              padding:10, borderRadius:12, cursor:'pointer', textAlign:'left',
              border: `1px solid ${side===sp.flag ? sp.color : 'rgba(143,185,204,.15)'}`,
              background: side===sp.flag ? 'rgba(124,245,212,.08)' : 'rgba(10,30,48,.4)',
              color:'inherit', transition:'all .3s'
            }}>
              <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:sp.color,letterSpacing:'.1em'}}>SPEAKER {sp.flag}</div>
              <div style={{fontSize:11, marginTop:4, color:'#eaf4f8'}}>{sp.lang}</div>
              <div style={{marginTop:6, width:18,height:18,borderRadius:9,
                background: side===sp.flag ? sp.color : 'rgba(143,185,204,.2)',
                display:'flex',alignItems:'center',justifyContent:'center',color:'#051320',fontSize:10}}>
                {side===sp.flag ? '●' : ''}
              </div>
            </button>
          ))}
        </div>
        <div style={{marginTop:14, padding:12, borderRadius:12, background:'rgba(10,30,48,.7)', border:`1px solid ${active.color}40`, flex:1, display:'flex', flexDirection:'column'}}>
          <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:active.color,letterSpacing:'.14em'}}>LISTENING · {active.lang}</div>
          <div style={{display:'flex',gap:2,marginTop:10,alignItems:'center',height:34}}>
            {bars.map((b,i)=>(
              <div key={i} style={{
                width:3, height: 4 + b * 28, background: active.color, borderRadius:1.5, opacity:.4 + b*.6
              }}/>
            ))}
          </div>
          <div style={{marginTop:8, fontSize:10, color:'#cde4ee', lineHeight:1.35, overflow:'hidden', maxHeight:44, display:'-webkit-box', WebkitLineClamp:3, WebkitBoxOrient:'vertical'}}>
            "{phrases[side].text}"
          </div>
          <div style={{marginTop:'auto', display:'flex', gap:8}}>
            <button onClick={()=>speak('demo-babel',phrases[side].text,{lang:phrases[side].lang})} style={{
              flex:1,padding:'8px',borderRadius:8,cursor:'pointer',border:0,
              background:'linear-gradient(135deg,#2d9cdb,#5fd7ff)',color:'#051320',
              fontFamily:'JetBrains Mono',fontSize:10,letterSpacing:'.08em',fontWeight:600
            }}>{speakingId==='demo-babel'?'❙❙ STOP':'▶ SPEAK'}</button>
            <button onClick={()=>setSide(s=>s==='A'?'B':'A')} style={{
              padding:'8px 12px',borderRadius:8,cursor:'pointer',
              background:'transparent',border:'1px solid rgba(124,245,212,.35)',
              color:'#9ef0ff',fontFamily:'JetBrains Mono',fontSize:10
            }}>⇆ flip</button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------
// DEMO 3 — Dyslexia Focus: line-focus band
// ------------------------------------------------------------
function DemoFocus(){
  const lines = [
    'The quiet river carried the small',
    'wooden boat toward the rising sun.',
    'Birds circled slowly overhead and',
    'the reeds whispered in the breeze.',
    'She opened the book and began to read.',
  ];
  const [row, setRow] = useState(0);
  const [word, setWord] = useState(0);
  const [auto, setAuto] = useState(true);
  const { speak, speakingId } = useSpeakCtx();
  useEffect(()=>{
    if(!auto) return;
    const id = setInterval(()=>{
      setWord(w => {
        const words = lines[row].split(' ');
        if (w+1 >= words.length){
          setRow(r => (r+1) % lines.length);
          return 0;
        }
        return w+1;
      });
    }, 340);
    return ()=>clearInterval(id);
  }, [row, auto]);
  const btnF = {
    width:38,height:30,borderRadius:8, cursor:'pointer',
    background:'rgba(124,245,212,.12)', border:'1px solid rgba(124,245,212,.25)',
    fontSize:10, color:'#9ef0ff', fontFamily:'JetBrains Mono'
  };
  return (
    <div className="mini-phone">
      <div className="screen" style={{padding:'14px'}}>
        <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.16em'}}>● DYSLEXIA FOCUS</div>
        <div style={{marginTop:14, padding:'14px 10px', borderRadius:12, background:'rgba(10,30,48,.55)',border:'1px solid rgba(143,185,204,.12)'}}>
          {lines.map((l, i) => {
            const active = i === row;
            const words = l.split(' ');
            return (
              <div key={i} style={{
                padding:'8px 10px', borderRadius:8, margin:'4px 0',
                background: active ? 'linear-gradient(90deg, rgba(124,245,212,.18), rgba(95,215,255,.08))' : 'transparent',
                border: active ? '1px solid rgba(124,245,212,.4)' : '1px solid transparent',
                fontSize:12, lineHeight:1.5,
                color: active ? '#eaf4f8' : 'rgba(143,185,204,.35)',
                filter: active ? 'none' : 'blur(.4px)',
                transition:'all .3s',
                fontFamily:'Fraunces, serif'
              }}>
                {words.map((w,wi)=>(
                  <span key={wi} style={{
                    background: (active && wi===word) ? '#7cf5d4' : 'transparent',
                    color: (active && wi===word) ? '#051320' : 'inherit',
                    padding: (active && wi===word) ? '1px 3px' : '1px 0',
                    borderRadius:3, marginRight:3, transition:'all .2s'
                  }}>{w}</span>
                ))}
              </div>
            );
          })}
        </div>
        <div style={{marginTop:14, display:'flex', gap:6, justifyContent:'center'}}>
          <button onClick={()=>{setAuto(false);setRow(r=>(r-1+lines.length)%lines.length);setWord(0);}} style={btnF}>◀◀</button>
          <button onClick={()=>{setAuto(false);setWord(w=>Math.max(0,w-1));}} style={btnF}>◀</button>
          <button onClick={()=>setAuto(a=>!a)} style={{...btnF, width:56, background: auto?'rgba(124,245,212,.25)':'rgba(143,185,204,.08)'}}>{auto?'❙❙ auto':'▶ auto'}</button>
          <button onClick={()=>{setAuto(false);setWord(w=>w+1);}} style={btnF}>▶</button>
          <button onClick={()=>{setAuto(false);setRow(r=>(r+1)%lines.length);setWord(0);}} style={btnF}>▶▶</button>
        </div>
        <button onClick={()=>speak('demo-focus', lines[row])} style={{
          marginTop:10, width:'100%', padding:'8px', borderRadius:8, cursor:'pointer', border:0,
          background:'linear-gradient(135deg,#2d9cdb,#5fd7ff)', color:'#051320',
          fontFamily:'JetBrains Mono',fontSize:10,letterSpacing:'.08em',fontWeight:600
        }}>{speakingId==='demo-focus'?'❙❙ STOP':'▶ READ LINE'}</button>
      </div>
    </div>
  );
}

// ------------------------------------------------------------
// DEMO 4 — AR Magic Lens: translated overlay labels
// ------------------------------------------------------------
function DemoArLens(){
  const labels = [
    { x:14, y:22, src:'CAFÉ',      tr:'COFFEE',     w:52 },
    { x:22, y:52, src:'PANADERÍA', tr:'BAKERY',     w:70 },
    { x:60, y:38, src:'ABIERTO',   tr:'OPEN',       w:54 },
    { x:50, y:72, src:'MENÚ DEL DÍA', tr:"TODAY'S MENU", w:86 },
  ];
  const [flip, setFlip] = useState(true);
  const [tapped, setTapped] = useState(null);
  const { speak, speakingId } = useSpeakCtx();
  return (
    <div className="mini-phone">
      <div className="screen" style={{padding:'14px', position:'relative'}}>
        <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.16em'}}>● AR MAGIC LENS</div>
        <div style={{marginTop:10, height:300, borderRadius:14, position:'relative', overflow:'hidden',
          background:'linear-gradient(180deg,#1a2f3a 0%, #0e1d28 60%, #061420 100%)',
          border:'1px solid rgba(95,215,255,.2)'}}>
          {/* fake storefront */}
          <div style={{position:'absolute', inset:0, background:
            'linear-gradient(180deg, rgba(255,200,114,.05) 0%, rgba(255,139,179,.04) 40%, transparent 70%)'}}/>
          <div style={{position:'absolute', left:10, top:10, right:10, bottom:120, borderRadius:8,
            background:'repeating-linear-gradient(90deg,#1b3447 0 18px,#173042 18px 36px)', opacity:.8}}/>
          <div style={{position:'absolute', left:10, bottom:14, right:10, height:100, borderRadius:8,
            background:'linear-gradient(180deg,#2a1a14,#1a100c)'}}/>
          {/* scan reticle corners */}
          {['0 0','auto 0','0 auto','auto auto'].map((p,i)=>{
            const [h,v] = p.split(' ');
            return <div key={i} style={{position:'absolute', [h==='auto'?'right':'left']:6, [v==='auto'?'bottom':'top']:6, width:12,height:12, borderColor:'#7cf5d4', borderStyle:'solid', borderWidth:0,
              borderTopWidth:v==='0'?2:0, borderBottomWidth:v==='auto'?2:0, borderLeftWidth:h==='0'?2:0, borderRightWidth:h==='auto'?2:0}}/>;
          })}
          {/* overlay labels */}
          {labels.map((l,i) => (
            <button key={i} onClick={()=>{setTapped(i); speak('demo-lens-'+i, l.tr, {lang:'en-US'});}} style={{
              position:'absolute', left:`${l.x}%`, top:`${l.y}%`,
              fontFamily:'JetBrains Mono', fontSize:10, letterSpacing:'.08em',
              padding:'4px 8px', borderRadius:6, cursor:'pointer',
              background:'rgba(5,19,32,.85)',
              color: tapped===i ? '#051320' : (flip ? '#cde4ee' : '#7cf5d4'),
              border:`1px solid ${tapped===i?'#7cf5d4':(flip? 'rgba(143,185,204,.3)':'rgba(124,245,212,.6)')}`,
              boxShadow: tapped===i ? '0 0 16px rgba(124,245,212,.7)' : (flip ? 'none' : '0 0 12px rgba(124,245,212,.35)'),
              transition:'all .3s', minWidth:l.w, textAlign:'center',
              backgroundColor: tapped===i ? '#7cf5d4' : 'rgba(5,19,32,.85)'
            }}>
              {flip ? l.src : l.tr}
            </button>
          ))}
          <div style={{position:'absolute', bottom:8, left:10, fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4'}}>
            ● 60fps · SDF · ES→EN
          </div>
          <div style={{position:'absolute', bottom:8, right:10, fontFamily:'JetBrains Mono',fontSize:9,color:'#9ef0ff'}}>
            cache: 142/200
          </div>
        </div>
        <div style={{display:'flex',gap:8,marginTop:12}}>
          <button onClick={()=>setFlip(f=>!f)} style={{
            flex:1,padding:'10px',borderRadius:10,cursor:'pointer',border:0,
            background:'linear-gradient(135deg,#2d9cdb,#5fd7ff)',color:'#051320',
            fontFamily:'JetBrains Mono',fontSize:10,letterSpacing:'.1em',fontWeight:600
          }}>{flip?'TRANSLATE →':'← ORIGINAL'}</button>
          <button onClick={()=>{const all = labels.map(l=>l.tr).join('. '); speak('demo-lens-all', all);}} style={{
            padding:'10px 12px',borderRadius:10,cursor:'pointer',
            background:'transparent',border:'1px solid rgba(124,245,212,.35)',
            color:'#9ef0ff',fontFamily:'JetBrains Mono',fontSize:10
          }}>{speakingId==='demo-lens-all'?'❙❙':'▶ read all'}</button>
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------
// DEMO 5 — E-Reader: bilingual spread + on-device summary
// ------------------------------------------------------------
function DemoReader(){
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(47);
  const { speak, speakingId } = useSpeakCtx();
  const original = "The sea was calm that morning. A thin mist rose from the water and the old fisherman hummed a tune as he mended his nets.";
  const translated = "La mer était calme ce matin-là. Une brume légère s'élevait de l'eau et le vieux pêcheur fredonnait un air en réparant ses filets.";
  const summary = [
    'Opens on a calm sea at dawn',
    'A thin mist rises from the water',
    'An old fisherman is introduced',
    'He mends his nets while humming',
    'Mood: peaceful, contemplative',
  ];
  const current = tab===0 ? original : tab===1 ? translated : summary.join('. ');
  const lang    = tab===1 ? 'fr-FR' : 'en-US';
  return (
    <div className="mini-phone">
      <div className="screen" style={{padding:'14px'}}>
        <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.16em'}}>● E-READER</div>
        <div style={{display:'flex', gap:4, marginTop:10}}>
          {['ORIGINAL','FRANÇAIS','AI SUMMARY'].map((t,i)=>(
            <button key={t} onClick={()=>setTab(i)} style={{
              flex:1, textAlign:'center', padding:'6px 4px', borderRadius:8, cursor:'pointer',
              fontFamily:'JetBrains Mono', fontSize:8, letterSpacing:'.08em',
              background: i===tab ? 'rgba(124,245,212,.16)' : 'rgba(143,185,204,.05)',
              border:`1px solid ${i===tab ? 'rgba(124,245,212,.4)' : 'rgba(143,185,204,.1)'}`,
              color: i===tab ? '#9ef0ff' : '#6aa2b5', transition:'all .3s'
            }}>{t}</button>
          ))}
        </div>
        <div style={{marginTop:12, padding:14, borderRadius:12, background:'rgba(10,30,48,.6)', border:'1px solid rgba(143,185,204,.14)', minHeight:180}}>
          {tab !== 2 ? (
            <div style={{fontFamily:'Fraunces, serif', fontSize:12, lineHeight:1.55, color:'#eaf4f8'}}>
              {tab === 0 ? original : translated}
            </div>
          ) : (
            <div>
              <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.14em',marginBottom:8}}>
                ⌬ GEMMA · ON-DEVICE
              </div>
              {summary.map((s,i)=>(
                <div key={i} style={{display:'flex',gap:8, marginBottom:6, fontSize:11, lineHeight:1.4, color:'#cde4ee'}}>
                  <span style={{color:'#7cf5d4'}}>◆</span>
                  <span>{s}</span>
                </div>
              ))}
            </div>
          )}
        </div>
        <div style={{display:'flex', justifyContent:'space-between', alignItems:'center', marginTop:12, fontFamily:'JetBrains Mono', fontSize:9, color:'#6aa2b5'}}>
          <button onClick={()=>setPage(p=>Math.max(1,p-1))} style={{background:'transparent',border:'1px solid rgba(143,185,204,.2)',color:'#9ef0ff',padding:'4px 8px',borderRadius:6,cursor:'pointer',fontFamily:'inherit',fontSize:9}}>◀ prev</button>
          <span>page {page} of 312</span>
          <button onClick={()=>setPage(p=>Math.min(312,p+1))} style={{background:'transparent',border:'1px solid rgba(143,185,204,.2)',color:'#9ef0ff',padding:'4px 8px',borderRadius:6,cursor:'pointer',fontFamily:'inherit',fontSize:9}}>next ▶</button>
        </div>
        <button onClick={()=>speak('demo-reader', current, { lang })} style={{
          marginTop:10, width:'100%', padding:'10px', borderRadius:10, cursor:'pointer', border:0,
          background:'linear-gradient(135deg,#2d9cdb,#5fd7ff)', color:'#051320',
          fontFamily:'JetBrains Mono', fontSize:10, letterSpacing:'.1em', fontWeight:600
        }}>{speakingId==='demo-reader'?'❙❙ PAUSE':'▶ READ ALOUD'}</button>
      </div>
    </div>
  );
}

// ------------------------------------------------------------
// DEMO 6 — Language model manager
// ------------------------------------------------------------
function DemoLanguages(){
  const initial = [
    { code:'EN', name:'English',  size:'42 MB', state:1 },
    { code:'ES', name:'Español',  size:'38 MB', state:1 },
    { code:'FR', name:'Français', size:'39 MB', state:1 },
    { code:'JA', name:'日本語',    size:'56 MB', state:2 },
    { code:'HI', name:'हिन्दी',     size:'48 MB', state:0 },
    { code:'AR', name:'العربية',  size:'44 MB', state:1 },
    { code:'DE', name:'Deutsch',  size:'40 MB', state:0 },
    { code:'ZH', name:'中文',      size:'52 MB', state:1 },
  ];
  const [langs, setLangs] = useState(initial);
  const [dl, setDl] = useState({});
  useEffect(()=>{
    const id = setInterval(()=>{
      setDl(prev => {
        const next = { ...prev };
        let any = false;
        for (const code of Object.keys(next)){
          next[code] = Math.min(1, next[code] + 0.04);
          if (next[code] >= 1){
            setLangs(ls => ls.map(l => l.code===code ? {...l, state:1} : l));
            delete next[code];
          } else any = true;
        }
        return next;
      });
    }, 160);
    return ()=>clearInterval(id);
  }, []);
  const toggle = (code) => {
    setLangs(ls => ls.map(l => {
      if (l.code !== code) return l;
      if (l.state === 1) return { ...l, state:0 };
      if (l.state === 0){ setDl(d => ({...d, [code]:0})); return { ...l, state:2 }; }
      return l;
    }));
  };
  const installed = langs.filter(l=>l.state===1).length;
  return (
    <div className="mini-phone">
      <div className="screen" style={{padding:'14px'}}>
        <div style={{fontFamily:'JetBrains Mono',fontSize:9,color:'#7cf5d4',letterSpacing:'.16em'}}>● LANGUAGES</div>
        <div style={{marginTop:10, padding:10, borderRadius:12, background:'rgba(10,30,48,.55)', border:'1px solid rgba(143,185,204,.12)'}}>
          <div style={{fontFamily:'JetBrains Mono',fontSize:8,color:'#9ef0ff',letterSpacing:'.14em'}}>ON DEVICE</div>
          <div style={{fontSize:18, fontWeight:500, color:'#eaf4f8'}}>{installed} <span style={{fontSize:11,color:'#6aa2b5'}}>/ 58 models</span></div>
          <div style={{marginTop:6, height:4, borderRadius:2, background:'rgba(143,185,204,.12)'}}>
            <div style={{height:'100%', width:`${(installed/58)*100}%`, borderRadius:2, background:'linear-gradient(90deg,#2d9cdb,#7cf5d4)', transition:'width .3s'}}/>
          </div>
        </div>
        <div style={{marginTop:10}}>
          {langs.map(l => (
            <button key={l.code} onClick={()=>toggle(l.code)} style={{
              display:'flex', alignItems:'center', gap:10, padding:'8px 4px', width:'100%',
              borderBottom:'1px solid rgba(143,185,204,.08)', borderTop:0, borderLeft:0, borderRight:0,
              background:'transparent', color:'inherit', cursor:'pointer', textAlign:'left'
            }}>
              <div style={{width:28,height:28,borderRadius:6, display:'flex',alignItems:'center',justifyContent:'center',
                background: l.state===1 ? 'rgba(124,245,212,.14)' : 'rgba(143,185,204,.06)',
                border:`1px solid ${l.state===1?'rgba(124,245,212,.35)':'rgba(143,185,204,.15)'}`,
                fontFamily:'JetBrains Mono', fontSize:9, color: l.state===1?'#7cf5d4':'#6aa2b5', letterSpacing:'.02em'
              }}>{l.code}</div>
              <div style={{flex:1}}>
                <div style={{fontSize:11, color:'#eaf4f8'}}>{l.name}</div>
                {l.state===2 ? (
                  <div style={{height:3, borderRadius:1.5, background:'rgba(143,185,204,.12)', marginTop:4, overflow:'hidden'}}>
                    <div style={{height:'100%', width:`${(dl[l.code]||0)*100}%`, background:'#7cf5d4', transition:'width .2s'}}/>
                  </div>
                ) : (
                  <div style={{fontFamily:'JetBrains Mono', fontSize:9, color:'#6aa2b5', marginTop:2}}>{l.size}</div>
                )}
              </div>
              <div style={{fontFamily:'JetBrains Mono', fontSize:9, color: l.state===1?'#7cf5d4':l.state===2?'#9ef0ff':'#6aa2b5'}}>
                {l.state===1?'✓ on':l.state===2?`${Math.round((dl[l.code]||0)*100)}%`:'↓ get'}
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------
// Generic feature section (with hover light)
// ------------------------------------------------------------
function Feature({ num, title, highlight, desc, tags, demo, reverse }){
  const ref = useRef(null);
  const onMove = (e) => {
    const r = ref.current.getBoundingClientRect();
    ref.current.style.setProperty('--mx', ((e.clientX-r.left)/r.width*100)+'%');
    ref.current.style.setProperty('--my', ((e.clientY-r.top)/r.height*100)+'%');
  };
  return (
    <div ref={ref} onMouseMove={onMove} className={"feat" + (reverse?' reverse':'')}>
      <div className="feat-body">
        <div className="feat-num">{num}</div>
        <h3>
          {title} <em>{highlight}</em>
        </h3>
        <p>{desc}</p>
        <div className="feat-tags">{tags.map(t => <span className="tag" key={t}>{t}</span>)}</div>
      </div>
      <div className="feat-demo">{demo}</div>
    </div>
  );
}

// ------------------------------------------------------------
// Features section (6)
// ------------------------------------------------------------
function Features(){
  return (
    <section className="pad">
      <div className="kicker">SIX MODES</div>
      <h2 className="section-h">One pipeline, <em>six lenses.</em></h2>
      <p className="section-sub">
        Every mode is fully isolated — switching modes stops all background processes
        instantly, so your battery and microphone are always in your control.
      </p>
      <div className="feat-grid">
        <Feature
          num="MODE 01 · CLASSIC TTS"
          title="Camera to speech."
          highlight="Word by word."
          desc="Point at any printed or handwritten text. Live ML Kit OCR extracts it, optional translation sits between recognition and voice, and the TTS reads aloud with word-level highlighting so you can follow along."
          tags={['ML Kit OCR', '50+ languages', 'sleep timer', 'torch', 'scan history']}
          demo={<DemoClassic/>}
        />
        <Feature
          num="MODE 02 · BABEL ENGINE"
          title="Two people, two languages,"
          highlight="one conversation."
          desc="Speaker A speaks; the mic auto-flips to Speaker B when they're done. On-device speech recognition, on-device translation, and TTS playback on the receiving side — all offline once models are downloaded."
          tags={['auto-flip mic', 'live waveform', 'offline STT', 'session history']}
          demo={<DemoBabel/>}
          reverse
        />
        <Feature
          num="MODE 03 · DYSLEXIA FOCUS"
          title="Read one line,"
          highlight="forget the rest."
          desc="A focus band isolates a single line at a time with EMA-smoothed tracking — no jitter, no jarring jumps. Paste text or scan with the camera, then step word-by-word at your own pace."
          tags={['line isolation', 'EMA smoothing', 'word stepper', 'auto-read']}
          demo={<DemoFocus/>}
        />
        <Feature
          num="MODE 04 · AR MAGIC LENS"
          title="The world,"
          highlight="in your language."
          desc="Translated text is painted directly over the real scene at 60fps, rendered with OpenGL ES 2.0 signed-distance-field glyphs so type stays crisp at any size. Spatial tracking keeps labels locked to the source words as you move."
          tags={['OpenGL SDF', '60fps overlay', 'LRU cache', 'spatial tracking']}
          demo={<DemoArLens/>}
          reverse
        />
        <Feature
          num="MODE 05 · E-READER"
          title="Bilingual library,"
          highlight="private AI summaries."
          desc="Import PDF, DOCX, DOC or TXT. Read with original and translated tabs side-by-side on every page, and tap Summarize for 4–6 bullet points generated by an on-device Gemma model — no API key, no cloud, no leak."
          tags={['PDF · DOCX · TXT', 'Gemma on-device', 'no API key', 'LRU page cache']}
          demo={<DemoReader/>}
        />
        <Feature
          num="MODE 06 · LANGUAGES"
          title="Download only what"
          highlight="you actually use."
          desc="Every mode shares the same translation model pool. Pull in only the languages you need, delete any you don't, and get a clear offline banner if you try to download without a connection."
          tags={['on-demand models', 'shared pool', 'offline detection']}
          demo={<DemoLanguages/>}
          reverse
        />
      </div>
    </section>
  );
}

// ------------------------------------------------------------
// Interactive Glyph Field (callout section)
// ------------------------------------------------------------
function GlyphCallout(){
  return (
    <section className="pad" id="languages">
      <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:60, alignItems:'center'}}>
        <div>
          <div className="kicker">THE LEXICON</div>
          <h2 className="section-h">Fifty scripts. <em>One</em> pocket.</h2>
          <p className="section-sub" style={{marginBottom:30}}>
            The nebula behind this page is alive — every floating glyph is a real
            language model that ships with OmniLingo. Click one to nudge it into a
            new orbit. They're drawn in real-time with WebGL; in the app, the same
            models translate offline on your device.
          </p>
          <div style={{display:'grid', gridTemplateColumns:'repeat(6, 1fr)', gap:10, maxWidth:440}}>
            {['语','अ','ا','ñ','ℵ','日','한','α','ß','ع','ת','Ω','音','é','ç','Ж','ई','س'].map((g,i)=>(
              <div key={i} style={{
                aspectRatio:'1', borderRadius:10, display:'flex',alignItems:'center',justifyContent:'center',
                background:'rgba(10,30,48,.4)', border:'1px solid rgba(95,215,255,.15)',
                fontSize:20, fontFamily:'Space Grotesk', color: i%3===0?'#7cf5d4':'#cde4ee',
                backdropFilter:'blur(4px)'
              }}>{g}</div>
            ))}
          </div>
        </div>
        <div style={{
          aspectRatio:'1', borderRadius:20, position:'relative', overflow:'hidden',
          border:'1px solid rgba(95,215,255,.2)',
          background:'linear-gradient(135deg, rgba(10,30,48,.4), rgba(5,17,28,.2))',
          backdropFilter:'blur(6px)',
          display:'flex', alignItems:'center', justifyContent:'center', flexDirection:'column',
          textAlign:'center', padding:40
        }}>
          <div style={{fontFamily:'JetBrains Mono', fontSize:11, color:'#7cf5d4', letterSpacing:'.2em', textTransform:'uppercase', marginBottom:20}}>try it →</div>
          <div style={{fontFamily:'Fraunces', fontStyle:'italic', fontWeight:300, fontSize:28, lineHeight:1.15, color:'#eaf4f8'}}>
            Move your mouse through the nebula. Click a drifting glyph to flip its orbit.
          </div>
          <div style={{fontFamily:'JetBrains Mono', fontSize:10, color:'#6aa2b5', letterSpacing:'.1em', marginTop:20}}>
            (the whole page is a live three.js canvas)
          </div>
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------
// Tech / architecture strip
// ------------------------------------------------------------
function Tech(){
  const rows = [
    ['LANGUAGE',    'Kotlin'],
    ['UI',          'Jetpack Compose · Material3 Glassmorphism'],
    ['CAMERA',      'CameraX'],
    ['OCR',         'ML Kit Text Recognition'],
    ['TRANSLATE',   'ML Kit on-device translator'],
    ['AI SUMMARY',  'Gemma via MediaPipe LLM Inference'],
    ['STT / TTS',   'Android SpeechRecognizer · TextToSpeech'],
    ['OVERLAY',     'OpenGL ES 2.0 · SDF glyph rendering'],
    ['STORAGE',     'Room DB · DataStore Preferences'],
    ['MIN SDK',     'API 24 · Android 7.0+'],
  ];
  return (
    <section className="pad" id="tech">
      <div className="kicker">UNDER THE HOOD</div>
      <h2 className="section-h">Engineered for <em>silence.</em></h2>
      <p className="section-sub" style={{marginBottom:50}}>
        On-device everywhere it matters. No round-trip to the cloud, no API keys,
        no usage caps. Every frame, every phrase, every summary stays on the phone.
      </p>
      <div style={{
        borderRadius:20, overflow:'hidden',
        border:'1px solid rgba(143,185,204,.14)',
        background:'linear-gradient(160deg, rgba(10,30,48,.5), rgba(5,17,28,.3))',
        backdropFilter:'blur(14px)'
      }}>
        {rows.map(([k,v],i)=>(
          <div key={k} style={{
            display:'grid', gridTemplateColumns:'200px 1fr', gap:24,
            padding:'18px 28px',
            borderBottom: i<rows.length-1 ? '1px solid rgba(143,185,204,.08)' : 'none',
            alignItems:'center'
          }}>
            <div style={{fontFamily:'JetBrains Mono', fontSize:11, letterSpacing:'.18em', color:'#7cf5d4'}}>{k}</div>
            <div style={{fontSize:15, color:'#eaf4f8'}}>{v}</div>
          </div>
        ))}
      </div>

      {/* perf strip */}
      <div style={{marginTop:40, display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:20}}>
        {[
          ['60fps', 'AR overlay'],
          ['<1s',   'mode switch'],
          ['0 kB',  'cloud traffic'],
          ['1.3 GB','on-device AI'],
        ].map(([n,l]) => (
          <div key={l} style={{
            padding:'24px 20px', borderRadius:16,
            background:'rgba(10,30,48,.4)', border:'1px solid rgba(143,185,204,.12)',
            backdropFilter:'blur(8px)'
          }}>
            <div style={{fontFamily:'Space Grotesk', fontSize:36, fontWeight:300, color:'#9ef0ff', letterSpacing:'-.02em'}}>{n}</div>
            <div style={{fontFamily:'JetBrains Mono', fontSize:11, color:'#6aa2b5', letterSpacing:'.12em', textTransform:'uppercase', marginTop:4}}>{l}</div>
          </div>
        ))}
      </div>
    </section>
  );
}

// ------------------------------------------------------------
// Privacy / CTA
// ------------------------------------------------------------
function PrivacyCTA(){
  return (
    <section className="pad" id="privacy">
      <div style={{
        padding:'80px 60px', borderRadius:28, position:'relative', overflow:'hidden',
        background:'linear-gradient(135deg, rgba(45,156,219,.15), rgba(124,245,212,.06))',
        border:'1px solid rgba(95,215,255,.25)',
        backdropFilter:'blur(18px)'
      }}>
        <div style={{position:'absolute', inset:0, background:
          'radial-gradient(80% 60% at 100% 0%, rgba(124,245,212,.15), transparent 50%)'}}/>
        <div style={{position:'relative'}}>
          <div className="kicker">PRIVACY BY DEFAULT</div>
          <h2 className="section-h" style={{maxWidth:700}}>
            Your documents <em>never</em> leave your phone.
          </h2>
          <p className="section-sub" style={{marginBottom:36}}>
            No account. No cloud sync. No analytics. Exiting any mode immediately unbinds
            the camera, stops TTS/STT, and cancels every coroutine. When OmniLingo isn't
            listening, it really isn't listening.
          </p>
          <div style={{display:'flex', gap:14, flexWrap:'wrap'}}>
            <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App" target="_blank" rel="noopener noreferrer" className="btn-primary">View Source ↗</a>
            <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App" target="_blank" rel="noopener noreferrer" className="btn-ghost">read the source ↗</a>
            <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App/blob/main/LICENSE" target="_blank" rel="noopener noreferrer" className="btn-ghost">MIT license</a>
          </div>
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------
// Footer
// ------------------------------------------------------------
function Footer(){
  return (
    <footer>
      <div className="footer-grid">
        <div className="foot-col">
          <div className="brand" style={{marginBottom:16}}>
            <div className="brand-mark" style={{width:28,height:28}}><BrandMark/></div>
            <div className="brand-name">Omni<span>Lingo</span></div>
          </div>
          <p style={{color:'#95b4c3', fontSize:13, lineHeight:1.55, maxWidth:340}}>
            Your all-in-one assistive language companion. Built with Kotlin, Jetpack
            Compose and a lot of love for people who just want to read the sign across
            the street.
          </p>
        </div>
        <div className="foot-col">
          <h5>Modes</h5>
          <a href="#modes">Classic TTS</a>
          <a href="#modes">Babel Engine</a>
          <a href="#modes">Dyslexia Focus</a>
          <a href="#modes">AR Magic Lens</a>
          <a href="#modes">E-Reader</a>
        </div>
        <div className="foot-col">
          <h5>Build</h5>
          <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App" target="_blank" rel="noopener noreferrer">Source (GitHub)</a>
          <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App/releases" target="_blank" rel="noopener noreferrer">Release notes</a>
          <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App/issues" target="_blank" rel="noopener noreferrer">Roadmap / Issues</a>
          <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App/blob/main/CONTRIBUTING.md" target="_blank" rel="noopener noreferrer">Contributing</a>
        </div>
        <div className="foot-col">
          <h5>Contact</h5>
          <a href="mailto:vishweshadla6@gmail.com">vishweshadla6@gmail.com</a>
          <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App/issues" target="_blank" rel="noopener noreferrer">Issues</a>
          <a href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App/discussions" target="_blank" rel="noopener noreferrer">Discussions</a>
        </div>
      </div>
      <div className="copyright">
        <span>© 2026 OmniLingo · MIT</span>
        <a
          href="https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App"
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display:'inline-flex', alignItems:'center', gap:8,
            padding:'10px 22px', borderRadius:999,
            background:'linear-gradient(135deg,#2d9cdb,#5fd7ff)',
            color:'#051320', fontWeight:600, fontFamily:'JetBrains Mono',
            fontSize:13, letterSpacing:'.04em', textDecoration:'none',
            boxShadow:'0 8px 28px -8px rgba(95,215,255,.55)',
            transition:'transform .2s, box-shadow .2s'
          }}
          onMouseEnter={e=>{e.currentTarget.style.transform='translateY(-2px)';e.currentTarget.style.boxShadow='0 14px 36px -8px rgba(95,215,255,.7)';}}
          onMouseLeave={e=>{e.currentTarget.style.transform='';e.currentTarget.style.boxShadow='0 8px 28px -8px rgba(95,215,255,.55)';}}
        >
          ⌥ Build it yourself ↗
        </a>
        <span>built offline, for offline</span>
      </div>
    </footer>
  );
}

// ------------------------------------------------------------
// Root
// ------------------------------------------------------------
function App(){
  return (
    <SpeakProvider>
      <TopBar/>
      <Hero/>
      <Pipeline/>
      <Features/>
      <GlyphCallout/>
      <Tech/>
      <PrivacyCTA/>
      <Footer/>
    </SpeakProvider>
  );
}

const root = ReactDOM.createRoot(document.getElementById('page'));
root.render(<App/>);
