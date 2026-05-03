// Animated nebula background + floating interactive language glyphs.
// Runs in a fixed full-viewport canvas. Uses raw Three.js (not R3F) so the
// background layer stays independent of React's render cycle and always
// covers every section.
import * as THREE from 'three';

const container = document.getElementById('bg3d');

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(55, window.innerWidth/window.innerHeight, 0.1, 100);
camera.position.set(0, 0, 6);

const renderer = new THREE.WebGLRenderer({ antialias:true, alpha:true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setClearColor(0x05111c, 1);
container.appendChild(renderer.domElement);

// ---------- NEBULA SHADER PLANE ----------
// Flowing FBM noise in teal/cyan/aurora tones, cubic-bezier S-curve time warp
// (matches the app's described aurora).
const nebulaGeom = new THREE.PlaneGeometry(40, 22, 1, 1);
const nebulaMat = new THREE.ShaderMaterial({
  uniforms: {
    uTime: { value: 0 },
    uMouse: { value: new THREE.Vector2(0.5, 0.5) },
    uRes: { value: new THREE.Vector2(window.innerWidth, window.innerHeight) }
  },
  transparent: true,
  depthWrite: false,
  vertexShader: `
    varying vec2 vUv;
    void main(){
      vUv = uv;
      gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
  `,
  fragmentShader: `
    precision highp float;
    varying vec2 vUv;
    uniform float uTime;
    uniform vec2 uMouse;
    uniform vec2 uRes;

    // hash / noise / fbm
    float hash(vec2 p){ p = fract(p*vec2(123.34, 345.45)); p += dot(p, p+34.345); return fract(p.x*p.y); }
    float noise(vec2 p){
      vec2 i = floor(p); vec2 f = fract(p);
      vec2 u = f*f*(3.0-2.0*f);
      float a = hash(i);
      float b = hash(i+vec2(1.0,0.0));
      float c = hash(i+vec2(0.0,1.0));
      float d = hash(i+vec2(1.0,1.0));
      return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
    }
    float fbm(vec2 p){
      float v = 0.0; float a = 0.5; mat2 m = mat2(1.6,1.2,-1.2,1.6);
      for(int i=0;i<6;i++){ v += a*noise(p); p = m*p; a *= 0.5; }
      return v;
    }

    // cubic bezier S-curve so time doesn't feel uniform
    float sEase(float t){
      t = fract(t);
      return t*t*(3.0-2.0*t);
    }

    void main(){
      vec2 uv = vUv;
      // aspect correct
      vec2 p = (uv - 0.5) * vec2(uRes.x/uRes.y, 1.0) * 2.2;

      float tRaw = uTime / 14.0;        // 14-second cycle like the app
      float t = sEase(tRaw) * 14.0;

      // layered flow fields
      vec2 q;
      q.x = fbm(p + vec2(0.0, t*0.15));
      q.y = fbm(p + vec2(5.2, -t*0.12));
      vec2 r;
      r.x = fbm(p + 2.0*q + vec2(1.7, 9.2) + t*0.10);
      r.y = fbm(p + 2.0*q + vec2(8.3, 2.8) + t*0.09);

      float f = fbm(p + 2.0*r);
      float f2 = fbm(p*1.7 - r + t*0.04);

      // palette (teal / cyan / aurora over deep navy)
      vec3 navy  = vec3(0.020, 0.067, 0.110);
      vec3 deep  = vec3(0.039, 0.141, 0.216);
      vec3 teal  = vec3(0.102, 0.420, 0.588);  // #1a6b96
      vec3 cyan  = vec3(0.176, 0.612, 0.859);  // #2d9cdb
      vec3 aur   = vec3(0.486, 0.961, 0.831);  // #7cf5d4
      vec3 ice   = vec3(0.620, 0.941, 1.000);

      vec3 col = mix(navy, deep, smoothstep(0.0, 0.45, f));
      col = mix(col, teal, smoothstep(0.30, 0.75, f));
      col = mix(col, cyan, smoothstep(0.55, 0.90, f2));
      col = mix(col, aur,  smoothstep(0.80, 0.98, f2) * 0.55);

      // rim glow toward upper right
      float rim = smoothstep(1.1, 0.2, length(p - vec2(0.8, 0.6)));
      col += cyan * rim * 0.12;

      // mouse reactive soft highlight
      vec2 m = (uMouse - 0.5) * vec2(uRes.x/uRes.y, 1.0) * 2.2;
      float md = length(p - m);
      col += ice * smoothstep(1.2, 0.0, md) * 0.05;

      // subtle scan grain (animated)
      float grain = (hash(p*800.0 + t) - 0.5) * 0.04;
      col += grain;

      // vignette
      float vig = smoothstep(1.6, 0.2, length(p));
      col *= vig * 0.95 + 0.15;

      gl_FragColor = vec4(col, 1.0);
    }
  `
});
const nebula = new THREE.Mesh(nebulaGeom, nebulaMat);
nebula.position.z = -4;
scene.add(nebula);

// ---------- FLOATING GLYPHS (interactive) ----------
// Each glyph is a unicode character rendered to a canvas, mapped onto a
// billboarded plane. They drift in a torus-shaped swarm; hovering highlights
// and on click they burst to a new orbit.
const GLYPHS = ['语','अ','ا','ñ','ℵ','日','한','α','ß','ع','ת','Ω','音','é','ç','Ж','ई','س','ك','龙','文','音','言','星'];

function glyphTexture(ch, tint = '#9ef0ff'){
  const s = 256;
  const cv = document.createElement('canvas'); cv.width = cv.height = s;
  const ctx = cv.getContext('2d');
  ctx.clearRect(0,0,s,s);
  // soft glow
  const g = ctx.createRadialGradient(s/2, s/2, 10, s/2, s/2, s/2);
  g.addColorStop(0, 'rgba(95,215,255,0.22)');
  g.addColorStop(1, 'rgba(95,215,255,0)');
  ctx.fillStyle = g;
  ctx.beginPath(); ctx.arc(s/2, s/2, s/2, 0, Math.PI*2); ctx.fill();
  // glyph
  ctx.font = '500 150px "Space Grotesk", system-ui, sans-serif';
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.fillStyle = tint;
  ctx.shadowColor = 'rgba(124,245,212,0.8)';
  ctx.shadowBlur = 24;
  ctx.fillText(ch, s/2, s/2 + 6);
  const tx = new THREE.CanvasTexture(cv);
  tx.anisotropy = 4;
  return tx;
}

const glyphGroup = new THREE.Group();
scene.add(glyphGroup);

const glyphMeshes = [];
const GLYPH_COUNT = 22;
for (let i=0; i<GLYPH_COUNT; i++){
  const ch = GLYPHS[i % GLYPHS.length];
  const tint = i % 4 === 0 ? '#7cf5d4' : (i % 5 === 0 ? '#5fd7ff' : '#cde4ee');
  const tex = glyphTexture(ch, tint);
  const mat = new THREE.MeshBasicMaterial({ map: tex, transparent:true, depthWrite:false });
  const size = 0.6 + Math.random()*0.5;
  const g = new THREE.PlaneGeometry(size, size);
  const m = new THREE.Mesh(g, mat);

  // orbit params — tight so glyphs stay on-screen
  const radius = 1.8 + Math.random()*1.4;
  const theta  = Math.random() * Math.PI * 2;
  const yAmp   = (Math.random()-0.5) * 1.2;
  const speed  = 0.04 + Math.random()*0.07;
  const baseZ  = -0.8 - Math.random()*1.4;
  const baseSize = 0.5 + Math.random()*0.3;

  m.userData = { radius, theta, yAmp, speed, baseZ, hover:0, burst:0, baseSize };
  glyphGroup.add(m);
  glyphMeshes.push(m);
}

// ---------- INTERACTION ----------
const ray = new THREE.Raycaster();
const pointer = new THREE.Vector2(-2, -2);
let mouseU = new THREE.Vector2(0.5, 0.5);

window.addEventListener('click', (e) => {
  ray.setFromCamera(pointer, camera);
  const hits = ray.intersectObjects(glyphMeshes);
  if (hits[0]){
    const m = hits[0].object;
    m.userData.burst = 1.0;
    m.userData.speed *= -1; // reverse orbit direction
  }
});

// ---------- SCROLL PARALLAX ----------
let scrollY = 0;
window.addEventListener('scroll', () => { scrollY = window.scrollY; }, { passive:true });

// ---------- RESIZE ----------
window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
  nebulaMat.uniforms.uRes.value.set(window.innerWidth, window.innerHeight);
});

