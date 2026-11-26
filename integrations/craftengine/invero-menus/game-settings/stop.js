(function () {
    var task = session.getVariable('Settings__Task');
    if (task != null) task.cancel();
    session.removeVariable('Settings__Task');
    return true;
})();
