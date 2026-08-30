package com.github.tvbox.osc.bean;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveSettingItem {
    private int itemIndex;
    private String itemName;
    private boolean itemSelected = false;
    /** 多源切换分组内的来源类型：0=用户在直播地址中配置的直播源，1=当前点播源自带的 lives 线路 */
    private int itemGroup = 0;
    /** itemGroup=0 时对应的直播地址；itemGroup=1 时为空 */
    private String itemUrl = "";
    /** itemGroup=1 时，该条目在点播源 lives 数组中的下标；itemGroup=0 时为 -1 */
    private int itemSourceIndex = -1;

    public int getItemIndex() {
        return itemIndex;
    }

    public void setItemIndex(int itemIndex) {
        this.itemIndex = itemIndex;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public boolean isItemSelected() {
        return itemSelected;
    }

    public void setItemSelected(boolean itemSelected) {
        this.itemSelected = itemSelected;
    }

    public int getItemGroup() {
        return itemGroup;
    }

    public void setItemGroup(int itemGroup) {
        this.itemGroup = itemGroup;
    }

    public String getItemUrl() {
        return itemUrl;
    }

    public void setItemUrl(String itemUrl) {
        this.itemUrl = itemUrl;
    }

    public int getItemSourceIndex() {
        return itemSourceIndex;
    }

    public void setItemSourceIndex(int itemSourceIndex) {
        this.itemSourceIndex = itemSourceIndex;
    }
}