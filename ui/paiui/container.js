/* Pure PaiUI container skin. Native slot clicks preserve DreamSkyblock permissions and actions. */
(function () {
  'use strict';
  var page = document.body.getAttribute('data-page');
  var menu = window.DreamChestMenus[page];
  var api = null, poller = null, active = false, generation = 0, resizeTimer = 0;
  var containerId = null, interactionReady = false, visible = true, papiPending = {};
  var buttons = document.querySelectorAll('[data-action]');
  // These are fixed menu actions. Display-data callbacks must never gate native clicks.
  buttons.forEach(function (button) {
    var slot = menu.slots[button.getAttribute('data-action')];
    if (slot != null) { button.setAttribute('data-slot', slot); button.classList.add('slot'); button.disabled = false; }
  });
  function fitMenu() {
    var width = window.innerWidth, height = window.innerHeight;
    var scale = page === 'main'
      ? Math.min(width * 0.94 / 1440, height * 0.94 / 900)
      : Math.min(width * 0.8 / 1020, height * 0.8 / 640);
    scale = Math.max(page === 'main' ? 0.1 : 0.65, Math.min(2.5, scale));
    var panel = document.querySelector('.game-menu');
    panel.style.transform = 'scale(' + scale + ')';
    panel.style.maxHeight = (height * (page === 'main' ? 0.96 : 0.86) / scale) + 'px';
  }
  function field(name, value) {
    document.querySelectorAll('[data-field="' + name + '"]').forEach(function (el) { el.textContent = value == null || value === '' ? '—' : String(value); });
  }
  function plain(value) { return String(value || '').replace(/§x(?:§[0-9a-f]){6}/gi, '').replace(/§[0-9a-fk-or]/gi, '').trim(); }
  function lines(item) { return (item && item.lore || []).map(plain); }
  function extract(item, label) {
    var row = lines(item).find(function (s) { return s.indexOf(label + '：') >= 0 || s.indexOf(label + ':') >= 0; });
    return row ? row.split(/[：:]/).slice(1).join(':').trim() : '';
  }
  function register() {
    if (!active || !api) return;
    // Welcome cards use HTML text only. Slot interaction binds to data-slot DOM
    // elements independently; an empty render list suppresses the native item layer.
    if (page === 'main') { api.registerSlots([]); return; }
    // Full card / action-button rectangles are native container slot hit regions.
    var rects = [];
    document.querySelectorAll('[data-slot]').forEach(function (el) {
      if (el.disabled) return;
      var r = el.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0) return;
      rects.push({slot:Number(el.getAttribute('data-slot')), x:r.left, y:r.top, w:r.width, h:r.height, size:16});
    });
    api.registerSlots(rects);
  }
  function resize() { fitMenu(); clearTimeout(resizeTimer); resizeTimer = setTimeout(register, 120); }
  function draw(data) {
    if (typeof data === 'string') { try { data = JSON.parse(data); } catch (error) { return; } }
    if (!active || !data || !Array.isArray(data.slots)) return;
    if (containerId != null && String(data.containerId) !== String(containerId)) return;
    var items = {};
    data.slots.forEach(function (item) { items[item.index] = item; });
    if (!items[menu.slots[page === 'main' ? 'create' : 'info']]) return;
    if (window.DreamLore) window.DreamLore.update(data.slots);
    if (page === 'main') {
      var create = items[menu.slots.create], join = items[menu.slots.join];
      var platform = extract(create, '草地平台').match(/(\d+)\s*×\s*(\d+)/);
      if (platform) field('platform', platform[1]);
      var invite = extract(join, '待回应邀约').match(/\d+/); if (invite) field('invites', invite[0]);
      var lore = lines(create).filter(Boolean);
      var description = lore.find(function (s) { return !/[❖▸∟]|平台|点击/.test(s); });
      if (description) field('template-description', description);
      field('template-name', '空岛家园');
    } else {
      var info = items[menu.slots.info], upgrade = items[menu.slots.upgrade];
      field('owner', extract(info, '初始主人')); field('level', extract(info, '空岛等级'));
      var member = extract(info, '共同成员').match(/(\d+)\s*\/\s*(\d+)/);
      if (member) { field('member-count', member[1]); field('member-limit', member[2]); }
      var border = extract(info, '建造边界').match(/\d+/); if (border) field('border', border[0]);
      field('state', extract(info, '当前状态')); field('experience', extract(upgrade, '累计经验'));
      field('required-experience', extract(upgrade, '下一级经验需求'));
      var level = Number(extract(info, '空岛等级')); if (isFinite(level)) field('next-level', level + 1);
      field('upgrade-message', lines(upgrade).find(function (s) { return s.indexOf('暂未开放') >= 0; }));
    }
    register();
  }
  function papi(name, target, callback) {
    if (!api || typeof api.papi !== 'function' || papiPending[name]) return;
    var stamp = generation;
    papiPending[name] = true;
    api.papi('%dreamskyblock_' + name + '%', function (value) {
      if (!active || stamp !== generation) return;
      delete papiPending[name];
      if (typeof value !== 'string' || !value || value.indexOf('%') >= 0) return;
      if (target) field(target, value);
      if (callback) callback(value);
    });
  }
  function refresh(done) {
    var stamp = generation;
    api.getContainerInfo(function (info) {
      if (!active || stamp !== generation) return;
      if (typeof info === 'string') { try { info = JSON.parse(info); } catch (error) { if (done) done(); return; } }
      // The original chest may arrive after the overlay's first frame. Retry in the
      // next poll instead of permanently stopping the page on that first snapshot.
      if (!info || Number(info.slotCount) !== 81) { if (done) done(); return; }
      containerId = info.containerId;
      api.getContainerSlots(function (data) {
        if (!active || stamp !== generation) return;
        draw(data); if (done) done();
      });
    });
    if (page === 'island') {
      papi('current_level_experience', 'current-experience');
      papi('progress_percent', null, function (value) {
        var percent = Number(value); if (!isFinite(percent)) return;
        percent = Math.max(0,Math.min(100,percent)); field('progress', Math.round(percent) + '%');
        document.getElementById('progress-fill').style.width = percent + '%';
        document.getElementById('progress').setAttribute('aria-valuenow', String(Math.round(percent)));
      });
    }
  }
  function start() {
    if (!api || active || !visible) return;
    active = true; generation++;
    var stamp = generation;
    var originalTitle = plain(PDX.getParam('title') || window.__containerTitle);
    if (originalTitle && originalTitle !== menu.title) { stop(); return; }
    fitMenu();
    requestAnimationFrame(function () {
      if (!active || stamp !== generation) return;
      register();
      if (!interactionReady) {
        api.setupSlotInteraction(function () { if (active && poller) poller.trigger(); });
        interactionReady = true;
      }
    });
    poller = PDX.createSingleFlightPoller(refresh, 1000, 2500); poller.start();
    if (typeof api.getPlayerInfo === 'function') api.getPlayerInfo(function (info) {
      if (active && stamp === generation && info && info.name) field('player', info.name);
    });
  }
  function stop() { active = false; generation++; clearTimeout(resizeTimer); if (poller) poller.pause(); poller = null; containerId = null; papiPending = {}; }
  function hide() { visible = false; stop(); }
  function show() { visible = true; start(); }
  document.getElementById('close').addEventListener('click', function () { hide(); if (api) api.close(); });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' || event.key.toLowerCase() === 'e') { event.preventDefault(); hide(); if (api) api.close(); }
  });
  document.addEventListener('paidaxing:pageHide', hide);
  document.addEventListener('paidaxing:pageShow', show);
  document.addEventListener('paidaxing:resize', resize);
  window.addEventListener('resize', resize);
  window.addEventListener('beforeunload', hide);
  PDX.blockContextMenu();
  fitMenu();
  PDX.waitBridge(function () { api = window.PaiDaXing; start(); });
})();
