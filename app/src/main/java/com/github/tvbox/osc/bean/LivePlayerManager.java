package com.github.tvbox.osc.bean;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.PlayerSwitchUtil;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

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
            // 4 档 PLAY_TYPE:0=EXO硬解,1=EXO软解,2=IJK硬解,3=IJK软解;默认 IJK硬解
            // PLAY_TYPE 统一为 4 档新编码,直接使用,不再做历史 1=IJK/2=EXO 映射
            int livePlayType = Hawk.get(HawkConfig.LIVE_PLAY_TYPE, 2);
            // 兜底:不在 0~3 范围按 IJK硬解处理
            if (livePlayType < 0 || livePlayType > 3) livePlayType = 2;
            defaultPlayerConfig.put("pl", livePlayType);
            // EXO 路径下 ijk 字段不再被使用,但保留默认以便旧 UI 兼容
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
            // 4 档直接对应 position(EXO硬解=0, EXO软解=1, IJK硬解=2, IJK软解=3)
            // 兼容历史 PLAY_TYPE:0=系统 -> IJK硬解(2),1=IJK -> IJK硬解(2),2=EXO -> EXO硬解(0)
            switch (playerType) {
                case 0:
                default:
                    playerTypeIndex = 0; // EXO硬解
                    break;
                case 1:
                    playerTypeIndex = 1; // EXO软解
                    break;
                case 2:
                    playerTypeIndex = 2; // IJK硬解
                    break;
                case 3:
                    playerTypeIndex = 3; // IJK软解
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
     * 直播页切换播放器：4 档位置(0=EXO硬解,1=EXO软解,2=IJK硬解,3=IJK软解)
     * 写入直播全局设置,所有频道共用
     */
    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName) {
        try {
            // 4 档位置直接映射到 PLAY_TYPE
            switch (playerType) {
                case 0:
                    defaultPlayerConfig.put("pl", 0); // EXO硬解
                    defaultPlayerConfig.put("ijk", "硬解码");
                    break;
                case 1:
                    defaultPlayerConfig.put("pl", 1); // EXO软解
                    defaultPlayerConfig.put("ijk", "硬解码");
                    break;
                case 2:
                    defaultPlayerConfig.put("pl", 2); // IJK硬解
                    defaultPlayerConfig.put("ijk", "硬解码");
                    break;
                case 3:
                    defaultPlayerConfig.put("pl", 3); // IJK软解
                    defaultPlayerConfig.put("ijk", "软解码");
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // 直播全局保存,不影响点播/全局播放器设置
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
     * 自动切换播放内核(直播播放失败时由上层调用)。
     * 按固定顺序 0→1→2→3 尝试除当前外的其余内核,已尝试过的记录在 triedPlayerTypes 中。
     *
     * @param triedPlayerTypes 已尝试过的内核档位集合(内部会累加当前内核)
     * @return true 表示已切换到下一个内核,调用方应使用当前源 URL 重新播放;
     *         false 表示其余三个内核都已试过(或配置不支持),调用方应降级处理(如换源/换频道)
     */
    public boolean switchLivePlayer(VideoView videoView, String channelName, Set<Integer> triedPlayerTypes) {
        if (defaultPlayerConfig == null) {
            LOG.i("echo-liveSwitchPlayer: skip empty player config");
            return false;
        }
        try {
            int playerType = defaultPlayerConfig.getInt("pl");
            // defaultPlayerConfig.pl 统一为 4 档新编码,直接使用,不再做历史 1=IJK/2=EXO 映射
            int switchPlayerType = PlayerSwitchUtil.nextPlayerType(playerType, triedPlayerTypes);
            if (switchPlayerType < 0) {
                LOG.i("echo-liveSwitchPlayer: all player types tried, skip");
                return false;
            }
            LOG.i("echo-liveSwitchPlayer: " + playerType + " -> " + switchPlayerType);
            defaultPlayerConfig.put("pl", switchPlayerType);
            defaultPlayerConfig.put("ijk", PlayerSwitchUtil.ijkCodeFor(switchPlayerType));
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
