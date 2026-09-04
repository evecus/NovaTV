package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.OpenListFile;
import com.github.tvbox.osc.bean.OpenListFsGetData;
import com.github.tvbox.osc.bean.OpenListFsListData;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.controller.OpenListPlayerController;
import com.github.tvbox.osc.player.danmu.DanmuLoadController;
import com.github.tvbox.osc.ui.dialog.SearchDanmuDialog;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OpenListApi;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.PlayerSwitchUtil;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import master.flame.danmaku.ui.widget.DanmakuView;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * OpenList 视频全屏播放页，复用 TVBox 内置播放器内核 (VideoView + PlayerHelper)。
 */
public class OpenListVideoPlayerActivity extends BaseActivity {
    private MyVideoView mVideoView;
    private OpenListPlayerController mController;
    private DanmuLoadController danmuLoadController;
    private DanmakuView mDanmuView;
    private String path;
    private String name;

    // ── 播放失败自动切内核重试 ────────────────────────────────────────────────
    /** 已尝试过的播放内核(0=EXO硬解 1=EXO软解 2=IJK硬解 3=IJK软解),失败时按序切换 */
    private final Set<Integer> triedPlayerTypes = new HashSet<>();
    /** 当前正在使用的内核档位,首次播放时按全局 PLAY_TYPE 初始化 */
    private int currentPlayType = -1;
    /** 当前播放 URL 与请求头,切内核重试时复用 */
    private String currentPlayUrl;
    private Map<String, String> currentPlayHeaders = new HashMap<>();

