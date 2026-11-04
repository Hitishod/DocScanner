/* Convert and size tools: Images to PDF, PDF to images, Resize image, Compress PDF. */
(function () {
  'use strict';
  const { h } = App, U = App.util, UI = App.ui, P = App.pdf;
  const IMG = 'image/*';

  // =============================================================== Images -> PDF
  App.tools.push({
    id: 'img2pdf', group: 'Convert', icon: '📄', title: 'Images to PDF',
    desc: 'Combine photos into one PDF. Change the order with the arrows.',
    render(body) {
      const items = [];
      const status = UI.status(), out = h('div');
      let size = 'a4', filter = 'original';
      const margin = UI.slider('Margin', 0, 60, 1, 0, (v) => v + ' pt');
      const quality = UI.slider('Quality (lower = smaller file)', 30, 100, 1, 85, (v) => v + '%');
      const name = UI.text('', 'Optional, for example Invoice');
      const sizeChips = UI.chips([{ v: 'a4', l: 'A4' }, { v: 'letter', l: 'Letter' }, { v: 'fit', l: 'Fit to image' }], size, (v) => (size = v));
      const filterChips = UI.chips(U.FILTERS, filter, (v) => (filter = v));
      const list = UI.orderList(items, (it) => it.file.name, (it) => h('img', { src: it.url, alt: '' }));
      const make = UI.btn('Create PDF', () => UI.run(status, async () => {
        if (!items.length) throw new Error('Add at least one image first.');
        out.replaceChildren();
        const blob = await P.fromCanvases(items.length, async (i) => {
          const c = await U.loadImage(items[i].file, 2600);
          return U.process(c, { filter });
        }, { size, margin: margin.get(), quality: quality.get(), progress: (d, t) => status.busy('Page ' + d + ' of ' + t, d / t) });
        out.replaceChildren(UI.results([{ name: U.safeName(name.value, 'Images') + '_' + U.stamp() + '.pdf', blob }]));
        status.clear();
      }, make));

      body.append(
        UI.card('1. Add images', UI.drop({ accept: IMG, multiple: true, title: 'Choose images', hint: 'JPG, PNG, WebP… or drop them here',
          onFiles: (fs) => { fs.forEach((f) => items.push({ file: f, url: URL.createObjectURL(f) })); list.refresh(); } }), list),
        UI.card('2. Options',
          h('div', { class: 'row gap' }, UI.field('File name', name)),
          h('p', { class: 'note' }, 'Look'), filterChips, h('p', { class: 'note' }, 'Page size'), sizeChips,
          h('div', { class: 'row gap', style: { marginTop: '12px' } }, margin, quality)),
        h('div', { class: 'row' }, make), status, out);
    }
  });

  // =============================================================== PDF -> images
  App.tools.push({
    id: 'pdf2img', group: 'Convert', icon: '🖼️', title: 'PDF to images',
    desc: 'Save every page, or only some pages, as JPG or PNG pictures.',
    render(body) {
      let doc = null, file = null, scale = 2, fmt = 'jpg';
      const info = h('p', { class: 'note' }), status = UI.status(), out = h('div');
      const range = UI.text('', 'All pages. Or type 1-3, 5');
      const scaleChips = UI.chips([{ v: 1.5, l: 'Normal (108 dpi)' }, { v: 2, l: 'High (144 dpi)' }, { v: 3, l: 'Very high (216 dpi)' }], scale, (v) => (scale = v));
      const fmtChips = UI.chips([{ v: 'jpg', l: 'JPG' }, { v: 'png', l: 'PNG' }], fmt, (v) => (fmt = v));
      const go = UI.btn('Convert to images', () => UI.run(status, async () => {
        out.replaceChildren();
        const n = doc.numPages;
        const pages = range.value.trim() ? U.parseRanges(range.value, n) : [...Array(n).keys()];
        if (!pages.length) throw new Error('Those page numbers are not in this PDF (it has ' + n + ' pages).');
        const base = U.safeName(file.name, 'page'), files = [];
        for (let k = 0; k < pages.length; k++) {
          status.busy('Page ' + (pages[k] + 1) + ' (' + (k + 1) + ' of ' + pages.length + ')', k / pages.length);
          const c = await P.renderPage(doc, pages[k], scale);
          const blob = await U.toBlob(c, fmt === 'png' ? 'image/png' : 'image/jpeg', 0.92);
          files.push({ name: base + '_p' + (pages[k] + 1) + '.' + fmt, blob });
          await U.sleep(0);
        }
        out.replaceChildren(UI.results(files)); status.clear();
      }, go));
      go.disabled = true;

      body.append(
        UI.card('1. Choose a PDF', UI.drop({ accept: 'application/pdf', title: 'Choose a PDF', onFiles: (fs) => UI.run(status, async () => {
          file = fs[0]; doc = await P.openJs(file); go.disabled = false; out.replaceChildren();
          info.textContent = file.name + ' · ' + doc.numPages + ' pages · ' + U.fmtSize(file.size); status.clear();
        }) }), info),
        UI.card('2. Options', h('p', { class: 'note' }, 'Image quality'), scaleChips, h('p', { class: 'note' }, 'Format'), fmtChips,
          h('div', { style: { marginTop: '12px' } }, UI.field('Pages', range))),
        h('div', { class: 'row' }, go), status, out);
    }
  });

  // =============================================================== Resize image
  /** Find a JPEG at most kb KB: lower the quality first, then the dimensions. */
  async function toTarget(src, kb) {
    let cur = src, scale = 1;
    for (let round = 0; round < 10; round++) {
      for (let q = 0.92; q >= 0.3; q -= 0.1) {
        const b = await U.toBlob(cur, 'image/jpeg', q);
        if (b.size <= kb * 1024) return b;
      }
      scale *= 0.8; cur = U.scaleCanvas(src, src.width * scale, src.height * scale);
    }
    return U.toBlob(cur, 'image/jpeg', 0.3);
  }

  App.tools.push({
    id: 'resize', group: 'Size', icon: '📏', title: 'Resize image',
    desc: 'Make photos smaller to share, bigger when you need more pixels, or fit a size limit such as "under 100 KB".',
    render(body) {
      let files = [], mode = 'percent', fmt = 'jpg';
      const info = h('p', { class: 'note' }), status = UI.status(), out = h('div'), opts = h('div');
      const pct = UI.slider('Scale (below 100 = smaller, above = bigger)', 10, 300, 1, 50, (v) => v + '%');
      const px = UI.text('1080'), kb = UI.text('200');
      const q = UI.slider('JPG quality', 30, 100, 1, 85, (v) => v + '%');
      const fmtChips = UI.chips([{ v: 'jpg', l: 'JPG' }, { v: 'png', l: 'PNG' }], fmt, (v) => (fmt = v));
      const modeChips = UI.chips([{ v: 'percent', l: 'Percent' }, { v: 'pixels', l: 'Pixels' }, { v: 'target', l: 'Target file size' }], mode, (v) => { mode = v; drawOpts(); });
      function quick(input, values, unit) {
        return UI.chips(values.map((v) => ({ v, l: v + ' ' + unit })), null, (v) => (input.value = v));
      }
      function drawOpts() {
        opts.replaceChildren();
        if (mode === 'percent') opts.append(pct);
        if (mode === 'pixels') opts.append(UI.field('Longest side in pixels', px), quick(px, ['640', '1080', '1600', '2048', '3000'], 'px'), h('p', { class: 'note' }, 'The other side follows automatically, so the picture is never stretched.'));
        if (mode === 'target') opts.append(UI.field('Maximum size in KB', kb), quick(kb, ['20', '50', '100', '200', '500', '1000'], 'KB'), h('p', { class: 'note' }, 'Handy for forms that say "photo under 100 KB". The result is a JPG.'));
        if (mode !== 'target') opts.append(h('p', { class: 'note' }, 'Format'), fmtChips, fmt === 'jpg' ? q : null);
      }
      drawOpts();
      const go = UI.btn('Resize', () => UI.run(status, async () => {
        if (!files.length) throw new Error('Choose at least one image first.');
        out.replaceChildren(); const res = [];
        for (let i = 0; i < files.length; i++) {
          status.busy('Image ' + (i + 1) + ' of ' + files.length, i / files.length);
          const src = await U.loadImage(files[i], 8000);
          const base = U.safeName(files[i].name, 'image') + '_resized';
          let blob, ext = 'jpg';
          if (mode === 'target') {
            const k = parseInt(kb.value, 10); if (!(k > 0)) throw new Error('Enter the size in KB.');
            blob = await toTarget(src, k);
          } else {
            let c;
            if (mode === 'percent') {
              const longest = Math.max(src.width, src.height);
              const ratio = Math.min(8000, longest * pct.get() / 100) / longest;
              c = U.scaleCanvas(src, src.width * ratio, src.height * ratio);
            }
            else { const L = parseInt(px.value, 10); if (!(L >= 16)) throw new Error('Enter the size in pixels.'); const r = Math.min(8000, L) / Math.max(src.width, src.height); c = U.scaleCanvas(src, src.width * r, src.height * r); }
            ext = fmt; blob = await U.toBlob(c, fmt === 'png' ? 'image/png' : 'image/jpeg', q.get() / 100);
          }
          res.push({ name: base + '.' + ext, blob });
          await U.sleep(0);
        }
        out.replaceChildren(UI.results(res)); status.clear();
      }, go));

      body.append(
        UI.card('1. Choose images', UI.drop({ accept: IMG, multiple: true, title: 'Choose images', onFiles: async (fs) => {
          files = fs; out.replaceChildren();
          const c = await U.loadImage(fs[0], 0).catch(() => null);
          info.textContent = fs.length + ' image(s) selected.' + (c ? ' First one: ' + c.width + ' × ' + c.height + ' px, ' + U.fmtSize(fs[0].size) : '');
        } }), info),
        UI.card('2. How to resize', modeChips, h('div', { style: { marginTop: '12px' } }, opts)),
        h('div', { class: 'row' }, go), status, out);
    }
  });

  // =============================================================== Compress PDF
  const PRESETS = { light: [2.0, 85, 'Light (best quality)'], balanced: [1.5, 70, 'Balanced'], strong: [1.0, 50, 'Strong'], extreme: [0.7, 35, 'Extreme (smallest)'] };
  const STEPS = [[2.0, 85], [1.6, 75], [1.3, 65], [1.0, 55], [0.8, 45], [0.6, 35]];

  App.tools.push({
    id: 'compress', group: 'Size', icon: '🗜️', title: 'Compress PDF',
    desc: 'Make a PDF smaller by re-saving each page as a lighter picture. Text stays readable but can no longer be selected.',
    render(body) {
      let doc = null, file = null, mode = 'preset', preset = 'balanced';
      const info = h('p', { class: 'note' }), status = UI.status(), out = h('div'), opts = h('div');
      const kb = UI.text('500');
      const modeChips = UI.chips([{ v: 'preset', l: 'Preset' }, { v: 'target', l: 'Target size' }], mode, (v) => { mode = v; drawOpts(); });
      const presetChips = UI.chips(Object.keys(PRESETS).map((k) => ({ v: k, l: PRESETS[k][2] })), preset, (v) => (preset = v));
      function drawOpts() {
        opts.replaceChildren(modeChips, h('div', { style: { marginTop: '12px' } },
          mode === 'preset' ? presetChips : [UI.field('Maximum size in KB', kb),
            UI.chips(['100', '200', '500', '1000', '2000'].map((v) => ({ v, l: v + ' KB' })), null, (v) => (kb.value = v)),
            h('p', { class: 'note' }, 'Stronger and stronger settings are tried until the file fits. Very small targets may not be reachable for long documents.')]));
      }
      drawOpts();

      async function build(scale, quality, label) {
        return P.fromCanvases(doc.numPages, async (i) => {
          status.busy(label + ': page ' + (i + 1) + ' of ' + doc.numPages, i / doc.numPages);
          const sz = await P.pageSize(doc, i);
          return { canvas: await P.renderPage(doc, i, scale), w: sz.w, h: sz.h };
        }, { quality });
      }

      const go = UI.btn('Compress', () => UI.run(status, async () => {
        out.replaceChildren(); let blob;
        if (mode === 'preset') { const [s, qq] = PRESETS[preset]; blob = await build(s, qq, PRESETS[preset][2]); }
        else {
          const target = parseInt(kb.value, 10) * 1024; if (!(target > 0)) throw new Error('Enter the size in KB.');
          for (const [s, qq] of STEPS) { blob = await build(s, qq, 'Trying ' + Math.round(s * 72) + ' dpi'); if (blob.size <= target) break; }
        }
        const note = blob.size >= file.size ? 'This PDF was already small, so the new file is not smaller. Keep your original.' : 'Size: ' + U.fmtSize(file.size) + ' → ' + U.fmtSize(blob.size) + ' (' + Math.round(100 - blob.size / file.size * 100) + '% smaller)';
        out.replaceChildren(UI.results([{ name: U.safeName(file.name, 'document') + '_compressed.pdf', blob }], note)); status.clear();
      }, go));
      go.disabled = true;

      body.append(
        UI.card('1. Choose a PDF', UI.drop({ accept: 'application/pdf', title: 'Choose a PDF', onFiles: (fs) => UI.run(status, async () => {
          file = fs[0]; doc = await P.openJs(file); go.disabled = false; out.replaceChildren();
          info.textContent = file.name + ' · ' + doc.numPages + ' pages · ' + U.fmtSize(file.size); status.clear();
        }) }), info),
        UI.card('2. How much to compress', opts), h('div', { class: 'row' }, go), status, out);
    }
  });
})();
