package com.github.tvbox.osc.event;

/**
 * @author pj567
 * @date :2021/1/6
 * @description:
 */
public class RefreshEvent {
    public static final int TYPE_REFRESH = 0;
    public static final int TYPE_HISTORY_REFRESH = 1;
    public static final int TYPE_QUICK_SEARCH = 2;
    public static final int TYPE_QUICK_SEARCH_SELECT = 3;
    public static final int TYPE_QUICK_SEARCH_WORD = 4;
    public static final int TYPE_QUICK_SEARCH_WORD_CHANGE = 5;
    public static final int TYPE_SEARCH_RESULT = 6;
    public static final int TYPE_QUICK_SEARCH_RESULT = 7;
    public static final int TYPE_API_URL_CHANGE = 8;
    public static final int TYPE_PUSH_URL = 9;
    public static final int TYPE_EPG_URL_CHANGE = 10;
    public static final int TYPE_SETTING_SEARCH_TV = 11;
    public static final int TYPE_SUBTITLE_SIZE_CHANGE = 12;
    public static final int TYPE_FILTER_CHANGE = 13;
    public static final int TYPE_LIVE_API_URL_CHANGE = 14;
    public static final int TYPE_SET_DANMU_SETTINGS = 18;
    public static final int TYPE_DANMU_REFRESH = 19;
    public static final int TYPE_OPENLIST_LOGIN = 20;
    /** 远程推送仓库/线路配置，已写入 Hawk，需重启首页生效 */
    public static final int TYPE_VOD_CONFIG_PUSH = 21;
    /** 远程推送直播地址，已写入 Hawk，通知 UI 更新 */
    public static final int TYPE_LIVE_CONFIG_PUSH = 22;
    public int type;
    public Object obj;

    public RefreshEvent(int type) {
        this.type = type;
    }

    public RefreshEvent(int type, Object obj) {
        this.type = type;
        this.obj = obj;
    }
}
