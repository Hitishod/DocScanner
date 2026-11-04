/* Sign / add image to a PDF, and watermark + page numbers. Edits keep the existing text of the PDF. */
(function () {
  'use strict';
  const { h } = App, U = App.util, UI = App.ui, P = App.pdf;
  const { degrees, rgb, StandardFonts } = PDFLib;

  /** Load an image keeping its transparency (U.loadImage paints white behind it). */
  async function loadAlpha(file, maxDim) {
    const bmp = await createImageBitmap(file);
    const r = Math.min(1, maxDim / Math.max(bmp.width, bmp.height));
    const c = U.newCanvas(bmp.width * r, bmp.height * r); c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height); bmp.close && bmp.close(); return c;
  }
  function whiteToTransparent(src) {
    const c = U.newCanvas(src.width, src.height), x = c.getContext('2d'); x.drawImage(src, 0, 0);
    const img = x.getImageData(0, 0, c.width, c.height), d = img.data;
    for (let i = 0; i < d.length; i += 4) if (d[i] > 235 && d[i + 1] > 235 && d[i + 2] > 235) d[i + 3] = 0;
    x.putImageData(img, 0, 0); return c;
  }

  // =============================================================== Sign / add image
  App.tools.push({
    id: 'sign', group: 'Edit PDF', icon: '✍️', title: 'Sign / add image',
    desc: 'Add your signature, a stamp or any picture to a PDF. Drag it where you want it.',
    render(body) {
      let jsDoc = null, file = null, pageCount = 0, pageIdx = 0, baseOverlay = null, overlay = null;
      let fx = 0.3, fy = 0.72, fw = 0.3, applyTo = 'this';
      const status = UI.status(), out = h('div'), info = h('p', { class: 'note' });
      const stage = h('div', { class: 'stage', style: { display: 'none', maxWidth: '620px' } });
      const pageCanvas = h('canvas'); const ovImg = h('img', { style: { position: 'absolute', cursor: 'grab', touchAction: 'none', outline: '1px dashed #2D5BFF', display: 'none' }, draggable: false });
      stage.append(pageCanvas, ovImg);
      const pageSlider = UI.slider('Preview page', 1, 1, 1, 1, (v) => v + ' / ' + pageCount, (v) => { pageIdx = v - 1; drawPage(); });
      const sizeSlider = UI.slider('Size', 5, 100, 1, 30, (v) => v + '% of page width', (v) => { fw = v / 100; place(); });
      const whiteSwitch = h('input', { type: 'checkbox', onchange: () => setOverlay(baseOverlay) });
      const range = UI.text('', 'For example 1-3, 5');
      const applyChips = UI.chips([{ v: 'this', l: 'This page' }, { v: 'all', l: 'All pages' }, { v: 'range', l: 'Page range' }], applyTo, (v) => { applyTo = v; rangeBox.style.display = v === 'range' ? 'block' : 'none'; });
      const rangeBox = h('div', { style: { display: 'none', marginTop: '8px' } }, UI.field('Pages', range));

      function setOverlay(canvas) {
        baseOverlay = canvas; if (!canvas) { overlay = null; place(); return; }
        overlay = whiteSwitch.checked ? whiteToTransparent(canvas) : canvas;
        ovImg.src = overlay.toDataURL('image/png'); place();
      }
      function place() {
        ovImg.style.display = overlay && jsDoc ? 'block' : 'none';
        if (!overlay) return;
        fx = Math.max(0, Math.min(1 - fw, fx)); fy = Math.max(0, fy);
        ovImg.style.left = fx * 100 + '%'; ovImg.style.top = fy * 100 + '%'; ovImg.style.width = fw * 100 + '%'; ovImg.style.height = 'auto';
      }
      async function drawPage() {
        if (!jsDoc) return;
        const c = await P.renderPage(jsDoc, pageIdx, 1.2);
        pageCanvas.width = c.width; pageCanvas.height = c.height; pageCanvas.getContext('2d').drawImage(c, 0, 0); stage.style.display = 'inline-block';
      }
      // dragging the picture
      let drag = null;
      ovImg.addEventListener('pointerdown', (e) => { const r = stage.getBoundingClientRect(); drag = { x: e.clientX, y: e.clientY, fx, fy, r }; ovImg.setPointerCapture(e.pointerId); ovImg.style.cursor = 'grabbing'; e.preventDefault(); });
      ovImg.addEventListener('pointermove', (e) => {
        if (!drag) return;
        fx = drag.fx + (e.clientX - drag.x) / drag.r.width; fy = drag.fy + (e.clientY - drag.y) / drag.r.height;
        const hFrac = ovImg.getBoundingClientRect().height / drag.r.height; fy = Math.min(fy, 1 - hFrac); place();
      });
      ['pointerup', 'pointercancel'].forEach((ev) => ovImg.addEventListener(ev, () => { drag = null; ovImg.style.cursor = 'grab'; }));

      // signature pad
      const pad = h('canvas', { class: 'pad', width: 560, height: 190 }), px = pad.getContext('2d');
      let strokes = 0, bb = null, down = false;
      px.lineWidth = 4; px.lineCap = 'round'; px.lineJoin = 'round'; px.strokeStyle = '#000';
      const pp = (e) => { const r = pad.getBoundingClientRect(); return [(e.clientX - r.left) * pad.width / r.width, (e.clientY - r.top) * pad.height / r.height]; };
      pad.addEventListener('pointerdown', (e) => { down = true; const [x, y] = pp(e); px.beginPath(); px.moveTo(x, y); px.lineTo(x + 0.1, y); px.stroke(); pad.setPointerCapture(e.pointerId); mark(x, y); e.preventDefault(); });
      pad.addEventListener('pointermove', (e) => { if (!down) return; const [x, y] = pp(e); px.lineTo(x, y); px.stroke(); px.beginPath(); px.moveTo(x, y); mark(x, y); });
      ['pointerup', 'pointercancel'].forEach((ev) => pad.addEventListener(ev, () => { down = false; }));
      function mark(x, y) { strokes++; bb = bb ? [Math.min(bb[0], x), Math.min(bb[1], y), Math.max(bb[2], x), Math.max(bb[3], y)] : [x, y, x, y]; }
      const padCard = h('div', { style: { display: 'none', marginTop: '10px' } }, pad,
        h('div', { class: 'row', style: { marginTop: '8px' } },
          UI.btn('Use this signature', () => {
            if (!bb) return UI.toast('Draw your signature first');
            const m = 8, x0 = Math.max(0, bb[0] - m), y0 = Math.max(0, bb[1] - m), w = Math.min(pad.width, bb[2] + m) - x0, hh = Math.min(pad.height, bb[3] + m) - y0;
            const c = U.newCanvas(w, hh); c.getContext('2d').drawImage(pad, x0, y0, w, hh, 0, 0, w, hh);
            whiteSwitch.checked = false; setOverlay(c); padCard.style.display = 'none'; UI.toast('Signature ready. Drag it onto the page.');
          }, 'small'),
          UI.btn('Clear', () => { px.clearRect(0, 0, pad.width, pad.height); bb = null; }, 'ghost small')));

      const go = UI.btn('Insert and save PDF', () => UI.run(status, async () => {
        if (!overlay) throw new Error('Choose an image or draw a signature first.');
        out.replaceChildren();
        const doc = await P.loadLib(file), png = await doc.embedPng(await (await U.toBlob(overlay, 'image/png')).arrayBuffer());
        const targets = applyTo === 'all' ? [...Array(pageCount).keys()] : applyTo === 'this' ? [pageIdx] : U.parseRanges(range.value, pageCount);
        if (!targets.length) throw new Error('No valid pages selected.');
        const pages = doc.getPages();
        targets.forEach((i) => {
          const pg = pages[i], { width, height } = pg.getSize(), w = width * fw, hh = w * overlay.height / overlay.width;
          pg.drawImage(png, { x: width * fx, y: height - height * fy - hh, width: w, height: hh });
        });
        out.replaceChildren(UI.results([{ name: U.safeName(file.name, 'document') + '_signed.pdf', blob: await P.save(doc) }])); status.clear();
      }, go));
      go.disabled = true;

      body.append(
        UI.card('1. Choose a PDF', UI.drop({ accept: 'application/pdf', title: 'Choose a PDF', onFiles: (fs) => UI.run(status, async () => {
          file = fs[0]; jsDoc = await P.openJs(file); pageCount = jsDoc.numPages; pageIdx = 0; go.disabled = false; out.replaceChildren();
          pageSlider.querySelector('input').max = pageCount; pageSlider.set(1);
          pageSlider.style.display = pageCount > 1 ? 'flex' : 'none';
          info.textContent = file.name + ' · ' + pageCount + ' pages'; await drawPage(); place(); status.clear();
        }) }), info),
        UI.card('2. What to insert',
          h('div', { class: 'row' },
            UI.btn('✍ Draw a signature', () => { padCard.style.display = padCard.style.display === 'none' ? 'block' : 'none'; }, 'ghost'),
            h('label', { class: 'btn ghost' }, 'Choose an image', h('input', { type: 'file', accept: 'image/*', style: { display: 'none' }, onchange: async (e) => { const f = e.target.files[0]; if (f) { try { setOverlay(await loadAlpha(f, 1400)); } catch (er) { status.error('Cannot read that image.'); } } e.target.value = ''; } }))),
          padCard, h('label', { class: 'row', style: { marginTop: '10px', fontSize: '14px' } }, whiteSwitch, 'Make the white background transparent (for a photo of a signature)')),
        UI.card('3. Position and size', pageSlider, stage, h('div', { style: { marginTop: '12px', maxWidth: '420px' } }, sizeSlider),
          h('p', { class: 'note' }, 'Drag the picture on the page to move it.')),
        UI.card('4. Which pages', applyChips, rangeBox),
        h('div', { class: 'row' }, go), status, out);
    }
  });

  // =============================================================== Watermark + page numbers
  App.tools.push({
    id: 'stamp', group: 'Edit PDF', icon: '💧', title: 'Watermark & page numbers',
    desc: 'Put a diagonal text watermark on every page, or add "Page 1 of N" at the bottom.',
    render(body) {
      let file = null, count = 0;
      const info = h('p', { class: 'note' }), status = UI.status(), out = h('div');
      const text = UI.text('CONFIDENTIAL'); const op = UI.slider('Visibility', 10, 80, 1, 25, (v) => v + '%');
      const note = 'Watermark text supports English letters, numbers and common symbols.';
      const wm = UI.btn('Add watermark', () => UI.run(status, async () => {
        const t = text.value.trim(); if (!t) throw new Error('Type the watermark text first.');
        out.replaceChildren();
        const doc = await P.loadLib(file), font = await doc.embedFont(StandardFonts.HelveticaBold), th = 35 * Math.PI / 180;
        try {
          doc.getPages().forEach((pg) => {
            const { width, height } = pg.getSize(), w100 = font.widthOfTextAtSize(t, 100);
            const size = Math.min(width / 5, (0.8 * width / Math.cos(th)) / (w100 / 100)), w = w100 * size / 100;
            const ox = w / 2, oy = size * 0.3, rx = ox * Math.cos(th) - oy * Math.sin(th), ry = ox * Math.sin(th) + oy * Math.cos(th);
            pg.drawText(t, { x: width / 2 - rx, y: height / 2 - ry, size, font, color: rgb(0.5, 0.5, 0.5), opacity: op.get() / 100, rotate: degrees(35) });
          });
        } catch (e) { throw new Error(note); }
        out.replaceChildren(UI.results([{ name: U.safeName(file.name, 'document') + '_watermarked.pdf', blob: await P.save(doc) }])); status.clear();
      }, wm));
      const nums = UI.btn('Add page numbers', () => UI.run(status, async () => {
        out.replaceChildren();
        const doc = await P.loadLib(file), font = await doc.embedFont(StandardFonts.Helvetica), pages = doc.getPages();
        pages.forEach((pg, i) => { const s = 'Page ' + (i + 1) + ' of ' + pages.length, w = font.widthOfTextAtSize(s, 11); pg.drawText(s, { x: (pg.getWidth() - w) / 2, y: 18, size: 11, font, color: rgb(0.25, 0.25, 0.25) }); });
        out.replaceChildren(UI.results([{ name: U.safeName(file.name, 'document') + '_numbered.pdf', blob: await P.save(doc) }])); status.clear();
      }, nums), 'ghost');
      wm.disabled = nums.disabled = true;
      body.append(
        UI.card('1. Choose a PDF', UI.drop({ accept: 'application/pdf', title: 'Choose a PDF', onFiles: (fs) => UI.run(status, async () => {
          file = fs[0]; const d = await P.loadLib(file); count = d.getPageCount(); wm.disabled = nums.disabled = false; out.replaceChildren();
          info.textContent = file.name + ' · ' + count + ' pages'; status.clear();
        }) }), info),
        UI.card('Text watermark', UI.field('Watermark text', text), h('div', { style: { margin: '12px 0', maxWidth: '420px' } }, op), wm, h('p', { class: 'note' }, note)),
        UI.card('Page numbers', nums), status, out);
    }
  });
})();
