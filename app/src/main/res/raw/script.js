/* ========== 基础操作 ========== */
function search() {
    doAction('search', { word: $('#search_key_word').val() });
}

function push() {
    doAction('push', { url: $('#push_url').val() });
}

function doAction(action, kv) {
    kv['do'] = action;
    $('#loadingToast').show();
    $.post('/action', kv, function (data) {
        $('#loadingToast').hide();
        showCfgToast('已推送到电视 ✓');
    }).fail(function () {
        $('#loadingToast').hide();
        showCfgToast('推送失败，请检查连接');
    });
    return false;
}

/* ========== 配置页面三大表单 ========== */

// 仓库/线路配置
function pushRepo() {
    var url  = $('#repo_url').val().trim();
    var name = $('#repo_name').val().trim() || '远程推送';
    if (!url) { showCfgToast('请输入仓库/线路地址'); return false; }
    doAction('api', { url: url, name: name });
    return false;
}

// 直播地址配置
function pushLive() {
    var url  = $('#live_url').val().trim();
    var name = $('#live_name').val().trim() || '直播推送';
    if (!url) { showCfgToast('请输入直播源地址'); return false; }
    doAction('liveApi', { url: url, name: name });
    return false;
}

// OpenList / AList 配置 —— 推送前检测已有配置
function pushOpenList() {
    var server = $('#ol_server').val().trim();
    var user   = $('#ol_user').val().trim();
    var pass   = $('#ol_pass').val();
    if (!server) { showCfgToast('请输入服务器地址'); return false; }
    if (!user)   { showCfgToast('请输入用户名'); return false; }

    var existing = $('#ol_existing').data('info');   // 由 checkOpenListStatus() 写入
    if (existing && existing.logged_in) {
        var msg = '当前已配置：' + existing.username + ' @ ' + existing.server + '\n\n是否覆盖为新配置？';
        if (!confirm(msg)) return false;
    }
    doAction('openlistConfig', { server: server, username: user, password: pass });
    return false;
}

/* ========== OpenList 状态检测 ========== */
function checkOpenListStatus() {
    $.getJSON('/status', function (data) {
        var $tip = $('#ol_existing');
        if (data.logged_in) {
            $tip.text('当前已配置：' + data.username + ' @ ' + data.server)
                .data('info', data)
                .show();
        } else {
            $tip.text('').data('info', null).hide();
        }
    }).fail(function () {
        $('#ol_existing').hide();
    });
}

/* ========== Toast 提示 ========== */
function showCfgToast(msg) {
    var $t = $('#cfgToast');
    $t.text(msg).fadeIn(200);
    setTimeout(function() { $t.fadeOut(400); }, 2000);
}

function warnToast(msg) {
    $('#warnToastContent').html(msg);
    $('#warnToast').show();
    setTimeout(function() { $('#warnToast').hide(); }, 1500);
}

/* ========== Tab 切换 ========== */
function showPanel(id) {
    var tab = $('#tab' + id)[0];
    $(tab).attr('aria-selected', 'true').addClass('weui-bar__item_on');
    $(tab).siblings('.weui-bar__item_on').removeClass('weui-bar__item_on').attr('aria-selected', 'false');
    var panelId = '#' + $(tab).attr('aria-controls');
    $(panelId).css('display', 'block');
    $(panelId).siblings('.weui-tab__panel').css('display', 'none');
    // 切换到配置 tab 时查一次状态
    if (id === 3) checkOpenListStatus();
}

$(function () {
    $('.weui-tabbar__item').on('click', function () {
        showPanel(parseInt($(this).attr('id').substr(3)));
    });

    // 回车触发推送
    $('#repo_url').on('keydown', function(e) { if(e.key==='Enter') pushRepo(); });
    $('#live_url').on('keydown', function(e) { if(e.key==='Enter') pushLive(); });
    $('#ol_pass').on('keydown',  function(e) { if(e.key==='Enter') pushOpenList(); });
});

// 根据 URL 决定默认 tab
var url = window.location.href;
if (url.indexOf('push.html') > 0)
    showPanel(2);
else if (url.indexOf('api.html') > 0 || url.indexOf('all.html') > 0)
    showPanel(3);
else
    showPanel(1);
