package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.dlna.DlnaRendererManager;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.controller.OpenListPlayerController;
import com.github.tvbox.osc.util.PlayerHelper;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * DLNA 投屏播放页（裸 URL 直接播放，不依赖 push_agent）。
 * - 复用 OpenListPlayerController：进度条拖动、左右键快进/快退、播放/暂停
 * - 播放器跟随设置页"播放器"设置（默认 IJK，可切 EXO）
 * 由 HomeActivity 在无推送源时启动，URL 通过 Intent / TYPE_PUSH_URL 事件传入。
 * 另每 500ms 向 DlnaRendererManager 上报真实进度,供投屏端 GetPositionInfo 查询/拖动。
 */
public class CastPlayActivity extends BaseActivity {

    private VideoView<AbstractPlayer> mVideoView;
    private OpenListPlayerController mController;
    private String currentUrl;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVideoView != null) {
                DlnaRendererManager.get().updatePlaybackState(
                        mVideoView.getCurrentPosition(),
                        mVideoView.getDuration());
            }
            progressHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_cast_play;
    }

    @Override
    protected void init() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mVideoView = findViewById(R.id.mVideoView);
        mController = new OpenListPlayerController(this);
        mController.setTitle("投屏播放");
        mVideoView.setVideoController(mController);
        // 跟随设置页"播放器"设置（默认 IJK，设置页可切 EXO）
        PlayerHelper.updateCfg(mVideoView);
        playUrl(getIntentUrl());
        progressHandler.post(progressRunnable);
    }

    private String getIntentUrl() {
        Intent intent = getIntent();
        if (intent != null) return intent.getStringExtra("url");
        return null;
    }

    private void playUrl(String url) {
        currentUrl = url;
        if (mVideoView == null || url == null || url.isEmpty()) return;
        mVideoView.release();
        // 跟随设置页"播放器"设置
        PlayerHelper.updateCfg(mVideoView);
        mVideoView.setUrl(url, null);
        mVideoView.start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // 遥控器控制：进度条/快进快退/播放暂停（下键唤出信息栏）
        if (mController != null && mController.handleKeyEvent(event)) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        DlnaRendererManager.get().updatePlaybackState(0, 0);
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_CAST_PLAY) {
            if (mVideoView != null && currentUrl != null) mVideoView.start();
        } else if (event.type == RefreshEvent.TYPE_CAST_PAUSE) {
            if (mVideoView != null && mVideoView.isPlaying()) mVideoView.pause();
        } else if (event.type == RefreshEvent.TYPE_CAST_STOP) {
            finish();
        } else if (event.type == RefreshEvent.TYPE_CAST_SEEK) {
            if (mVideoView != null && event.obj instanceof Long) {
                mVideoView.seekTo((Long) event.obj);
            }
        } else if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            // 后续投屏推送新 URL：替换播放
            playUrl(event.obj == null ? null : event.obj.toString());
        }
    }
}
