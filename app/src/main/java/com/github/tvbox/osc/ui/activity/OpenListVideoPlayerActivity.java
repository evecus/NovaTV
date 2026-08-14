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
import com.github.tvbox.osc.util.OpenListApi;
import com.github.tvbox.osc.util.PlayerHelper;

import java.util.HashMap;
import java.util.Map;

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
