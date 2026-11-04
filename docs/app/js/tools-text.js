/* Text & codes: OCR (image/PDF -> text, Word), QR scanner, QR creator. Everything runs in the browser. */
(function () {
  'use strict';
  const { h } = App, U = App.util, UI = App.ui, P = App.pdf;
  const abs = (p) => new URL(p, document.baseURI).href;

  // ------------------------------------------------------------ OCR engine (loaded once, reused)
  let worker = null, workerLang = '';
  async function getWorker(lang, onProgress) {
    if (worker && workerLang === lang) { worker._cb = onProgress; return worker; }
    if (worker) { try { await worker.terminate(); } catch (e) { /* ignore */ } worker = null; }
    let w = null;
    w = await Tesseract.createWorker(lang, 1, {
      workerPath: abs('vendor/worker.min.js'), corePath: abs('vendor/tess-core'), langPath: abs('vendor/tessdata'), gzip: true,
      logger: (m) => { if (w && w._cb) w._cb(m); else if (onProgress) onProgress(m); }
    });
    w._cb = onProgress; worker = w; workerLang = lang; return w;
  }

  /** Minimal .docx with one paragraph per line, built with JSZip. */
  async function makeDocx(pagesText) {
    const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '');
    const paras = [];
    pagesText.forEach((t, pi) => {
      t.split(/\r?\n/).forEach((line, li) => {
        const brk = pi > 0 && li === 0 ? '<w:r><w:br w:type="page"/></w:r>' : '';
        paras.push('<w:p>' + brk + '<w:r><w:t xml:space="preserve">' + esc(line) + '</w:t></w:r></w:p>');
      });
    });
    const z = new JSZip();
    z.file('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>');
    z.file('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>');
    z.file('word/document.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>' + paras.join('') + '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>');
    return z.generateAsync({ type: 'blob', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });
  }

  // =============================================================== OCR
  App.tools.push({
    id: 'ocr', group: 'Text & codes', icon: '🔤', title: 'Extract text (OCR)',
    desc: 'Read the text from a photo, a scanned page or a PDF. English and Hindi. Copy it or save as TXT or Word.',
    render(body) {
      let files = [], lang = 'eng', results = [];
      const status = UI.status(), out = h('div'), info = h('p', { class: 'note' });
      const langChips = UI.chips([{ v: 'eng', l: 'English' }, { v: 'hin+eng', l: 'Hindi + English' }], lang, (v) => (lang = v));
      const area = h('textarea', { rows: 14, style: { width: '100%' }, placeholder: 'The text will appear here. You can edit it.' });
      const actions = h('div', { class: 'row', style: { marginTop: '10px', display: 'none' } },
        UI.btn('Copy text', async () => { try { await navigator.clipboard.writeText(area.value); UI.toast('Copied'); } catch (e) { area.select(); document.execCommand('copy'); UI.toast('Copied'); } }, 'small'),
        UI.btn('Save as TXT', () => U.download(new Blob([area.value], { type: 'text/plain;charset=utf-8' }), 'Docverse_text_' + U.stamp() + '.txt'), 'ghost small'),
        UI.btn('Save as Word', async () => U.download(await makeDocx([area.value]), 'Docverse_text_' + U.stamp() + '.docx'), 'ghost small'));

      const go = UI.btn('Extract text', () => UI.run(status, async () => {
        if (!files.length) throw new Error('Choose a photo or a PDF first.');
        area.value = ''; actions.style.display = 'none';
        status.busy('Starting the text reader (first time takes a few seconds)…');
        let stage = '';
        const w = await getWorker(lang, (m) => { if (m.status === 'recognizing text') status.busy(stage, (m.progress || 0)); });
        const texts = [];
        // build the list of images to read
        const jobs = [];
        for (const f of files) {
          if (/pdf$/i.test(f.type) || /\.pdf$/i.test(f.name)) {
            const doc = await P.openJs(f); if (doc.numPages > 60) throw new Error('This PDF has ' + doc.numPages + ' pages. Please use one with at most 60 pages.');
            for (let i = 0; i < doc.numPages; i++) jobs.push(async () => P.renderPage(doc, i, 2.2));
          } else jobs.push(async () => U.loadImage(f, 2600));
        }
        for (let i = 0; i < jobs.length; i++) {
          stage = 'Reading page ' + (i + 1) + ' of ' + jobs.length; status.busy(stage, 0);
          const canvas = await jobs[i]();
          const r = await w.recognize(canvas);
          texts.push(r.data.text.replace(/\s+$/, ''));
        }
        area.value = texts.map((t, i) => (jobs.length > 1 ? '--- Page ' + (i + 1) + ' ---\n' : '') + t).join('\n\n');
        results = texts; actions.style.display = 'flex';
        if (!area.value.trim()) status.error('No text was found. Try a clearer, brighter photo.'); else status.clear();
      }, go));

      body.append(
        UI.card('1. Choose photos or a PDF', UI.drop({ accept: 'image/*,application/pdf', multiple: true, title: 'Choose photos or a PDF', onFiles: (fs) => { files = fs; info.textContent = fs.map((f) => f.name).join(', '); } }), info),
        UI.card('2. Language', langChips, h('p', { class: 'note' }, 'The language file is loaded from this website, nothing is sent anywhere.')),
        h('div', { class: 'row' }, go), status,
        UI.card('Text', area, actions), out);
      return () => { if (worker) { worker.terminate().catch(() => {}); worker = null; workerLang = ''; } };
    }
  });

  // =============================================================== QR scan
  App.tools.push({
    id: 'qrscan', group: 'Text & codes', icon: '🔍', title: 'Scan QR code',
    desc: 'Read a QR code from a picture, or point your webcam at one.',
    render(body) {
      let stream = null, raf = 0, running = false;
      const status = UI.status(), result = h('div');
      const video = h('video', { autoplay: true, playsInline: true, muted: true }), cam = h('div', { style: { display: 'none' } }, video);
      const work = U.newCanvas(2, 2);

      function show(text) {
        const isUrl = /^https?:\/\//i.test(text);
        result.replaceChildren(UI.card('Result',
          h('pre', { style: { whiteSpace: 'pre-wrap', wordBreak: 'break-all', margin: '0 0 10px', fontFamily: 'inherit' } }, text),
          h('div', { class: 'row' },
            UI.btn('Copy', async () => { try { await navigator.clipboard.writeText(text); UI.toast('Copied'); } catch (e) { UI.toast('Select and copy the text'); } }, 'small'),
            isUrl ? h('a', { class: 'btn ghost small', href: text, target: '_blank', rel: 'noopener noreferrer' }, 'Open link') : null)));
      }
      function decode(canvas) {
        const x = canvas.getContext('2d', { willReadFrequently: true }), d = x.getImageData(0, 0, canvas.width, canvas.height);
        const r = jsQR(d.data, d.width, d.height, { inversionAttempts: 'attemptBoth' }); return r && r.data;
      }
      const stop = () => { running = false; cancelAnimationFrame(raf); if (stream) stream.getTracks().forEach((t) => t.stop()); stream = null; cam.style.display = 'none'; };
      function tick() {
        if (!running) return;
        if (video.videoWidth) {
          const s = Math.min(1, 640 / video.videoWidth); work.width = Math.round(video.videoWidth * s); work.height = Math.round(video.videoHeight * s);
          work.getContext('2d', { willReadFrequently: true }).drawImage(video, 0, 0, work.width, work.height);
          const t = decode(work); if (t) { stop(); show(t); status.clear(); return; }
        }
        raf = requestAnimationFrame(tick);
      }
      const camBtn = UI.btn('📷 Scan with webcam', async () => {
        try {
          stop(); stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
          video.srcObject = stream; cam.style.display = 'block'; await video.play(); running = true; status.busy('Point the camera at a QR code…'); tick();
        } catch (e) { status.error('Could not open the camera: ' + (e.message || e)); }
      }, 'ghost');
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) camBtn.style.display = 'none';

      body.append(
        UI.card('Scan', UI.drop({ accept: 'image/*', title: 'Choose a picture with a QR code', onFiles: (fs) => UI.run(status, async () => {
          stop(); const img = await U.loadImage(fs[0], 1600); let t = decode(img);
          if (!t) { const s = U.fitMax(img, 700); t = decode(s); }
          if (!t) throw new Error('No QR code found in that picture. Try a sharper, closer photo.');
          show(t); status.clear();
        }) }), h('div', { class: 'row', style: { marginTop: '10px' } }, camBtn, UI.btn('Stop camera', stop, 'ghost')), cam),
        status, result);
      return stop;
    }
  });

  // =============================================================== QR create
  App.tools.push({
    id: 'qrmake', group: 'Text & codes', icon: '🔳', title: 'Create QR code',
    desc: 'Turn a link, text, Wi-Fi details or a phone number into a QR code you can download.',
    render(body) {
      let kind = 'text', ecc = 'M';
      const status = UI.status(), out = h('div'), preview = h('canvas', { style: { maxWidth: '100%', background: '#fff', borderRadius: '8px', display: 'none' } });
      const text = h('textarea', { rows: 3, style: { width: '100%' }, placeholder: 'https://example.com' });
      const ssid = UI.text('', 'Network name'), pass = UI.text('', 'Password'), wifiSec = UI.chips([{ v: 'WPA', l: 'WPA/WPA2' }, { v: 'nopass', l: 'No password' }], 'WPA', () => {});
      const phone = UI.text('', '+91 98765 43210');
      const wifiBox = h('div', { style: { display: 'none' } }, UI.field('Wi-Fi name', ssid), UI.field('Password', pass), wifiSec);
      const textBox = h('div', {}, UI.field('Content', text)), phoneBox = h('div', { style: { display: 'none' } }, UI.field('Phone number', phone));
      const kindChips = UI.chips([{ v: 'text', l: 'Link or text' }, { v: 'wifi', l: 'Wi-Fi' }, { v: 'phone', l: 'Phone' }], kind, (v) => {
        kind = v; textBox.style.display = v === 'text' ? 'block' : 'none'; wifiBox.style.display = v === 'wifi' ? 'block' : 'none'; phoneBox.style.display = v === 'phone' ? 'block' : 'none';
      });
      const eccChips = UI.chips([{ v: 'L', l: 'Small' }, { v: 'M', l: 'Normal' }, { v: 'H', l: 'Strong (damage-proof)' }], ecc, (v) => (ecc = v));
      const esc = (s) => s.replace(/([\\;,:"])/g, '\\$1');
      function payload() {
        if (kind === 'wifi') { if (!ssid.value.trim()) throw new Error('Enter the Wi-Fi name.'); const open = wifiSec.value === 'nopass'; return 'WIFI:T:' + (open ? 'nopass' : 'WPA') + ';S:' + esc(ssid.value) + ';' + (open ? '' : 'P:' + esc(pass.value) + ';') + ';'; }
        if (kind === 'phone') { const p = phone.value.replace(/[^\d+]/g, ''); if (!p) throw new Error('Enter a phone number.'); return 'tel:' + p; }
        if (!text.value.trim()) throw new Error('Type something to put in the QR code.'); return text.value.trim();
      }
      const make = UI.btn('Create QR code', () => UI.run(status, async () => {
        const data = payload(); out.replaceChildren();
        const qr = qrcode(0, ecc); qr.addData(data, 'Byte'); qr.make();
        const n = qr.getModuleCount(), cell = Math.max(4, Math.floor(1000 / (n + 8))), size = (n + 8) * cell;
        preview.width = preview.height = size; const x = preview.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, size, size); x.fillStyle = '#000';
        for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (qr.isDark(r, c)) x.fillRect((c + 4) * cell, (r + 4) * cell, cell, cell);
        preview.style.display = 'block'; preview.style.width = Math.min(size, 320) + 'px';
        out.replaceChildren(UI.results([{ name: 'Docverse_QR_' + U.stamp() + '.png', blob: await U.toBlob(preview, 'image/png') }])); status.clear();
      }, make));
      body.append(UI.card('1. What should it contain?', kindChips, h('div', { style: { marginTop: '10px' } }, textBox, wifiBox, phoneBox)),
        UI.card('2. Strength', eccChips, h('p', { class: 'note' }, 'Stronger codes still scan if part of them is dirty or covered, but look denser.')),
        h('div', { class: 'row' }, make), status, h('div', { style: { margin: '12px 0' } }, preview), out);
    }
  });
})();
