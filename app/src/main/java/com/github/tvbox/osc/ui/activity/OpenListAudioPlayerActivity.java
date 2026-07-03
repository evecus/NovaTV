package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.OpenListFsGetData;
import com.github.tvbox.osc.ui.widget.LrcView;
import com.github.tvbox.osc.util.AudioMetadataLoader;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.OpenListApi;
import com.github.tvbox.osc.util.PlayerHelper;

import java.util.HashMap;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class OpenListAudioPlayerActivity extends BaseActivity {

    private VideoView<AbstractPlayer> mVideoView;
    private ImageView ivAlbumArt;
    private View llNoCover;
    private TextView tvSongName;
    private TextView tvArtistRight;
    private TextView tvCurTime;
    private TextView tvTotalTime;
    private SeekBar seekBar;
    private ProgressBar pbLoading;
    private LrcView lrcView;
    private View audioContentRoot;

    private String path;
    private String name;
    private boolean userSeeking = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVideoView != null && !userSeeking) {
                int position = PlayerUtils.safeTimeMs(mVideoView.getCurrentPosition());
                int duration  = PlayerUtils.safeTimeMs(mVideoView.getDuration());
                if (duration > 0) seekBar.setProgress(position * 1000 / duration);
                tvCurTime.setText(PlayerUtils.stringForTime(position));
                tvTotalTime.setText(PlayerUtils.stringForTime(duration));
                lrcView.updateProgress(position);
            }
            mHandler.postDelayed(this, 250);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_audio_player;
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

        mVideoView      = findViewById(R.id.mVideoView);
        ivAlbumArt      = findViewById(R.id.ivAlbumArt);
        llNoCover       = findViewById(R.id.llNoCover);
        tvSongName      = findViewById(R.id.tvSongName);
        tvArtistRight   = findViewById(R.id.tvArtistRight);
        tvCurTime       = findViewById(R.id.tvCurTime);
        tvTotalTime     = findViewById(R.id.tvTotalTime);
        seekBar         = findViewById(R.id.seekBar);
        pbLoading       = findViewById(R.id.pbAudioLoading);
        lrcView         = findViewById(R.id.lrcView);
        audioContentRoot = findViewById(R.id.audioContentRoot);

        tvSongName.setText(stripExtension(name));
        lrcView.setEmptyText("暂无歌词");

        PlayerHelper.updateCfg(mVideoView);

        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        pbLoading.setVisibility(View.VISIBLE);
                        break;
                    default:
                        pbLoading.setVisibility(View.GONE);
                        break;
                }
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mVideoView != null) {
                    long dur = mVideoView.getDuration();
                    tvCurTime.setText(PlayerUtils.stringForTime(
                            PlayerUtils.safeTimeMs(dur * progress / 1000)));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                if (mVideoView != null)
                    mVideoView.seekTo(mVideoView.getDuration() * sb.getProgress() / 1000);
                userSeeking = false;
            }
        });

        mHandler.post(mProgressRunnable);
        audioContentRoot.post(() -> audioContentRoot.requestFocus());

        loadAndPlay();
    }

    // ─── 加载播放地址 ─────────────────────────────────────────────────────────

    private void loadAndPlay() {
        OpenListApi.getFile(path, new OpenListApi.Callback<OpenListFsGetData>() {
            @Override
            public void onSuccess(OpenListFsGetData data) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    if (TextUtils.isEmpty(data.rawUrl)) {
                        Toast.makeText(mContext, "未获取到播放地址", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    Map<String, String> headers = new HashMap<>();
                    String token = OpenListApi.getToken();
                    if (!TextUtils.isEmpty(token)) headers.put("Authorization", token);

                    // 开始播放
                    mVideoView.setUrl(data.rawUrl, headers);
                    mVideoView.start();

                    // 用 OkHttp Range 请求读取 ID3 tag（只下载前 128KB）
                    AudioMetadataLoader.loadAsync(
                            data.rawUrl, headers, OkGoHelper.getDefaultClient(),
                            new AudioMetadataLoader.Callback() {
                                @Override
                                public void onLoaded(AudioMetadataLoader.Metadata meta) {
                                    runOnUiThread(() -> {
                                        if (isActivityUnavailable()) return;
                                        applyMetadata(meta);
                                    });
                                }
                                @Override
                                public void onError(String msg) {
                                    // 元数据读取失败不影响播放，保留文件名
                                }
                            });
                });
            }
            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    Toast.makeText(mContext,
                            TextUtils.isEmpty(msg) ? "获取播放地址失败" : msg,
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    // ─── 应用元数据到 UI ──────────────────────────────────────────────────────

    private void applyMetadata(AudioMetadataLoader.Metadata meta) {
        if (!TextUtils.isEmpty(meta.title)) {
            tvSongName.setText(meta.title);
        }
        if (!TextUtils.isEmpty(meta.artist)) {
            String display = TextUtils.isEmpty(meta.album)
                    ? meta.artist : meta.artist + " · " + meta.album;
            tvArtistRight.setText(display);
            tvArtistRight.setVisibility(View.VISIBLE);
        }
        if (meta.cover != null) {
            ivAlbumArt.setImageBitmap(meta.cover);
            ivAlbumArt.setVisibility(View.VISIBLE);
            llNoCover.setVisibility(View.GONE);
        } else {
            ivAlbumArt.setVisibility(View.GONE);
            llNoCover.setVisibility(View.VISIBLE);
        }
        lrcView.setLrc(meta.lyrics); // null 或空时 LrcView 显示"暂无歌词"
    }

    // ─── 播放控制 ─────────────────────────────────────────────────────────────

    private void togglePlay() {
        if (mVideoView == null) return;
        if (mVideoView.isPlaying()) mVideoView.pause(); else mVideoView.start();
    }

    private void seekBy(int deltaMs) {
        if (mVideoView == null) return;
        long cur = mVideoView.getCurrentPosition();
        long dur = mVideoView.getDuration();
        long target = Math.max(0, cur + deltaMs);
        if (dur > 0) target = Math.min(target, dur);
        mVideoView.seekTo(target);
    }

    // ─── 遥控器 ───────────────────────────────────────────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    togglePlay(); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekBy(-10000); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekBy(10000); return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_UP
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish(); return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ─── 生命周期 ─────────────────────────────────────────────────────────────

    @Override protected void onPause()   { super.onPause();   if (mVideoView != null) mVideoView.pause(); }
    @Override protected void onResume()  { super.onResume();  if (mVideoView != null) mVideoView.resume();
                                           if (audioContentRoot != null) audioContentRoot.requestFocus(); }
    @Override protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mVideoView != null) { mVideoView.release(); mVideoView = null; }
        super.onDestroy();
    }

    // ─── 工具 ────────────────────────────────────────────────────────────────

    private String stripExtension(String filename) {
        if (TextUtils.isEmpty(filename)) return "";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private boolean isActivityUnavailable() {
        return isFinishing() ||
                (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                        && isDestroyed());
    }
}
