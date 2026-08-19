package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import xyz.doikki.videoplayer.controller.BaseVideoController;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * 极简播放控制器，专用于 OpenList 视频/音频播放页。
 * - 进入全屏后顶部/底部信息栏显示 3 秒后自动隐藏
 * - 只有按遥控器【下键】才重新显示，3 秒无操作再次隐藏
 * - 顶部/底部背景完全透明，无黑色阴影
 */
public class OpenListPlayerController extends BaseVideoController {
    private TextView tvTitle;
    private TextView tvCurTime;
    private TextView tvTotalTime;
    private SeekBar seekBar;
    private ProgressBar loading;
    private ImageView pauseIcon;
    private LinearLayout topRoot;
    private LinearLayout bottomRoot;

    private boolean userSeeking = false;
    private boolean infoVisible = false;

    private static final int SEEK_STEP_MS = 10000;
    private static final int AUTO_HIDE_DELAY_MS = 3000;

    private final Handler mHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hideInfo();
        }
    };

    public OpenListPlayerController(@NonNull Context context) {
        super(context);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.player_openlist_control_view;
    }

    @Override
    protected void initView() {
        super.initView();
        tvTitle    = findViewById(R.id.openlistPlayTitle);
        tvCurTime  = findViewById(R.id.openlistCurTime);
        tvTotalTime = findViewById(R.id.openlistTotalTime);
        seekBar    = findViewById(R.id.openlistSeekBar);
        loading    = findViewById(R.id.openlistLoading);
        pauseIcon  = findViewById(R.id.openlistPauseIcon);
        topRoot    = findViewById(R.id.openlistTopRoot);
        bottomRoot = findViewById(R.id.openlistBottomRoot);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mControlWrapper != null) {
                    long duration = mControlWrapper.getDuration();
                    long newPos = duration * progress / 1000;
                    tvCurTime.setText(PlayerUtils.stringForTime(PlayerUtils.safeTimeMs(newPos)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mControlWrapper != null) {
                    long duration = mControlWrapper.getDuration();
                    long newPos = duration * seekBar.getProgress() / 1000;
                    mControlWrapper.seekTo(newPos);
                }
                userSeeking = false;
            }
        });

        // 进入页面后延一帧显示信息栏（等控制器完全 attach 后）
        post(new Runnable() {
            @Override
            public void run() {
                showInfoWithAutoHide();
            }
        });
    }

    public void setTitle(String title) {
        if (tvTitle != null) tvTitle.setText(title == null ? "" : title);
    }

    // ───────── 显示 / 隐藏 ─────────

    /** 显示顶部+底部信息栏，并启动 3 秒自动隐藏计时 */
    private void showInfoWithAutoHide() {
        if (topRoot == null || bottomRoot == null) return;
        mHideHandler.removeCallbacks(mHideRunnable);
        if (!infoVisible) {
            infoVisible = true;
            fadeIn(topRoot);
            fadeIn(bottomRoot);
        }
        mHideHandler.postDelayed(mHideRunnable, AUTO_HIDE_DELAY_MS);
    }

    /** 隐藏顶部+底部信息栏 */
    private void hideInfo() {
        if (topRoot == null || bottomRoot == null) return;
        infoVisible = false;
        fadeOut(topRoot);
        fadeOut(bottomRoot);
    }

    private void fadeIn(final View v) {
        if (v.getVisibility() == View.VISIBLE) return;
        v.clearAnimation();
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(250);
        v.startAnimation(anim);
        v.setVisibility(View.VISIBLE);
    }

    private void fadeOut(final View v) {
        if (v.getVisibility() != View.VISIBLE) return;
        v.clearAnimation();
        AlphaAnimation anim = new AlphaAnimation(1f, 0f);
        anim.setDuration(250);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) {
                v.setVisibility(View.GONE);
            }
        });
        v.startAnimation(anim);
    }

    // ───────── 播放状态 ─────────

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        switch (playState) {
            case VideoView.STATE_IDLE:
            case VideoView.STATE_PLAYBACK_COMPLETED:
            case VideoView.STATE_ERROR:
                loading.setVisibility(GONE);
                pauseIcon.setVisibility(GONE);
                stopProgress();
                break;
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_BUFFERING:
                loading.setVisibility(VISIBLE);
                pauseIcon.setVisibility(GONE);
                break;
            case VideoView.STATE_PREPARED:
            case VideoView.STATE_BUFFERED:
                loading.setVisibility(GONE);
                break;
            case VideoView.STATE_PLAYING:
                loading.setVisibility(GONE);
                pauseIcon.setVisibility(GONE);
                startProgress();
                break;
            case VideoView.STATE_PAUSED:
                loading.setVisibility(GONE);
                pauseIcon.setVisibility(VISIBLE);
                stopProgress();
                // 暂停时显示信息栏方便用户看到进度
                showInfoWithAutoHide();
                break;
        }
    }

    @Override
    protected void setProgress(int duration, int position) {
        super.setProgress(duration, position);
        if (userSeeking) return;
        if (duration > 0) {
            seekBar.setProgress((int) (position * 1000L / duration));
        }
        tvCurTime.setText(PlayerUtils.stringForTime(position));
        tvTotalTime.setText(PlayerUtils.stringForTime(duration));
    }

    // ───────── 遥控器按键 ─────────

    /**
     * 遥控器按键处理：返回 true 表示已消费该事件
     * 【只有按下键】才唤出信息栏
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (mControlWrapper == null) return false;
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePlay();
                // 播放/暂停不主动唤出信息栏（暂停时 onPlayStateChanged 会唤出）
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-SEEK_STEP_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(SEEK_STEP_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // 只有下键才唤出信息栏
                showInfoWithAutoHide();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                // 上键不处理，交给系统/Activity
                return false;
            default:
                return false;
        }
    }

    private void seekBy(int deltaMs) {
        long cur = mControlWrapper.getCurrentPosition();
        long duration = mControlWrapper.getDuration();
        long target = cur + deltaMs;
        if (target < 0) target = 0;
        if (duration > 0 && target > duration) target = duration;
        mControlWrapper.seekTo(target);
        // seek 时刷新信息栏计时，让用户看到进度变化
        showInfoWithAutoHide();
    }
}
