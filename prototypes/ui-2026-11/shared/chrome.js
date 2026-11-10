/* Prototype chrome: icons, status bar, navigation bar, the Add button, and the clutter counter.
   Each screen sets <body data-dir="a" data-tab="today|plan|track|people" [data-fab="none"]>. */
(function () {
  const I = {
    today: '<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/><circle cx="12" cy="15" r="1.4" fill="currentColor" stroke="none"/>',
    plan: '<path d="M4 7.5l2 2 3.5-3.5M12.5 8H20M4 15.5l2 2 3.5-3.5M12.5 16H20"/>',
    track: '<path d="M3 12.5h4l3-7 4 13 3-6h4"/>',
    people: '<circle cx="9" cy="8.5" r="3.5"/><path d="M2.8 20c.4-3.4 3-5.6 6.2-5.6s5.8 2.2 6.2 5.6"/><path d="M15.5 5.3a3.3 3.3 0 0 1 0 6.4M17.6 14.6c2 .7 3.4 2.6 3.6 5.4"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    close: '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>',
    chev: '<path d="M9.5 6l6 6-6 6"/>',
    back: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
    more: '<circle cx="12" cy="5.5" r="1.3" fill="currentColor"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/><circle cx="12" cy="18.5" r="1.3" fill="currentColor"/>',
    tune: '<path d="M4 7h8.5M16.5 7H20M4 17h3M11 17h9"/><circle cx="14.5" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
    moon: '<path d="M19.5 14.6A7.8 7.8 0 1 1 9.4 4.5a6.2 6.2 0 0 0 10.1 10.1z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/>',
    dumbbell: '<path d="M6.5 7.5v9M17.5 7.5v9M3.5 10v4M20.5 10v4M6.5 12h11"/>',
    note: '<rect x="5" y="3.5" width="14" height="17" rx="2.5"/><path d="M9 8.5h6M9 12h6M9 15.5h3.5"/>',
    event: '<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4M12 13v5M9.5 15.5h5"/>',
    bell: '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    task: '<circle cx="12" cy="12" r="8.5"/><path d="M8.5 12.2l2.4 2.4 4.6-4.8"/>',
    cake: '<rect x="4" y="11" width="16" height="9.5" rx="2.5"/><path d="M4 15.2c2.6 1.6 5.4 1.6 8 0s5.4-1.6 8 0M12 11V8"/><path d="M12 3.5c1 1.2 1 2.6 0 3-1-.4-1-1.8 0-3z"/>',
    text: '<path d="M5 7h14M5 12h10M5 17h6.5"/>',
    play: '<path d="M8 5.5v13l10.5-6.5z" fill="currentColor" stroke-width="1.2"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  };
  window.KI = (name, cls) => `<svg class="i ${cls || ''}" viewBox="0 0 24 24" aria-hidden="true">${I[name] || ''}</svg>`;

  function hydrateIcons(root) {
    root.querySelectorAll('i[data-icon]').forEach(el => {
      el.outerHTML = KI(el.dataset.icon, el.className);
    });
  }

  function chrome() {
    const b = document.body;
    const tab = b.dataset.tab || 'today';
    b.insertAdjacentHTML('afterbegin',
      '<div class="status" aria-hidden="true"><span>8:52</span><span class="sys">' +
      '<span style="width:15px;height:11px;clip-path:polygon(0 100%,100% 0,100% 100%)"></span>' +
      '<span style="width:9px;height:14px;border-radius:2px"></span></span></div>');
    if (b.dataset.nav !== 'none') {
      const tabs = [['today', 'Today'], ['plan', 'Plan'], ['track', 'Track'], ['people', 'People']];
      b.insertAdjacentHTML('beforeend', '<nav class="bar" aria-label="Main">' + tabs.map(([id, label]) =>
        `<a href="${id}.html" ${id === tab ? 'aria-current="page"' : ''}><span class="pill">${KI(id)}</span>${label}</a>`).join('') + '</nav>');
    }
    if (b.dataset.fab !== 'none') {
      b.insertAdjacentHTML('beforeend', '<a class="fab" href="add-sheet.html" aria-label="Add" data-primary>' + KI('plus') + '</a>');
    }
    hydrateIcons(document);
  }

  /* Clutter budget (docs/ui/design-system.md): in the first viewport, outside the navigation bar,
     count tappables (a list of rows counts once), text inputs, and primary actions. */
  function count() {
    const top = 32, bottom = 915 - 96;
    const vis = el => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el);
      return r.width > 0 && r.height > 0 && r.bottom > top && r.top < bottom && s.visibility !== 'hidden' && s.display !== 'none'; };
    const seen = new Set(); let tap = 0;
    document.querySelectorAll('a, button, [role="button"], input, textarea, [data-tap]').forEach(el => {
      if (el.closest('nav.bar') || !vis(el)) return;
      if (el.matches('input, textarea')) return;
      const list = el.closest('[data-list]');
      if (list) { if (!seen.has(list)) { seen.add(list); tap++; } return; }
      if (el.parentElement && el.parentElement.closest('a, button')) return;
      tap++;
    });
    const inputs = [...document.querySelectorAll('input, textarea')].filter(vis).length;
    const primary = [...document.querySelectorAll('[data-primary]')].filter(vis).length;
    const result = { tap: tap + inputs, inputs, primary };
    document.body.dataset.clutter = JSON.stringify(result);
    if (location.hash === '#count') {
      document.body.insertAdjacentHTML('beforeend', `<div style="position:absolute;left:8px;top:40px;z-index:9;background:#000c;color:#fff;padding:4px 8px;border-radius:8px;font:700 13px Cantarell">${result.tap} tappable, ${inputs} input, ${primary} primary</div>`);
    }
  }

  document.addEventListener('DOMContentLoaded', () => { chrome(); document.fonts.ready.then(() => setTimeout(count, 50)); });
})();
