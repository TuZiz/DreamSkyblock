/* Text-only item Lore. Slot icons stay hidden; clicks keep the native container path. */
(function () {
  'use strict';
  var tooltip = document.getElementById('menu-lore');
  var heading = document.getElementById('lore-heading');
  var content = document.getElementById('lore-content');
  var items = {}, hovered = null, point = null;
  function plain(value) { return String(value || '').replace(/§x(?:§[0-9a-f]){6}/gi, '').replace(/§[0-9a-fk-or]/gi, '').trim(); }
  function hide() { hovered = null; point = null; tooltip.style.display = 'none'; }
  function render() {
    if (!hovered) return;
    var action = hovered.getAttribute('data-action');
    var item = items[hovered.getAttribute('data-slot')] || DreamChestMenus.main.tooltips[action];
    if (!item) return;
    heading.textContent = plain(item.name);
    content.textContent = (item.lore || []).map(plain).join('\n');
    tooltip.style.fontSize = Math.max(14, Math.min(23, window.innerHeight / 65)) + 'px';
    tooltip.style.width = Math.min(430, window.innerWidth - 32) + 'px';
    tooltip.style.display = 'block';
    var rect = hovered.getBoundingClientRect();
    var x = point ? point.x + 18 : rect.left;
    var y = point ? point.y + 22 : rect.top - tooltip.getBoundingClientRect().height - 12;
    var box = tooltip.getBoundingClientRect();
    if (y + box.height > window.innerHeight - 16) y = (point ? point.y : rect.top) - box.height - 16;
    tooltip.style.left = Math.max(16, Math.min(x, window.innerWidth - box.width - 16)) + 'px';
    tooltip.style.top = Math.max(16, y) + 'px';
  }
  document.querySelectorAll('[data-action]').forEach(function (button) {
    button.addEventListener('mouseenter', function (event) { hovered = button; point = {x:event.clientX,y:event.clientY}; render(); });
    button.addEventListener('mousemove', function (event) { point = {x:event.clientX,y:event.clientY}; render(); });
    button.addEventListener('mouseleave', hide);
    button.addEventListener('focus', function () { hovered = button; point = null; render(); });
    button.addEventListener('blur', hide);
    button.addEventListener('mousedown', hide);
    button.addEventListener('click', hide);
  });
  document.addEventListener('paidaxing:pageHide', hide);
  document.addEventListener('keydown', function (event) { if (event.key === 'Escape') hide(); });
  window.addEventListener('resize', hide);
  window.DreamLore = {update:function (slots) {
    items = {}; slots.forEach(function (item) { items[item.index] = item; });
    render();
  }};
})();
