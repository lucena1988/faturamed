// Menu lateral: cada grupo abre e fecha so pelo proprio titulo; lembra os grupos abertos.
(function () {
  const key = 'faturamed-menu-grupos';
  const titles = [...document.querySelectorAll('.nav-group-title')];
  if (!titles.length) return;

  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(key)); } catch (_) {}
  const activeTitle = document.querySelector('.nav-item.active')?.closest('.nav-group')?.querySelector('.nav-group-title');
  const opened = new Set(Array.isArray(saved) ? saved : [activeTitle?.id || titles[0].id]);

  function save() {
    try { localStorage.setItem(key, JSON.stringify([...opened])); } catch (_) {}
  }

  for (const title of titles) {
    title.setAttribute('aria-expanded', String(opened.has(title.id)));
    title.addEventListener('click', () => {
      const expanded = title.getAttribute('aria-expanded') === 'true';
      title.setAttribute('aria-expanded', String(!expanded));
      expanded ? opened.delete(title.id) : opened.add(title.id);
      save();
    });
  }
})();
