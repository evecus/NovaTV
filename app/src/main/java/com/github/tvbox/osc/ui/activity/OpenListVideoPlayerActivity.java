package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.OpenListFsGetData;
import com.github.tvbox.osc.player.controller.OpenListPlayerController;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OpenListApi;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.PlayerSwitchUtil;
import com.orhanobut.hawk.Hawk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * OpenList 视频全屏播放页，复用 TVBox 内置播放器内核 (VideoView + PlayerHelper)。
 */
public class OpenListVideoPlayerActivity extends BaseActivity {
    private VideoView<AbstractPlayer> mVideoView;
    private OpenListPlayerController mController;
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

        mVideoView = findViewById(R.id.mVideoView);
        mController = new OpenListPlayerController(this);
        mController.setTitle(name);
        mVideoView.setVideoController(mController);
        PlayerHelper.updateCfg(mVideoView);

        loadAndPlay();
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
                        mVideoView.setUrl(data.rawUrl, headers);
                        mVideoView.start();
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
        PlayerHelper.updateCfg(mVideoView, next);
        mVideoView.release();
        mVideoView.setUrl(currentPlayUrl, currentPlayHeaders);
        mVideoView.start();
        return true;
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
