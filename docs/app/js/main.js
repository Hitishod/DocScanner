/* Navigation: builds the sidebar from the registered tools and shows the tool named in the URL (#merge). */
(function () {
  'use strict';
  const { h } = App, UI = App.ui;
  const GROUPS = ['Scan', 'Convert', 'Size', 'Edit PDF', 'Text & codes'];
  let cleanup = null;

  function buildNav() {
    const nav = document.getElementById('nav');
    nav.replaceChildren();
    GROUPS.forEach((g) => {
      const tools = App.tools.filter((t) => t.group === g);
      if (!tools.length) return;
      nav.append(h('div', { class: 'grp' }, g));
      tools.forEach((t) => nav.append(h('a', { class: 'nav-item', href: '#' + t.id, 'data-id': t.id }, h('i', {}, t.icon), t.title)));
    });
  }

  function show() {
    const id = location.hash.slice(1) || App.tools[0].id;
    const tool = App.tools.find((t) => t.id === id) || App.tools[0];
    if (cleanup) { try { cleanup(); } catch (e) { /* ignore */ } cleanup = null; }
    document.querySelectorAll('.nav-item').forEach((a) => a.classList.toggle('on', a.dataset.id === tool.id));
    const page = UI.page(tool.title, tool.desc);
    document.getElementById('view').replaceChildren(page.el);
    document.title = tool.title + ' · Docverse Web';
    try { cleanup = tool.render(page.body) || null; }
    catch (e) { console.error(e); page.body.append(h('p', { class: 'status err' }, 'This tool failed to start: ' + e.message)); }
    window.scrollTo(0, 0);
  }

  window.addEventListener('hashchange', show);
  window.addEventListener('DOMContentLoaded', () => { buildNav(); show(); });
})();
