'use strict';
(() => {
  const screenshot = document.querySelector('#app-screenshot');
  const panel = document.querySelector('#screen-panel');
  const caption = document.querySelector('#screen-caption');
  const appearanceControl = document.querySelector('#appearance-control');
  const tabs = [...document.querySelectorAll('[data-screen]')];
  const appearances = [...document.querySelectorAll('[data-appearance]')];
  // Page text comes from the page itself, so each translated page carries its own words.
  const text = JSON.parse(document.querySelector('#site-text').textContent);
  const screens = text.screens;
  const fill = (template, values) => template.replace(/\{(\w+)\}/g, (_, key) => values[key] ?? '');
  let selectedScreen = 'backup';
  let selectedAppearance = 'dark';
  let request = 0;

  async function renderPreview() {
    const currentRequest = ++request;
    const screen = screens[selectedScreen];
    const appearance = selectedScreen === 'backup' ? selectedAppearance : 'dark';
    const src = `${document.documentElement.dataset.root || ''}media/${selectedScreen}-${appearance}.png`;
    const theme = text.themes[appearance];
    const label = fill(text.label, {screen: screen.label, theme});
    panel.setAttribute('aria-busy', 'true');
    const preload = new Image();
    preload.src = src;
    try { await preload.decode(); } catch { /* The image element exposes a failed asset. */ }
    if (currentRequest !== request) return;
    screenshot.src = src;
    screenshot.alt = fill(text.alt, {theme, screen: screen.label, description: screen.description});
    caption.textContent = fill(text.caption, {label});
    panel.setAttribute('aria-busy', 'false');
  }

  function selectScreen(tab, focus = false) {
    if (!screens[tab.dataset.screen]) return;
    selectedScreen = tab.dataset.screen;
    for (const item of tabs) {
      const selected = item === tab;
      item.setAttribute('aria-selected', String(selected));
      item.tabIndex = selected ? 0 : -1;
    }
    panel.setAttribute('aria-labelledby', tab.id);
    appearanceControl.hidden = selectedScreen !== 'backup';
    if (focus) tab.focus();
    renderPreview();
  }

  for (const [index, tab] of tabs.entries()) {
    tab.addEventListener('click', () => selectScreen(tab));
    tab.addEventListener('keydown', event => {
      let next;
      if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (index + 1) % tabs.length;
      if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (index + tabs.length - 1) % tabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = tabs.length - 1;
      if (next === undefined) return;
      event.preventDefault();
      selectScreen(tabs[next], true);
    });
  }
  for (const button of appearances) {
    button.addEventListener('click', () => {
      selectedAppearance = button.dataset.appearance === 'light' ? 'light' : 'dark';
      for (const item of appearances) item.setAttribute('aria-pressed', String(item === button));
      renderPreview();
    });
  }
})();
