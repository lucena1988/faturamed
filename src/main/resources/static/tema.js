(function () {
  const key = 'faturamed-tema';
  let theme = 'light';
  try { theme = localStorage.getItem(key) || 'light'; } catch (_) {}
  const icons = {
    dark: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z"/></svg>',
    light: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></svg>'
  };
  function apply(value) {
    theme = value === 'dark' ? 'dark' : 'light';
    document.documentElement.dataset.theme = theme;
    document.querySelectorAll('[data-theme-toggle]').forEach(button => {
      const next = theme === 'dark' ? 'light' : 'dark';
      button.innerHTML = icons[next];
      button.title = next === 'dark' ? 'Ativar modo escuro' : 'Ativar modo claro';
      button.setAttribute('aria-label', button.title);
      button.setAttribute('aria-pressed', String(theme === 'dark'));
    });
  }
  apply(theme);
  document.addEventListener('DOMContentLoaded', () => {
    const host = document.querySelector('header .session-actions') || document.querySelector('.topbar') || document.querySelector('.access-form');
    if (!host) return;
    const button = document.createElement('button'); button.type = 'button'; button.className = 'theme-toggle'; button.dataset.themeToggle = '';
    host.prepend(button);
    button.addEventListener('click', () => { apply(theme === 'dark' ? 'light' : 'dark'); try { localStorage.setItem(key, theme); } catch (_) {} });
    apply(theme);
  });
  window.addEventListener('storage', event => { if (event.key === key) apply(event.newValue); });
})();
