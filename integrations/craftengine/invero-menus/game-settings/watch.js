// Runs on the player's EntityScheduler. Read cached preferences only; no disk/SQL access.
(function () {
    var Bukkit = Java.type('org.bukkit.Bukkit');
    var Consumer = Java.type('java.util.function.Consumer');
    var Runnable = Java.type('java.lang.Runnable');
    var StaticClass = Java.type('jdk.dynalink.beans.StaticClass');
    var original = session;
    var viewer = player;
    var owner = Bukkit.getPluginManager().getPlugin('Invero');
    var SessionType = StaticClass.forClass(original.getClass().getClassLoader().loadClass('cc.trixey.invero.core.Session'));
    var TitleHandler = StaticClass.forClass(original.getClass().getClassLoader().loadClass('cc.trixey.invero.ui.bukkit.nms.HandlerKt'));
    var previous = original.getVariable('Settings__Task');
    if (previous != null) previous.cancel();

    function plugin(name) {
        var p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled() ? p : null;
    }
    function hologram() {
        var p = plugin('Genlite');
        if (p == null || !p.hologramsReady()) return 'unknown';
        return p.hologramsEnabled(viewer.getUniqueId()) ? 'on' : 'off';
    }
    function income() {
        var p = plugin('Genlite');
        if (p == null || !p.saleNoticesReady(viewer.getUniqueId())) return 'unknown';
        return p.saleNoticesEnabled(viewer.getUniqueId()) ? 'on' : 'off';
    }
    function tracking() {
        var p = plugin('Quest');
        if (p == null) return 'unknown';
        // Installed Quest 1.0.0 exposes no public preference API. These two exact
        // cached-field signatures are verified against the installed JAR.
        var runtimeField = p.getClass().getDeclaredField('runtime');
        runtimeField.setAccessible(true);
        var runtime = runtimeField.get(p);
        if (runtime == null) return 'unknown';
        var prefsField = runtime.getClass().getDeclaredField('preferences');
        prefsField.setAccessible(true);
        var prefs = prefsField.get(runtime).get(viewer);
        return prefs == null ? 'unknown' : String(prefs.getMode().name()) === 'OFF' ? 'off' : 'on';
    }
    function read(kind, fn) {
        try { return fn(); }
        catch (error) {
            var key = 'Settings__Error_' + kind;
            if (original.getVariable(key) == null) {
                original.setVariable(key, true);
                owner.getLogger().warning('Game settings state read failed: menu=游戏设置, player=' + viewer.getUniqueId() + ', setting=' + kind + ', cause=' + error);
            }
            return 'unknown';
        }
    }
    var hChars = {on: '\ue921', off: '\ue922', unknown: '\ue923'};
    var qChars = {on: '\ue924', off: '\ue925', unknown: '\ue926'};
    var sChars = {on: '\ue929', off: '\ue92a', unknown: '\ue92b'};
    function title(h, q, s) {
        return '<#FFFFFF><font:dreamskyblock_menus:space>\ue912</font>' +
            '<font:dreamskyblock_menus:settings>\ue920</font>' +
            '<font:dreamskyblock_menus:settings_space>\ue927</font>' +
            '<font:dreamskyblock_menus:settings>' + hChars[h] + '</font>' +
            '<font:dreamskyblock_menus:settings_space>\ue928</font>' +
            '<font:dreamskyblock_menus:settings>' + qChars[q] + '</font>' +
            '<font:dreamskyblock_menus:settings_space>\ue928</font>' +
            '<font:dreamskyblock_menus:settings>' + sChars[s] + '</font>';
    }
    function tick(task) {
        var current = SessionType.Companion.getSession(original.getViewer());
        if (!owner.isEnabled() || !viewer.isOnline() || current == null || !current.equals(original)) {
            task.cancel();
            original.removeVariable('Settings__Task');
            return;
        }
        // Invero suppresses setTitle packets while the opening/previous title
        // update is in flight (two ticks). Do not commit the state until it can
        // be sent; an unchanged preference must still retry after that lock.
        if (TitleHandler.isTitleUpdating(original.getViewer())) return;
        if (!original.getWindow().isViewing()) {
            task.cancel();
            original.removeVariable('Settings__Task');
            return;
        }
        var h = read('holograms', hologram), q = read('tracking', tracking), s = read('income', income);
        var key = h + '/' + q + '/' + s;
        if (String(original.getVariable('Settings__State')) !== key) {
            original.getWindow().setTitle(title(h, q, s));
            original.setVariable('Settings__State', key);
        }
    }
    var task = viewer.getScheduler().runAtFixedRate(owner, new Consumer(function (task) {
        try { tick(task); }
        catch (error) {
            task.cancel();
            original.removeVariable('Settings__Task');
            owner.getLogger().warning('Game settings refresh stopped: menu=游戏设置, player=' + viewer.getUniqueId() + ', cause=' + error);
        }
    }), new Runnable(function () { original.removeVariable('Settings__Task'); }), 4, 10);
    if (task != null) original.setVariable('Settings__Task', task);
    return true;
})();