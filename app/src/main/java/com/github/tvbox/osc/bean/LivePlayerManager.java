package com.github.tvbox.osc.bean;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 直播播放器管理：直播播放器设置全局独立（所有频道共用），
 * 用户在直播页"播放解码"里选的播放器保存为直播全局默认（LIVE_PLAY_TYPE + LIVE_IJK_CODEC），
 * 不跟随点播设置，也不按频道记忆。
 */
public class LivePlayerManager {
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;

    public void init(VideoView videoView) {
        try {
            // 直播默认播放内核：1=IJK（0=系统播放器已移除 1=IJK 2=EXO），默认 IJK 硬解
            int livePlayType = Hawk.get(HawkConfig.LIVE_PLAY_TYPE, 1);
            if (livePlayType == 0) livePlayType = 1; // 兼容历史系统播放器配置，归一化为 IJK
            defaultPlayerConfig.put("pl", livePlayType);
            defaultPlayerConfig.put("ijk", Hawk.get(HawkConfig.LIVE_IJK_CODEC, "硬解码"));
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(VideoView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 所有频道统一使用直播全局默认播放器（不按频道记忆）
     */
    public void getLiveChannelPlayer(VideoView videoView, String channelName) {
        if (currentPlayerConfig == null || !currentPlayerConfig.toString().equals(defaultPlayerConfig.toString())) {
            getDefaultLiveChannelPlayer(videoView);
        } else {
            videoView.setScreenScaleType(Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
        }
    }

    public int getLivePlayerType() {
        int playerTypeIndex = 0;
        try {
            int playerType = currentPlayerConfig.getInt("pl");
            String ijkCodec = currentPlayerConfig.getString("ijk");
            switch (playerType) {
                case 1:
                    // ijk硬解 -> 0, ijk软解 -> 1
                    playerTypeIndex = ijkCodec.equals("硬解码") ? 0 : 1;
                    break;
                case 2:
                    playerTypeIndex = 2;
                    break;
                case 0:
                default:
                    // 历史系统播放器配置按 ijk 硬解显示
                    playerTypeIndex = 0;
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return playerTypeIndex;
    }

    public int getLivePlayerScale() {
        try {
            return currentPlayerConfig.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 直播页切换播放器：写入直播全局设置，所有频道共用
     */
    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName) {
        try {
            switch (playerType) {
                case 0:
                    defaultPlayerConfig.put("pl", 1);
                    defaultPlayerConfig.put("ijk", "硬解码");
                    break;
                case 1:
                    defaultPlayerConfig.put("pl", 1);
                    defaultPlayerConfig.put("ijk", "软解码");
                    break;
                case 2:
                    defaultPlayerConfig.put("pl", 2);
                    defaultPlayerConfig.put("ijk", "软解码");
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // 直播全局保存，不影响点播/全局播放器设置
        Hawk.put(HawkConfig.LIVE_PLAY_TYPE, defaultPlayerConfig.optInt("pl"));
        Hawk.put(HawkConfig.LIVE_IJK_CODEC, defaultPlayerConfig.optString("ijk"));
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 缓冲超时自动切换内核（IJK <-> EXO）：同样写入直播全局设置
     */
    public boolean switchLivePlayer(VideoView videoView, String channelName) {
        if (defaultPlayerConfig == null) {
            LOG.i("echo-liveSwitchPlayer: skip empty player config");
            return false;
        }
        try {
            int playerType = defaultPlayerConfig.getInt("pl");
            if (playerType == 0) playerType = 1; // 历史系统播放器配置归一化为 IJK
            int switchPlayerType = (playerType == 1) ? 2 : (playerType == 2) ? 1 : playerType;
            if (switchPlayerType == playerType) {
                LOG.i("echo-liveSwitchPlayer: skip unsupported playerType=" + playerType);
                return false;
            }
            LOG.i("echo-liveSwitchPlayer: " + playerType + " -> " + switchPlayerType);
            defaultPlayerConfig.put("pl", switchPlayerType);
            Hawk.put(HawkConfig.LIVE_PLAY_TYPE, switchPlayerType);
        } catch (JSONException e) {
            LOG.i("echo-liveSwitchPlayer error: " + e.getMessage());
            return false;
        }
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void changeLivePlayerScale(@NonNull VideoView videoView, int playerScale, String channelName){
        videoView.setScreenScaleType(playerScale);
        Hawk.put(HawkConfig.LIVE_PLAY_SCALE, playerScale);
        try {
            defaultPlayerConfig.put("sc", playerScale);
            if (currentPlayerConfig != null) currentPlayerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
