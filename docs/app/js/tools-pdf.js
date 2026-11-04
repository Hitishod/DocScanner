/* PDF editing tools: Merge, Split / extract, Organize pages. These keep text selectable (pages are copied, not redrawn). */
(function () {
  'use strict';
  const { h } = App, U = App.util, UI = App.ui, P = App.pdf;
  const { PDFDocument, degrees } = PDFLib;
  const PDF = 'application/pdf';

  // =============================================================== Merge
  App.tools.push({
    id: 'merge', group: 'Edit PDF', icon: '🔗', title: 'Merge PDFs',
    desc: 'Join two or more PDFs into one. They are joined in the order shown. Text stays selectable.',
    render(body) {
      const items = [], status = UI.status(), out = h('div');
      const name = UI.text('', 'Optional, for example Combined');
      const list = UI.orderList(items, (it) => it.file.name + ' (' + U.fmtSize(it.file.size) + ')');
      const go = UI.btn('Merge', () => UI.run(status, async () => {
        if (items.length < 2) throw new Error('Add at least two PDFs.');
        out.replaceChildren();
        const merged = await PDFDocument.create();
        for (let i = 0; i < items.length; i++) {
          status.busy('Adding ' + items[i].file.name, i / items.length);
          const src = await P.loadLib(items[i].file);
          const pages = await merged.copyPages(src, src.getPageIndices());
          pages.forEach((p) => merged.addPage(p));
        }
        out.replaceChildren(UI.results([{ name: U.safeName(name.value, 'Merged') + '_' + U.stamp() + '.pdf', blob: await P.save(merged) }]));
        status.clear();
      }, go));
      body.append(
        UI.card('1. Add PDFs', UI.drop({ accept: PDF, multiple: true, title: 'Choose PDFs', onFiles: (fs) => { fs.forEach((f) => items.push({ file: f })); list.refresh(); } }), list),
        UI.card('2. File name', UI.field('Name', name)),
        h('div', { class: 'row' }, go), status, out);
    }
  });

  // =============================================================== Split / extract
  App.tools.push({
    id: 'split', group: 'Edit PDF', icon: '✂️', title: 'Split / extract pages',
    desc: 'Pull pages out of a PDF into a new one, or split every page into its own PDF.',
    render(body) {
      let src = null, file = null, count = 0;
      const info = h('p', { class: 'note' }), status = UI.status(), out = h('div');
      const range = UI.text('', 'For example 1-3, 5, 8-10');
      async function extract(indices) {
        const d = await PDFDocument.create();
        (await d.copyPages(src, indices)).forEach((p) => d.addPage(p));
        return P.save(d);
      }
      const base = () => U.safeName(file.name, 'document');
      const go1 = UI.btn('Extract these pages', () => UI.run(status, async () => {
        const pages = U.parseRanges(range.value, count);
        if (!pages.length) throw new Error('Enter page numbers between 1 and ' + count + '.');
        out.replaceChildren(UI.results([{ name: base() + '_pages.pdf', blob: await extract(pages) }])); status.clear();
      }, go1));
      const go2 = UI.btn('Split into separate PDFs', () => UI.run(status, async () => {
        if (count > 200) throw new Error('Too many pages to split at once. Extract a range instead.');
        const files = [];
        for (let i = 0; i < count; i++) { status.busy('Page ' + (i + 1) + ' of ' + count, i / count); files.push({ name: base() + '_page' + (i + 1) + '.pdf', blob: await extract([i]) }); await U.sleep(0); }
        out.replaceChildren(UI.results(files)); status.clear();
      }, go2), 'ghost');
      go1.disabled = go2.disabled = true;
      body.append(
        UI.card('1. Choose a PDF', UI.drop({ accept: PDF, title: 'Choose a PDF', onFiles: (fs) => UI.run(status, async () => {
          file = fs[0]; src = await P.loadLib(file); count = src.getPageCount(); go1.disabled = go2.disabled = false; out.replaceChildren();
          info.textContent = file.name + ' · ' + count + ' pages'; status.clear();
        }) }), info),
        UI.card('2. Extract pages into one new PDF', UI.field('Pages', range), h('div', { style: { marginTop: '12px' } }, go1)),
        UI.card('Or split every page', go2), status, out);
    }
  });

  // =============================================================== Organize pages
  App.tools.push({
    id: 'organize', group: 'Edit PDF', icon: '🗂️', title: 'Organize pages',
    desc: 'Rotate, delete and reorder pages, then save a new PDF.',
    render(body) {
      let file = null, src = null, thumbs = [], pages = [];
      const info = h('p', { class: 'note' }), status = UI.status(), out = h('div'), grid = h('div', { class: 'grid2' });
      const name = UI.text('', 'Optional');

      function draw() {
        grid.replaceChildren(...pages.map((p, i) => {
          const c = U.rotateCanvas(thumbs[p.src], p.rot);
          const b = (t, title, fn, dis) => h('button', { class: 'ib', type: 'button', title, disabled: !!dis, onclick: fn }, t);
          return h('div', { class: 'thumb' }, c, h('div', {}, 'Page ' + (i + 1) + (p.src !== i ? ' (was ' + (p.src + 1) + ')' : '')),
            h('div', { class: 'acts' },
              b('↻', 'Rotate', () => { p.rot = (p.rot + 90) % 360; draw(); }),
              b('←', 'Move earlier', () => { [pages[i - 1], pages[i]] = [pages[i], pages[i - 1]]; draw(); }, i === 0),
              b('→', 'Move later', () => { [pages[i + 1], pages[i]] = [pages[i], pages[i + 1]]; draw(); }, i === pages.length - 1),
              b('🗑', 'Delete', () => { pages.splice(i, 1); draw(); })));
        }));
        save.disabled = !pages.length;
      }

      const save = UI.btn('Save new PDF', () => UI.run(status, async () => {
        out.replaceChildren();
        const d = await PDFDocument.create();
        const copied = await d.copyPages(src, pages.map((p) => p.src));
        copied.forEach((pg, i) => { pg.setRotation(degrees((pg.getRotation().angle + pages[i].rot) % 360)); d.addPage(pg); });
        out.replaceChildren(UI.results([{ name: U.safeName(name.value || file.name, 'Edited') + '_edited.pdf', blob: await P.save(d) }])); status.clear();
      }, save));
      save.disabled = true;

      body.append(
        UI.card('1. Choose a PDF', UI.drop({ accept: PDF, title: 'Choose a PDF', onFiles: (fs) => UI.run(status, async () => {
          file = fs[0]; out.replaceChildren();
          src = await P.loadLib(file);
          const js = await P.openJs(file); const n = js.numPages;
          if (n > 150) throw new Error('This PDF has ' + n + ' pages. Please use a PDF with at most 150 pages here.');
          thumbs = []; pages = [];
          for (let i = 0; i < n; i++) { status.busy('Loading page ' + (i + 1) + ' of ' + n, i / n); thumbs.push(await P.renderPage(js, i, 0.35)); pages.push({ src: i, rot: 0 }); }
          info.textContent = file.name + ' · ' + n + ' pages'; status.clear(); draw();
        }) }), info),
        UI.card('2. Pages', grid, h('div', { style: { marginTop: '12px' } }, UI.field('File name', name))),
        h('div', { class: 'row' }, save), status, out);
    }
  });
})();
