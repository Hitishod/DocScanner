/* Scan & edit: bring in photos (upload or webcam), crop, filter, rotate, then export as PDF or images. */
(function () {
  'use strict';
  const { h } = App, U = App.util, UI = App.ui, P = App.pdf;

  App.tools.push({
    id: 'scan', group: 'Scan', icon: '📷', title: 'Scan & edit',
    desc: 'Take photos with your webcam or upload them, crop each page, clean it up with filters, then save as PDF or images.',
    render(body) {
      const pages = []; // {src: canvas, filter, br, ct}
      let sel = -1, cropping = false, stream = null, raf = 0;
      let crop = { l: 0.04, t: 0.04, r: 0.96, b: 0.96 };
      let outFmt = 'pdf', size = 'a4';
      const status = UI.status(), out = h('div');

      // ------------------------------------------------ input: upload + webcam
      const camBox = h('div', { style: { display: 'none' } });
      const video = h('video', { autoplay: true, playsInline: true, muted: true });
      const stopCam = () => { if (stream) stream.getTracks().forEach((t) => t.stop()); stream = null; camBox.style.display = 'none'; };
      camBox.append(video, h('div', { class: 'row', style: { marginTop: '10px' } },
        UI.btn('📸 Capture page', () => {
          if (!video.videoWidth) return;
          const c = U.newCanvas(video.videoWidth, video.videoHeight);
          c.getContext('2d').drawImage(video, 0, 0); addPage(c); UI.toast('Page ' + pages.length + ' added');
        }), UI.btn('Done', stopCam, 'ghost')));
      const camBtn = UI.btn('📷 Use webcam', async () => {
        try {
          stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment', width: { ideal: 1920 } } });
          video.srcObject = stream; camBox.style.display = 'block'; await video.play();
        } catch (e) { status.error('Could not open the camera: ' + (e.message || e) + '. Allow camera access in your browser, or upload photos instead.'); }
      }, 'ghost');
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) camBtn.style.display = 'none';

      function addPage(canvas) {
        pages.push({ src: U.fitMax(canvas, 3000), filter: 'magic', br: 0, ct: 1 });
        select(pages.length - 1);
      }
      const drop = UI.drop({ accept: 'image/*', multiple: true, title: 'Upload photos', hint: 'Photos of documents, JPG or PNG. Or drop them here.',
        onFiles: (fs) => UI.run(status, async () => {
          for (let i = 0; i < fs.length; i++) { status.busy('Loading ' + fs[i].name, i / fs.length); addPage(await U.loadImage(fs[i], 3000)); await U.sleep(0); }
          status.clear();
        }) });

      // ------------------------------------------------ editor
      const strip = h('div', { class: 'grid2' });
      const view = h('canvas'), stage = h('div', { class: 'stage' }, view);
      const editor = h('div', { style: { display: 'none' } });
      const filterChips = UI.chips(U.FILTERS, 'magic', (v) => { cur().filter = v; refresh(); });
      const br = UI.slider('Brightness', -100, 100, 1, 0, (v) => String(v), (v) => { cur().br = v; refresh(true); });
      const ct = UI.slider('Contrast', 0.5, 2, 0.05, 1, (v) => Math.round(v * 100) + '%', (v) => { cur().ct = v; refresh(true); });
      const cur = () => pages[sel];

      function drawView() {
        const p = cur(); if (!p) return;
        if (cropping) {
          const c = U.fitMax(p.src, 800); view.width = c.width; view.height = c.height;
          const x = view.getContext('2d'); x.drawImage(c, 0, 0);
          const L = crop.l * c.width, T = crop.t * c.height, R = crop.r * c.width, B = crop.b * c.height;
          x.fillStyle = 'rgba(0,0,0,.55)';
          x.fillRect(0, 0, c.width, T); x.fillRect(0, B, c.width, c.height - B); x.fillRect(0, T, L, B - T); x.fillRect(R, T, c.width - R, B - T);
          x.strokeStyle = '#2D5BFF'; x.lineWidth = 3; x.strokeRect(L, T, R - L, B - T);
          [[L, T], [R, T], [L, B], [R, B]].forEach(([px, py]) => { x.fillStyle = '#fff'; x.beginPath(); x.arc(px, py, 11, 0, 7); x.fill(); x.fillStyle = '#2D5BFF'; x.beginPath(); x.arc(px, py, 7.5, 0, 7); x.fill(); });
        } else {
          const c = U.process(p.src, p, 900); view.width = c.width; view.height = c.height; view.getContext('2d').drawImage(c, 0, 0);
        }
      }
      function drawStrip() {
        strip.replaceChildren(...pages.map((p, i) => {
          const c = U.process(p.src, p, 150);
          const t = h('div', { class: 'thumb' + (i === sel ? ' sel' : ''), style: { cursor: 'pointer' }, onclick: () => select(i) }, U.scaleCanvas(c, c.width, c.height), h('div', {}, 'Page ' + (i + 1)));
          return t;
        }));
      }
      let pend = 0;
      function refresh(lite) { cancelAnimationFrame(pend); pend = requestAnimationFrame(() => { drawView(); if (!lite) drawStrip(); }); if (lite) { clearTimeout(refresh.t); refresh.t = setTimeout(drawStrip, 250); } }
      function select(i) {
        sel = i; cropping = false; editor.style.display = pages.length ? 'block' : 'none'; cropBar.style.display = 'none'; normalBar.style.display = 'flex';
        if (pages[i]) { filterChips.set(pages[i].filter); br.set(pages[i].br); ct.set(pages[i].ct); }
        out.replaceChildren(); refresh();
      }

      // crop interaction
      let drag = null;
      const pos = (e) => { const r = view.getBoundingClientRect(); return { x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height, r }; };
      const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
      view.addEventListener('pointerdown', (e) => {
        if (!cropping) return; const { x, y, r } = pos(e);
        const cs = [['l', 't'], ['r', 't'], ['l', 'b'], ['r', 'b']]; let best = 0, bd = 1e9;
        cs.forEach((k, i) => { const d = Math.hypot((x - crop[k[0]]) * r.width, (y - crop[k[1]]) * r.height); if (d < bd) { bd = d; best = i; } });
        drag = bd < 40 ? { corner: cs[best] } : { x, y, c: Object.assign({}, crop) };
        view.setPointerCapture(e.pointerId); e.preventDefault();
      });
      view.addEventListener('pointermove', (e) => {
        if (!drag) return; const { x, y } = pos(e), M = 0.08;
        if (drag.corner) {
          const [kx, ky] = drag.corner;
          crop[kx] = kx === 'l' ? clamp(x, 0, crop.r - M) : clamp(x, crop.l + M, 1);
          crop[ky] = ky === 't' ? clamp(y, 0, crop.b - M) : clamp(y, crop.t + M, 1);
        } else {
          const c = drag.c, dx = clamp(x - drag.x, -c.l, 1 - c.r), dy = clamp(y - drag.y, -c.t, 1 - c.b);
          crop = { l: c.l + dx, t: c.t + dy, r: c.r + dx, b: c.b + dy };
        }
        drawView();
      });
      ['pointerup', 'pointercancel'].forEach((ev) => view.addEventListener(ev, () => { drag = null; }));

      const normalBar = h('div', { class: 'row', style: { marginTop: '12px' } },
        UI.btn('✂ Crop', () => { cropping = true; crop = { l: 0.04, t: 0.04, r: 0.96, b: 0.96 }; normalBar.style.display = 'none'; cropBar.style.display = 'flex'; drawView(); }, 'ghost small'),
        UI.btn('↺ Rotate left', () => { cur().src = U.rotateCanvas(cur().src, 270); refresh(); }, 'ghost small'),
        UI.btn('↻ Rotate right', () => { cur().src = U.rotateCanvas(cur().src, 90); refresh(); }, 'ghost small'),
        UI.btn('🗑 Delete page', () => { pages.splice(sel, 1); select(Math.min(sel, pages.length - 1)); }, 'ghost small'));
      const cropBar = h('div', { class: 'row', style: { marginTop: '12px', display: 'none' } },
        UI.btn('Apply crop', () => { cur().src = U.cropCanvas(cur().src, crop.l, crop.t, crop.r, crop.b); cropping = false; cropBar.style.display = 'none'; normalBar.style.display = 'flex'; refresh(); }, 'small'),
        UI.btn('Cancel', () => { cropping = false; cropBar.style.display = 'none'; normalBar.style.display = 'flex'; drawView(); }, 'ghost small'),
        h('span', { class: 'note' }, 'Drag the corners, or drag inside the box to move it.'));

      editor.append(
        UI.card('Edit page', stage, normalBar, cropBar,
          h('div', { style: { marginTop: '14px' } }, h('p', { class: 'note' }, 'Look'), filterChips,
            h('div', { class: 'row', style: { marginTop: '8px' } }, UI.btn('Use this look on all pages', () => { const p = cur(); pages.forEach((q) => { q.filter = p.filter; q.br = p.br; q.ct = p.ct; }); refresh(); UI.toast('Applied to all pages'); }, 'ghost small'))),
          h('div', { class: 'row gap', style: { marginTop: '12px' } }, br, ct)));

      // ------------------------------------------------ export
      const name = UI.text('', 'Optional, for example Receipt');
      const quality = UI.slider('Quality (lower = smaller file)', 30, 100, 1, 85, (v) => v + '%');
      const fmtChips = UI.chips([{ v: 'pdf', l: 'PDF' }, { v: 'jpg', l: 'JPG images' }, { v: 'png', l: 'PNG images' }], outFmt, (v) => { outFmt = v; sizeBox.style.display = v === 'pdf' ? 'block' : 'none'; });
      const sizeChips = UI.chips([{ v: 'a4', l: 'A4' }, { v: 'letter', l: 'Letter' }, { v: 'fit', l: 'Fit to image' }], size, (v) => (size = v));
      const sizeBox = h('div', {}, h('p', { class: 'note' }, 'Page size'), sizeChips);
      const exp = UI.btn('Export', () => UI.run(status, async () => {
        if (!pages.length) throw new Error('Add at least one page first.');
        out.replaceChildren(); const base = U.safeName(name.value, 'Scan') + '_' + U.stamp();
        const render = (i) => U.process(pages[i].src, pages[i]);
        if (outFmt === 'pdf') {
          const blob = await P.fromCanvases(pages.length, async (i) => render(i), { size, quality: quality.get(), progress: (d, t) => status.busy('Page ' + d + ' of ' + t, d / t) });
          out.replaceChildren(UI.results([{ name: base + '.pdf', blob }]));
        } else {
          const files = [];
          for (let i = 0; i < pages.length; i++) {
            status.busy('Page ' + (i + 1) + ' of ' + pages.length, i / pages.length);
            files.push({ name: base + '_' + (i + 1) + '.' + outFmt, blob: await U.toBlob(render(i), outFmt === 'png' ? 'image/png' : 'image/jpeg', quality.get() / 100) });
            await U.sleep(0);
          }
          out.replaceChildren(UI.results(files));
        }
        status.clear();
      }, exp));

      body.append(
        UI.card('1. Add pages', h('div', { class: 'row' }, camBtn), camBox, h('div', { style: { marginTop: '10px' } }, drop)),
        UI.card('Pages', strip, h('p', { class: 'note' }, 'Click a page to edit it. Edit one page at a time, or use "Use this look on all pages".')),
        editor,
        UI.card('Save', h('p', { class: 'note' }, 'Format'), fmtChips, sizeBox,
          h('div', { class: 'row gap', style: { marginTop: '12px' } }, UI.field('File name', name), quality)),
        h('div', { class: 'row' }, exp), status, out);

      return () => { stopCam(); cancelAnimationFrame(pend); };
    }
  });
})();
