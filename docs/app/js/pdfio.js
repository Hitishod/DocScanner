/* PDF helpers: pdf.js renders pages, pdf-lib builds and edits files. */
(function () {
  'use strict';
  const { PDFDocument } = PDFLib;
  const U = App.util;
  pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('vendor/pdf.worker.min.js', document.baseURI).href;
  const P = (App.pdf = {});

  /** Open with pdf.js (for rendering and page counts). */
  P.openJs = async function (file) {
    const data = new Uint8Array(await file.arrayBuffer());
    try { return await pdfjsLib.getDocument({ data }).promise; }
    catch (e) {
      if (e && e.name === 'PasswordException') throw new Error('"' + file.name + '" is password protected, so it cannot be opened here.');
      throw new Error('Cannot open "' + file.name + '": ' + ((e && e.message) || e));
    }
  };

  P.renderPage = async function (doc, index, scale) {
    const page = await doc.getPage(index + 1);
    const vp = page.getViewport({ scale });
    const c = U.newCanvas(vp.width, vp.height), x = c.getContext('2d');
    x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height);
    await page.render({ canvasContext: x, viewport: vp }).promise;
    return c;
  };

  /** Page size in PDF points (1/72 inch). */
  P.pageSize = async function (doc, index) {
    const vp = (await doc.getPage(index + 1)).getViewport({ scale: 1 });
    return { w: vp.width, h: vp.height };
  };

  /** Open with pdf-lib (for editing, copying pages). */
  P.loadLib = async function (file) {
    const buf = await file.arrayBuffer();
    try { return await PDFDocument.load(buf); }
    catch (e) {
      if (/encrypt/i.test(e.message || '')) throw new Error('"' + file.name + '" is password protected, so it cannot be edited here.');
      throw new Error('Cannot open "' + file.name + '": ' + ((e && e.message) || e));
    }
  };

  P.save = async (doc) => new Blob([await doc.save()], { type: 'application/pdf' });

  /**
   * Build a PDF with one JPEG per page.
   * get(i) returns a canvas, or {canvas, w, h} to force the page size in points.
   * o: {size: 'a4'|'letter'|'fit', margin, quality (1-100), progress(done,total)}
   */
  P.fromCanvases = async function (count, get, o) {
    o = o || {};
    const doc = await PDFDocument.create();
    for (let i = 0; i < count; i++) {
      const item = await get(i);
      const canvas = item.canvas || item;
      const blob = await U.toBlob(canvas, 'image/jpeg', (o.quality || 85) / 100);
      const img = await doc.embedJpg(new Uint8Array(await blob.arrayBuffer()));
      let pw, ph, m = 0;
      if (item.w) { pw = item.w; ph = item.h; }
      else if (o.size === 'fit') { pw = canvas.width * 0.75; ph = canvas.height * 0.75; }
      else {
        const [a, b] = o.size === 'letter' ? [612, 792] : [595.28, 841.89];
        [pw, ph] = canvas.width > canvas.height ? [b, a] : [a, b];
        m = o.margin || 0;
      }
      const page = doc.addPage([pw, ph]);
      const r = Math.min((pw - 2 * m) / img.width, (ph - 2 * m) / img.height);
      const w = img.width * r, hh = img.height * r;
      page.drawImage(img, { x: (pw - w) / 2, y: (ph - hh) / 2, width: w, height: hh });
      if (o.progress) o.progress(i + 1, count);
      await U.sleep(0); // let the browser repaint the progress bar
    }
    return P.save(doc);
  };
})();
