/* Docverse Web: shared helpers (DOM builder, UI widgets, image and file utilities). */
(function () {
  'use strict';
  const App = (window.App = { tools: [], ui: {}, util: {} });
  const U = App.util, UI = App.ui;

  // ------------------------------------------------------------ DOM builder
  function h(tag, attrs) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else if (k in el && k !== 'list') el[k] = v;
      else el.setAttribute(k, v === true ? '' : v);
    }
    const kids = Array.prototype.slice.call(arguments, 2).flat(Infinity);
    for (const kid of kids) {
      if (kid == null || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return el;
  }
  App.h = h;

  // ------------------------------------------------------------ utilities
  U.fmtSize = (b) => b < 1024 ? b + ' B' : b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(2) + ' MB';
  U.safeName = (s, fallback) => (String(s || '').replace(/\.[^.]+$/, '').replace(/[^\w .-]+/g, '_').trim().slice(0, 60)) || fallback || 'file';
  U.stamp = () => { const d = new Date(), p = (n) => String(n).padStart(2, '0'); return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`; };
  U.sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  /** "1-3, 5, 8-10" -> [0,1,2,4,7,8,9] (zero based, in order, no duplicates) */
  U.parseRanges = function (text, n) {
    const out = [], seen = new Set();
    String(text || '').split(/[\s,;]+/).filter(Boolean).forEach((part) => {
      const m = part.match(/^(\d+)(?:-(\d+))?$/);
      if (!m) return;
      let a = +m[1], b = m[2] ? +m[2] : a;
      if (a > b) [a, b] = [b, a];
      for (let p = Math.max(1, a); p <= Math.min(n, b); p++) if (!seen.has(p)) { seen.add(p); out.push(p - 1); }
    });
    return out;
  };

  U.toBlob = (canvas, type, q) => new Promise((res, rej) => canvas.toBlob((b) => b ? res(b) : rej(new Error('Could not encode image')), type, q));
  U.download = function (blob, name) {
    const a = h('a', { href: URL.createObjectURL(blob), download: name });
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 30000);
  };
  U.zip = async function (files) {
    const z = new JSZip();
    files.forEach((f) => z.file(f.name, f.blob));
    return z.generateAsync({ type: 'blob', compression: 'DEFLATE' });
  };

  // ------------------------------------------------------------ canvas helpers
  U.newCanvas = (w, hh) => { const c = document.createElement('canvas'); c.width = Math.max(1, Math.round(w)); c.height = Math.max(1, Math.round(hh)); return c; };
  U.scaleCanvas = function (src, w, hh) {
    const c = U.newCanvas(w, hh), x = c.getContext('2d');
    x.imageSmoothingQuality = 'high'; x.drawImage(src, 0, 0, c.width, c.height); return c;
  };
  U.fitMax = function (src, maxDim) {
    const m = Math.max(src.width, src.height);
    if (m <= maxDim) return src;
    const r = maxDim / m; return U.scaleCanvas(src, src.width * r, src.height * r);
  };
  U.rotateCanvas = function (src, deg) {
    const d = ((deg % 360) + 360) % 360;
    if (d === 0) return src;
    const swap = d === 90 || d === 270;
    const c = U.newCanvas(swap ? src.height : src.width, swap ? src.width : src.height), x = c.getContext('2d');
    x.translate(c.width / 2, c.height / 2); x.rotate(d * Math.PI / 180);
    x.drawImage(src, -src.width / 2, -src.height / 2); return c;
  };
  /** Crop with fractions 0..1 */
  U.cropCanvas = function (src, l, t, r, b) {
    const x0 = Math.round(src.width * l), y0 = Math.round(src.height * t);
    const w = Math.max(1, Math.round(src.width * r) - x0), hh = Math.max(1, Math.round(src.height * b) - y0);
    const c = U.newCanvas(w, hh); c.getContext('2d').drawImage(src, x0, y0, w, hh, 0, 0, w, hh); return c;
  };

  /** Decode an image file into a canvas (EXIF rotation applied), longest side at most maxDim. */
  U.loadImage = async function (file, maxDim) {
    let bmp;
    try { bmp = await createImageBitmap(file, { imageOrientation: 'from-image' }); }
    catch (e) {
      bmp = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => rej(new Error('Cannot read image "' + file.name + '"')); i.src = URL.createObjectURL(file); });
    }
    const w = bmp.width || bmp.naturalWidth, hh = bmp.height || bmp.naturalHeight;
    const r = maxDim ? Math.min(1, maxDim / Math.max(w, hh)) : 1;
    const c = U.newCanvas(w * r, hh * r), x = c.getContext('2d');
    x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height); // JPEG has no transparency
    x.imageSmoothingQuality = 'high'; x.drawImage(bmp, 0, 0, c.width, c.height);
    if (bmp.close) bmp.close();
    return c;
  };

  // ------------------------------------------------------------ filters (same recipes as the Android app)
  U.FILTERS = [
    { v: 'original', l: 'Original' }, { v: 'magic', l: 'Auto color' }, { v: 'noshadow', l: 'No shadow' },
    { v: 'gray', l: 'Grayscale' }, { v: 'bw', l: 'Black & white' }, { v: 'white', l: 'Whiteboard' }
  ];
  /** Returns a new canvas with filter, brightness (-100..100) and contrast (0.5..2) applied. */
  U.process = function (src, o, maxDim) {
    const base = maxDim ? U.fitMax(src, maxDim) : src;
    const filter = (o && o.filter) || 'original', br = (o && o.br) || 0, ct = (o && o.ct) || 1;
    if (filter === 'original' && br === 0 && ct === 1) return base;
    const c = U.newCanvas(base.width, base.height), x = c.getContext('2d');
    x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height); x.drawImage(base, 0, 0);
    const img = x.getImageData(0, 0, c.width, c.height), d = img.data;
    const off = br * 2.55 + 128 * (1 - ct);
    const cl = (v) => v < 0 ? 0 : v > 255 ? 255 : v;
    for (let i = 0; i < d.length; i += 4) {
      let r = d[i], g = d[i + 1], b = d[i + 2];
      switch (filter) {
        case 'magic': r = r * 1.35 - 25; g = g * 1.35 - 25; b = b * 1.35 - 25; break;
        case 'noshadow': r = r * 1.15 + 35; g = g * 1.15 + 35; b = b * 1.15 + 35; break;
        case 'gray': { const y = 0.213 * r + 0.715 * g + 0.072 * b; r = g = b = y; break; }
        case 'bw': { const y = 0.213 * r + 0.715 * g + 0.072 * b, v = y * 6 - 620; r = g = b = v; break; }
        case 'white': {
          const y = 0.213 * r + 0.715 * g + 0.072 * b;
          r = y + (r - y) * 1.3; g = y + (g - y) * 1.3; b = y + (b - y) * 1.3;
          r = r * 1.6 - 30; g = g * 1.6 - 30; b = b * 1.6 - 30; break;
        }
      }
      if (ct !== 1 || br !== 0) { r = r * ct + off; g = g * ct + off; b = b * ct + off; }
      d[i] = cl(r); d[i + 1] = cl(g); d[i + 2] = cl(b);
    }
    x.putImageData(img, 0, 0); return c;
  };

  // ------------------------------------------------------------ UI widgets
  UI.toast = function (msg) {
    const t = document.getElementById('toast'); t.textContent = msg; t.classList.add('show');
    clearTimeout(UI._tt); UI._tt = setTimeout(() => t.classList.remove('show'), 2200);
  };

  UI.card = (title, ...kids) => h('div', { class: 'card' }, title ? h('h3', {}, title) : null, ...kids);
  UI.btn = (text, onclick, cls) => h('button', { class: 'btn ' + (cls || ''), type: 'button', onclick }, text);

  UI.drop = function (o) {
    const input = h('input', { type: 'file', accept: o.accept || '*/*', multiple: !!o.multiple });
    const el = h('label', { class: 'drop' }, h('b', {}, o.title || 'Choose files'), h('span', {}, o.hint || 'or drop them here'), input);
    const give = (files) => { files = Array.from(files || []); if (files.length) o.onFiles(o.multiple ? files : [files[0]]); };
    input.addEventListener('change', () => { give(input.files); input.value = ''; });
    ['dragenter', 'dragover'].forEach((ev) => el.addEventListener(ev, (e) => { e.preventDefault(); el.classList.add('over'); }));
    ['dragleave', 'drop'].forEach((ev) => el.addEventListener(ev, (e) => { e.preventDefault(); el.classList.remove('over'); }));
    el.addEventListener('drop', (e) => give(e.dataTransfer.files));
    return el;
  };

  UI.chips = function (options, value, onChange) {
    const el = h('div', { class: 'chips' });
    const btns = options.map((o) => h('button', { type: 'button', class: 'chip', onclick: () => set(o.v, true) }, o.l));
    function set(v, fire) { el.value = v; btns.forEach((b, i) => b.classList.toggle('on', options[i].v === v)); if (fire && onChange) onChange(v); }
    btns.forEach((b) => el.append(b)); set(value, false);
    el.set = (v) => set(v, false);
    return el;
  };

  UI.slider = function (label, min, max, step, value, fmt, onChange) {
    const out = h('b', {}, fmt(value));
    const input = h('input', { type: 'range', min, max, step, value });
    input.addEventListener('input', () => { out.textContent = fmt(+input.value); if (onChange) onChange(+input.value); });
    const el = h('div', { class: 'sl' }, h('div', {}, h('span', {}, label), out), input);
    el.get = () => +input.value;
    el.set = (v) => { input.value = v; out.textContent = fmt(v); };
    return el;
  };

  UI.field = (label, input) => h('label', { class: 'f' }, label, input);
  UI.text = (value, placeholder) => h('input', { type: 'text', value: value || '', placeholder: placeholder || '' });

  /** Status line with an optional progress bar. */
  UI.status = function () {
    const msg = h('span', {}), fill = h('i'), bar = h('div', { class: 'bar', style: { display: 'none' } }, fill);
    const el = h('div', { class: 'status' }, msg, bar);
    el.busy = (m, frac) => { el.classList.remove('err'); msg.textContent = m; bar.style.display = frac == null ? 'none' : 'block'; if (frac != null) fill.style.width = Math.round(frac * 100) + '%'; };
    el.error = (m) => { el.classList.add('err'); msg.textContent = m; bar.style.display = 'none'; };
    el.clear = () => { el.classList.remove('err'); msg.textContent = ''; bar.style.display = 'none'; };
    return el;
  };

  /** Run async work with status handling. */
  UI.run = async function (status, work, btn) {
    if (btn) btn.disabled = true;
    try { status.busy('Working…'); await work(); }
    catch (e) { console.error(e); status.error(e && e.message ? e.message : String(e)); return false; }
    finally { if (btn) btn.disabled = false; }
    return true;
  };

  /** Card with download buttons for a list of {name, blob}. */
  UI.results = function (files, note) {
    const el = h('div', { class: 'res' }, h('h4', {}, files.length === 1 ? 'Done! Your file is ready' : 'Done! ' + files.length + ' files are ready'));
    files.slice(0, 40).forEach((f) => {
      const isImg = /^image\//.test(f.blob.type);
      el.append(h('div', { class: 'item' },
        isImg ? h('img', { src: URL.createObjectURL(f.blob), alt: '' }) : null,
        h('div', { class: 'nm' }, f.name, h('small', {}, U.fmtSize(f.blob.size))),
        UI.btn('Download', () => U.download(f.blob, f.name), 'small')));
    });
    if (files.length > 40) el.append(h('p', { class: 'note' }, '…and ' + (files.length - 40) + ' more in the ZIP.'));
    if (files.length > 1) el.append(h('div', { style: { marginTop: '10px' } }, UI.btn('Download all as ZIP', async () => U.download(await U.zip(files), 'docverse_' + U.stamp() + '.zip'))));
    if (note) el.append(h('p', { class: 'note' }, note));
    return el;
  };

  /** Re-orderable list of items. items is an array that is changed in place. */
  UI.orderList = function (items, label, thumb, onChange) {
    const el = h('div', { class: 'list' });
    function draw() {
      el.replaceChildren(...items.map((it, i) => {
        const t = thumb ? thumb(it) : null;
        return h('div', { class: 'item' }, t, h('div', { class: 'nm' }, (i + 1) + '. ' + label(it)),
          h('button', { class: 'ib', type: 'button', title: 'Move up', disabled: i === 0, onclick: () => { [items[i - 1], items[i]] = [items[i], items[i - 1]]; draw(); onChange && onChange(); } }, '↑'),
          h('button', { class: 'ib', type: 'button', title: 'Move down', disabled: i === items.length - 1, onclick: () => { [items[i + 1], items[i]] = [items[i], items[i + 1]]; draw(); onChange && onChange(); } }, '↓'),
          h('button', { class: 'ib', type: 'button', title: 'Remove', onclick: () => { items.splice(i, 1); draw(); onChange && onChange(); } }, '✕'));
      }));
    }
    el.refresh = draw; draw(); return el;
  };

  /** Page layout shared by every tool: heading, description, body. */
  UI.page = (title, desc) => { const body = h('div'); return { el: h('div', {}, h('h1', {}, title), h('p', { class: 'desc' }, desc), body), body }; };
})();
