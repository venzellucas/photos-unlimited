'use strict';
(() => {
  const screenshot = document.querySelector('#app-screenshot');
  const panel = document.querySelector('#screen-panel');
  const caption = document.querySelector('#screen-caption');
  const appearanceControl = document.querySelector('#appearance-control');
  const tabs = [...document.querySelectorAll('[data-screen]')];
  const appearances = [...document.querySelectorAll('[data-appearance]')];
  const screens = {
    backup: {label: 'Backup', description: 'showing Connect your account'},
    activity: {label: 'Activity', description: 'with an empty upload history'},
    storage: {label: 'Storage', description: 'showing the library-scan preview before connecting an account'},
    settings: {label: 'Settings', description: 'showing backup preferences and theme controls'}
  };
  let selectedScreen = 'backup';
  let selectedAppearance = 'dark';
  let request = 0;

  async function renderPreview() {
    const currentRequest = ++request;
    const screen = screens[selectedScreen];
    const appearance = selectedScreen === 'backup' ? selectedAppearance : 'dark';
    const src = `media/${selectedScreen}-${appearance}.png`;
    const label = `${screen.label}, ${appearance} theme`;
    panel.setAttribute('aria-busy', 'true');
    const preload = new Image();
    preload.src = src;
    try { await preload.decode(); } catch { /* The image element exposes a failed asset. */ }
    if (currentRequest !== request) return;
    screenshot.src = src;
    screenshot.alt = `The app’s ${appearance} ${screen.label} screen, ${screen.description}`;
    caption.textContent = `Actual app · ${label}`;
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