// ---------- RENDER LOOP ----------
const clock = new THREE.Clock();
let animId = null;
let paused = false;

// Pause rendering when tab is hidden — saves CPU/GPU on server load
document.addEventListener('visibilitychange', () => {
  paused = document.hidden;
  if (!paused && !animId) tick();
});

// Throttle pointer updates to 1 per animation frame
let pointerDirty = false;
let _px = 0, _py = 0, _mu = { x: 0.5, y: 0.5 };
window.addEventListener('pointermove', (e) => {
  _px = (e.clientX / window.innerWidth) * 2 - 1;
  _py = -(e.clientY / window.innerHeight) * 2 + 1;
  _mu.x = e.clientX / window.innerWidth;
  _mu.y = 1 - e.clientY / window.innerHeight;
  pointerDirty = true;
}, { passive: true });

function tick(){
  if (paused){ animId = null; return; }
  animId = requestAnimationFrame(tick);

  // apply throttled pointer state
  if (pointerDirty){
    pointer.x = _px; pointer.y = _py;
    mouseU.x = _mu.x; mouseU.y = _mu.y;
    pointerDirty = false;
  }
  const t = clock.getElapsedTime();
  nebulaMat.uniforms.uTime.value = t;
  nebulaMat.uniforms.uMouse.value.lerp(mouseU, 0.08);

  // camera drifts with scroll (parallax illusion)
  const scrollNorm = Math.min(scrollY / 4000, 1);
  camera.position.y = THREE.MathUtils.lerp(camera.position.y, -scrollNorm * 1.5 + mouseU.y*0.15 - 0.07, 0.06);
  camera.position.x = THREE.MathUtils.lerp(camera.position.x, (mouseU.x - 0.5) * 0.6, 0.06);
  camera.lookAt(0, camera.position.y * 0.5, 0);

  // raycast for hover
  ray.setFromCamera(pointer, camera);
  const hits = ray.intersectObjects(glyphMeshes);
  const hoverId = hits[0] ? hits[0].object.id : -1;

  // animate glyphs
  for (const m of glyphMeshes){
    const ud = m.userData;
    ud.theta += ud.speed * 0.015;
    // no burst radius expansion — just a tiny pulse
    const r = ud.radius;
    m.position.x = THREE.MathUtils.clamp(Math.cos(ud.theta) * r, -5.5, 5.5);
    m.position.y = THREE.MathUtils.clamp(Math.sin(ud.theta * 0.6) * ud.yAmp + Math.sin(t * 0.3 + ud.theta) * 0.12, -3, 3);
    m.position.z = THREE.MathUtils.clamp(ud.baseZ + Math.sin(ud.theta * 1.1) * 0.5, -3, 0);

    // billboard
    m.lookAt(camera.position);

    // hover / burst visuals
    const isHover = m.id === hoverId;
    ud.hover = THREE.MathUtils.lerp(ud.hover, isHover ? 1 : 0, 0.15);
    ud.burst = THREE.MathUtils.lerp(ud.burst, 0, 0.04);
    // scale stays close to base — max 1.25× on hover
    const s = 1 + ud.hover * 0.25;
    m.scale.set(s, s, s);
    ud.burst = THREE.MathUtils.lerp(ud.burst, 0, 0.08); // drain fast
    m.material.opacity = 0.45 + ud.hover * 0.45;
  }

  // slow group rotation
  glyphGroup.rotation.y = t * 0.02;
  glyphGroup.rotation.x = Math.sin(t*0.07) * 0.08;

  document.body.style.cursor = hoverId >= 0 ? 'pointer' : '';

  renderer.render(scene, camera);
}
animId = requestAnimationFrame(tick);