    // ── 同目录上一集/下一集 ──────────────────────────────────────────────────
    /** 当前文件所在目录下的兄弟视频文件列表(自然排序后),用于上一集/下一集切换 */
    private final List<OpenListFile> siblingVideos = new ArrayList<>();
    /** 当前文件在 siblingVideos 中的位置,-1 表示尚未加载或未找到 */
    private int currentIndex = -1;
    private boolean siblingsLoaded = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_video_player;
    }

    @Override
    protected void init() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Bundle bundle = getIntent() != null ? getIntent().getExtras() : null;
        path = bundle != null ? bundle.getString("path", "") : "";
        name = bundle != null ? bundle.getString("name", "") : "";

        if (TextUtils.isEmpty(path)) {
            Toast.makeText(mContext, "文件路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        EventBus.getDefault().register(this);

        mVideoView = findViewById(R.id.mVideoView);
        mDanmuView = findViewById(R.id.danmaku);
        mController = new OpenListPlayerController(this);
        mController.setTitle(name);
        mController.setListener(new OpenListPlayerController.OpenListControlListener() {
            @Override
            public void playNext() {
                switchSibling(1);
            }

            @Override
            public void playPre() {
                switchSibling(-1);
            }

            @Override
            public void replay() {
                if (mVideoView == null) return;
                if (mVideoView.getCurrentPlayState() == VideoView.STATE_PLAYBACK_COMPLETED
                        || mVideoView.getCurrentPlayState() == VideoView.STATE_ERROR) {
                    loadAndPlay();
                } else {
                    mVideoView.seekTo(0);
                    mVideoView.start();
                }
            }

            @Override
            public void refresh() {
                loadAndPlay();
            }

            @Override
            public void switchPlayer() {
                int next = currentPlayType < 0
                        ? PlayerSwitchUtil.normalizePlayType(Hawk.get(HawkConfig.PLAY_TYPE, 2))
                        : currentPlayType;
                next = (next + 1) % 4;
                currentPlayType = next;
                triedPlayerTypes.clear();
                mController.setPlayerName(PlayerHelper.getPlayerName(currentPlayType));
                if (currentPlayUrl != null) {
                    PlayerHelper.updateCfg(mVideoView, currentPlayType);
                    mVideoView.release();
                    mVideoView.setUrl(currentPlayUrl, currentPlayHeaders);
                    mVideoView.start();
                }
            }

            @Override
            public void searchDanmuOnline() {
                SearchDanmuDialog dialog = new SearchDanmuDialog(mContext);
                dialog.setDanmuLoader(new SearchDanmuDialog.DanmuLoader() {
                    @Override
                    public void loadDanmu(String danmu) {
                        if (danmuLoadController != null) {
                            danmuLoadController.check(danmu, currentSearchWord(), "");
                        }
                    }
                });
                dialog.setEpisode("");
                dialog.setSearchWord(currentSearchWord());
                dialog.show();
            }
        });
        mVideoView.setVideoController(mController);
        danmuLoadController = new DanmuLoadController(mVideoView, mController, mDanmuView);
        PlayerHelper.updateCfg(mVideoView);

        loadAndPlay();
        loadSiblingVideos();
    }

    /** 用于弹幕匹配/云搜的关键词:取当前文件名(去掉扩展名) */
    private String currentSearchWord() {
        if (TextUtils.isEmpty(name)) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void loadAndPlay() {
        OpenListApi.getFile(path, new OpenListApi.Callback<OpenListFsGetData>() {
            @Override
            public void onSuccess(final OpenListFsGetData data) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) return;
                        if (data.rawUrl == null || data.rawUrl.isEmpty()) {
                            Toast.makeText(mContext, "未获取到播放地址", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        Map<String, String> headers = new HashMap<>();
                        String token = OpenListApi.getToken();
                        if (!TextUtils.isEmpty(token)) headers.put("Authorization", token);
                        // 新文件从头开始:重置内核尝试状态,用默认内核起播
                        currentPlayUrl = data.rawUrl;
                        currentPlayHeaders = headers;
                        triedPlayerTypes.clear();
                        currentPlayType = -1;
                        setupErrorRetry();
                        mController.resetForNewFile();
                        mController.setPlayerName(PlayerHelper.getPlayerName(
                                PlayerSwitchUtil.normalizePlayType(Hawk.get(HawkConfig.PLAY_TYPE, 2))));
                        mVideoView.setUrl(data.rawUrl, headers);
                        mVideoView.start();
                        if (danmuLoadController != null) {
                            danmuLoadController.check(currentSearchWord(), currentSearchWord(), "");
                        }
                    }
                });
            }

            @Override
            public void onError(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) return;
                        Toast.makeText(mContext, TextUtils.isEmpty(msg) ? "获取播放地址失败" : msg, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        });
    }

    // ───────── 同目录上一集/下一集 ─────────

    private String parentPathOf(String filePath) {
        if (TextUtils.isEmpty(filePath)) return "/";
        int idx = filePath.lastIndexOf('/');
        if (idx <= 0) return "/";
        return filePath.substring(0, idx);
    }

    private void loadSiblingVideos() {
        String parent = parentPathOf(path);
        OpenListApi.listFiles(parent, new OpenListApi.Callback<OpenListFsListData>() {
            @Override
            public void onSuccess(final OpenListFsListData data) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) return;
                        siblingVideos.clear();
                        if (data.content != null) {
                            for (OpenListFile f : data.content) {
                                f.parentPath = parent;
                                if (f.isVideo()) siblingVideos.add(f);
                            }
                        }
                        Collections.sort(siblingVideos, new Comparator<OpenListFile>() {
                            @Override
                            public int compare(OpenListFile a, OpenListFile b) {
                                return naturalCompare(a.name, b.name);
                            }
                        });
                        currentIndex = -1;
                        for (int i = 0; i < siblingVideos.size(); i++) {
                            if (siblingVideos.get(i).fullPath().equals(path)) {
                                currentIndex = i;
                                break;
                            }
                        }
                        siblingsLoaded = true;
                        updateSiblingButtons();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                // 目录列表加载失败:静默处理,上一集/下一集按钮保持置灰即可,不打断当前播放
                LOG.i("echo-openlist siblings load failed: " + msg);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        siblingsLoaded = true;
                        updateSiblingButtons();
                    }
                });
            }
        });
    }

    private void updateSiblingButtons() {
        if (mController == null) return;
        boolean hasNext = siblingsLoaded && currentIndex >= 0 && currentIndex < siblingVideos.size() - 1;
        boolean hasPre = siblingsLoaded && currentIndex > 0;
        mController.setPlayNextEnabled(hasNext);
        mController.setPlayPreEnabled(hasPre);
    }

    /** 切换到上一个(-1)/下一个(+1)兄弟视频文件 */
    private void switchSibling(int delta) {
        if (!siblingsLoaded) {
            Toast.makeText(mContext, "目录信息加载中,请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentIndex < 0) {
            Toast.makeText(mContext, "未能定位当前文件在目录中的位置", Toast.LENGTH_SHORT).show();
            return;
        }
        int target = currentIndex + delta;
        if (target < 0) {
            Toast.makeText(mContext, "已经是第一个文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (target >= siblingVideos.size()) {
            Toast.makeText(mContext, "已经是最后一个文件", Toast.LENGTH_SHORT).show();
            return;
        }
        OpenListFile targetFile = siblingVideos.get(target);
        currentIndex = target;
        path = targetFile.fullPath();
        name = targetFile.name;
        mController.setTitle(name);
        updateSiblingButtons();
        if (danmuLoadController != null) danmuLoadController.reset();
        loadAndPlay();
    }

    /**
     * 文件名自然排序:把连续数字整体当作数值比较,让 "第2集" 排在 "第10集" 之前，
     * 而不是按字符逐位比较导致 "第10集" 排到 "第2集" 前面。
     */
    private int naturalCompare(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int ia = 0, ib = 0;
        while (ia < a.length() && ib < b.length()) {
            char ca = a.charAt(ia);
            char cb = b.charAt(ib);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = ia, startB = ib;
                while (ia < a.length() && Character.isDigit(a.charAt(ia))) ia++;
                while (ib < b.length() && Character.isDigit(b.charAt(ib))) ib++;
                String numA = a.substring(startA, ia).replaceFirst("^0+(?=.)", "");
                String numB = b.substring(startB, ib).replaceFirst("^0+(?=.)", "");
                if (numA.length() != numB.length()) return numA.length() - numB.length();
                int cmp = numA.compareTo(numB);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.toLowerCase(ca) - Character.toLowerCase(cb);
                if (cmp != 0) return cmp;
                ia++;
                ib++;
            }
        }
        return (a.length() - ia) - (b.length() - ib);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mController != null && mController.handleKeyEvent(event)) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** 注册播放失败自动切内核重试监听(每次开始播新文件前调用) */
    private void setupErrorRetry() {
        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_PLAYING:
                        // 播放成功:重置内核尝试状态,若中途再次失败可重新按序尝试其余内核
                        triedPlayerTypes.clear();
                        if (danmuLoadController != null) danmuLoadController.startIfReady();
                        break;
                    case VideoView.STATE_ERROR:
                        // 播放失败:按顺序尝试其余三个内核,全部试完才提示
                        if (!retryWithNextPlayer()) {
                            Toast.makeText(mContext, "播放出错", Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }
        });
    }

    /**
     * 播放失败时按固定顺序 0→1→2→3 尝试其余三个内核,切换到下一个并重播当前 URL。
     *
     * @return true 已切换内核并重新播放;false 其余三个内核都已试过(或为网络类错误),停止尝试
     */
    private boolean retryWithNextPlayer() {
        if (mVideoView == null || currentPlayUrl == null) return false;
        // 网络原因访问不了播放地址(IO/超时/服务器不可达):切换播放内核无意义,直接停止尝试
        if (mVideoView.getLastErrorType() == AbstractPlayer.PlayerEventListener.ERROR_TYPE_NETWORK) {
            LOG.i("echo-openlistAutoRetry network error, skip player switch");
            return false;
        }
        if (currentPlayType < 0) currentPlayType = PlayerSwitchUtil.normalizePlayType(Hawk.get(HawkConfig.PLAY_TYPE, 2));
        int next = PlayerSwitchUtil.nextPlayerType(currentPlayType, triedPlayerTypes);
        if (next < 0) {
            // 全部内核都试过:重置,下次播放从头开始
            triedPlayerTypes.clear();
            currentPlayType = -1;
            return false;
        }
        currentPlayType = next;
        LOG.i("echo-openlistAutoRetry switch player: " + next);
        if (mController != null) mController.setPlayerName(PlayerHelper.getPlayerName(currentPlayType));
        PlayerHelper.updateCfg(mVideoView, next);
        mVideoView.release();
        mVideoView.setUrl(currentPlayUrl, currentPlayHeaders);
        mVideoView.start();
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SET_DANMU_SETTINGS) {
            if (danmuLoadController != null) {
                danmuLoadController.applySettings(event.obj instanceof Boolean && (Boolean) event.obj);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) mVideoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null) mVideoView.resume();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        if (danmuLoadController != null) {
            danmuLoadController.destroy();
            danmuLoadController = null;
        }
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        super.onDestroy();
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }
}
