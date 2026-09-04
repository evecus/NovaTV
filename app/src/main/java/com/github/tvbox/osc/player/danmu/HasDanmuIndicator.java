package com.github.tvbox.osc.player.danmu;

/**
 * 弹幕状态回调接口:DanmuLoadController 通过它通知宿主控制器"当前是否已匹配到弹幕内容"。
 * VodController、OpenListPlayerController 均实现此接口，
 * 使 DanmuLoadController 不再强绑定某一个具体的控制器类型，可在多个播放页复用。
 */
public interface HasDanmuIndicator {
    void setHasDanmu(boolean hasDanmu);
}
