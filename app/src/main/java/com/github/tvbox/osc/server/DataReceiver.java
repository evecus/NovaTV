package com.github.tvbox.osc.server;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public interface DataReceiver {

    void onTextReceived(String text);

    /** 推送点播配置地址（无名称，使用默认名） */
    void onApiReceived(String url);

    /** 推送点播配置地址（带名称） */
    void onApiReceived(String url, String name);

    /** 推送直播源地址（无名称，使用默认名） */
    void onLiveApiReceived(String url);

    /** 推送直播源地址（带名称） */
    void onLiveApiReceived(String url, String name);

    void onDanmuApiReceived(String url);

    void onPushReceived(String url);

    /** 通过局域网网页远程推送 OpenList/AList 登录信息 */
    void onOpenListConfigReceived(String serverUrl, String username, String password);
}
