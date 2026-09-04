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

    /** 快进快退预览态:ACTION_DOWN 期间只更新预览进度,ACTION_UP 时才真正 seek(与点播页一致) */
    private boolean seekPreviewActive = false;
    private long seekPreviewOffset = 0;
    private long seekPreviewTargetMs = 0;
    private long lastSeekKeyTime = 0;
    private static final int SEEK_BASE_STEP_MS = 10000;
    private static final float SEEK_ACCEL_FACTOR = 2.0f;
    private static final long SEEK_ACCEL_THRESHOLD_MS = 800;

    /**
     * 遥控器按键处理：返回 true 表示已消费该事件，false 表示交还给系统
     * (系统会处理按钮组内的焦点导航等默认行为)。
     *
     * 与点播页保持一致的两种状态：
     * - 底部栏隐藏时：左右键做快进快退预览(长按靠遥控器连续下发 ACTION_DOWN 实现累加加速，
     *   抬起时才真正 seek)；下键唤出底部栏并把焦点定位到第一个按钮上。
     * - 底部栏显示时：左右键交还给系统做按钮间的焦点导航，不再触发 seek。
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (mControlWrapper == null) return false;
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (isInfoVisible()) {
            // 底部栏已显示:左右键交还系统做焦点导航，只拦截确定键/播放暂停键
            if (action == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                togglePlay();
                return true;
            }
            return false;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    togglePlay();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekPreviewStart(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekPreviewStart(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // 只有下键才唤出信息栏，并把焦点定位到第一个按钮上
                    showInfoWithAutoHide();
                    if (btnPlayNext != null) btnPlayNext.requestFocus();
                    return true;
                default:
                    return false;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekPreviewStop();
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    /**
     * 底部栏当前是否真实可见(直接查容器可见性，而非仅凭 infoVisible 旗标)，
     * 仅底部栏可见时才需要把左右键交还系统做焦点导航，与点播页 isBottomVisible() 语义一致。
     */
    private boolean isInfoVisible() {
        return bottomRoot != null && bottomRoot.getVisibility() == View.VISIBLE;
    }

    /**
     * 快进快退预览:只累加内部偏移并刷新进度条/时间文本预览，不真正 seek。
     * 遥控器按住不放时系统会连续下发 ACTION_DOWN，从而不断累加偏移并按阈值加速，
     * 松开(ACTION_UP)时才调用 seekPreviewStop 真正跳转，行为与点播页一致。
     */
    private void seekPreviewStart(int dir) {
        long duration = mControlWrapper.getDuration();
        if (duration <= 0) return;
        long now = System.currentTimeMillis();
        if (!seekPreviewActive) {
            seekPreviewActive = true;
            seekPreviewOffset = (long) SEEK_BASE_STEP_MS * dir;
        } else if (now - lastSeekKeyTime <= SEEK_ACCEL_THRESHOLD_MS) {
            seekPreviewOffset += (long) (SEEK_BASE_STEP_MS * SEEK_ACCEL_FACTOR * dir);
        } else {
            seekPreviewOffset = (long) SEEK_BASE_STEP_MS * dir;
        }
        lastSeekKeyTime = now;
        long current = mControlWrapper.getCurrentPosition();
        long target = current + seekPreviewOffset;
        if (target < 0) target = 0;
        if (target > duration) target = duration;
        seekPreviewTargetMs = target;
        // 预览进度条与时间文本，不真正 seek
        if (seekBar != null) seekBar.setProgress((int) (target * 1000L / duration));
        if (tvCurTime != null) tvCurTime.setText(PlayerUtils.stringForTime(PlayerUtils.safeTimeMs(target)));
        showInfoWithAutoHide();
    }

    /** 松开左右键:真正执行 seek 并恢复播放 */
    private void seekPreviewStop() {
        if (!seekPreviewActive) return;
        mControlWrapper.seekTo(seekPreviewTargetMs);
        if (!mControlWrapper.isPlaying()) mControlWrapper.start();
        seekPreviewActive = false;
        seekPreviewOffset = 0;
        seekPreviewTargetMs = 0;
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
