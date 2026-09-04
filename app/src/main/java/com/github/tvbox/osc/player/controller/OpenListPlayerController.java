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
import com.github.tvbox.osc.player.danmu.HasDanmuIndicator;

import xyz.doikki.videoplayer.controller.BaseVideoController;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * OpenList 视频/音频播放页播放控制器。
 * - 进入全屏后顶部/底部信息栏显示 3 秒后自动隐藏
 * - 只有按遥控器【下键】才重新显示，3 秒无操作再次隐藏
 * - 顶部/底部背景完全透明，无黑色阴影
 * - 底部新增功能按钮行:下一集/上一集/重播/刷新/内核切换/倍速/弹幕/云搜
 */
public class OpenListPlayerController extends BaseVideoController implements HasDanmuIndicator {
    private TextView tvTitle;
    private TextView tvCurTime;
    private TextView tvTotalTime;
    private SeekBar seekBar;
    private ProgressBar loading;
    private ImageView pauseIcon;
    private LinearLayout topRoot;
    private LinearLayout bottomRoot;

    private TextView btnPlayNext;
    private TextView btnPlayPre;
    private TextView btnPlayRetry;
    private TextView btnPlayRefresh;
    private TextView btnPlayPlayer;
    private TextView btnPlaySpeed;
    private TextView btnDanmuSetting;
    private TextView btnDanmuSearch;

    private boolean userSeeking = false;
    private boolean infoVisible = false;
    private boolean hasDanmu = false;

    private static final int SEEK_STEP_MS = 10000;
    private static final int AUTO_HIDE_DELAY_MS = 3000;

    private final Handler mHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hideInfo();
        }
    };

    /** 播放器功能按钮回调,由 Activity 实现具体动作 */
    public interface OpenListControlListener {
        void playNext();

        void playPre();

        void replay();

        void refresh();

        /** 切换播放内核(EXO硬/EXO软/IJK硬/IJK软循环) */
        void switchPlayer();

        void searchDanmuOnline();
    }

    private OpenListControlListener listener;

    public void setListener(OpenListControlListener listener) {
        this.listener = listener;
    }

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

        btnPlayNext     = findViewById(R.id.openlistPlayNext);
        btnPlayPre      = findViewById(R.id.openlistPlayPre);
        btnPlayRetry    = findViewById(R.id.openlistPlayRetry);
        btnPlayRefresh  = findViewById(R.id.openlistPlayRefresh);
        btnPlayPlayer   = findViewById(R.id.openlistPlayPlayer);
        btnPlaySpeed    = findViewById(R.id.openlistPlaySpeed);
        btnDanmuSetting = findViewById(R.id.openlistDanmuSetting);
        btnDanmuSearch  = findViewById(R.id.openlistDanmuSearch);

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

        btnPlayNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.playNext();
                showInfoWithAutoHide();
            }
        });
        btnPlayPre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.playPre();
                showInfoWithAutoHide();
            }
        });
        btnPlayRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.replay();
                showInfoWithAutoHide();
            }
        });
        btnPlayRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.refresh();
                showInfoWithAutoHide();
            }
        });
        btnPlayPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.switchPlayer();
                showInfoWithAutoHide();
            }
        });
        btnPlaySpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSpeed();
                showInfoWithAutoHide();
            }
        });
        btnDanmuSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                com.github.tvbox.osc.ui.dialog.DanmuFullSettingDialog dialog =
                        new com.github.tvbox.osc.ui.dialog.DanmuFullSettingDialog(getContext());
                dialog.show();
                showInfoWithAutoHide();
            }
        });
        btnDanmuSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.searchDanmuOnline();
                showInfoWithAutoHide();
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

    // ───────── 倍速 ─────────

    private float currentSpeed = 1.0f;

    /** 倍速循环:0.5 → 0.75 → 1.0 → 1.25 → 1.5 → 2.0 → 0.5 ... */
    private static final float[] SPEED_STEPS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private void cycleSpeed() {
        int idx = 0;
        for (int i = 0; i < SPEED_STEPS.length; i++) {
            if (Math.abs(SPEED_STEPS[i] - currentSpeed) < 0.001f) {
                idx = i;
                break;
            }
        }
        idx = (idx + 1) % SPEED_STEPS.length;
        currentSpeed = SPEED_STEPS[idx];
        if (mControlWrapper != null) mControlWrapper.setSpeed(currentSpeed);
        updateSpeedText();
    }

    private void updateSpeedText() {
        if (btnPlaySpeed != null) btnPlaySpeed.setText("x" + currentSpeed);
    }

    // ───────── 内核切换按钮文案 ─────────

    public void setPlayerName(String name) {
        if (btnPlayPlayer != null) btnPlayPlayer.setText(name == null ? "" : name);
    }

    // ───────── 上一集/下一集按钮可用性 ─────────

    public void setPlayNextEnabled(boolean enabled) {
        if (btnPlayNext != null) btnPlayNext.setAlpha(enabled ? 1.0f : 0.4f);
    }

    public void setPlayPreEnabled(boolean enabled) {
        if (btnPlayPre != null) btnPlayPre.setAlpha(enabled ? 1.0f : 0.4f);
    }

    // ───────── 弹幕状态 ─────────

    @Override
    public void setHasDanmu(boolean hasDanmu) {
        this.hasDanmu = hasDanmu;
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

    /**
     * 切到新文件(上一集/下一集/刷新)时调用，重置倍速显示状态，
     * 新内核/新 URL 起播后倍速从 1.0 重新开始。
     */
    public void resetForNewFile() {
        currentSpeed = 1.0f;
        updateSpeedText();
    }
}
