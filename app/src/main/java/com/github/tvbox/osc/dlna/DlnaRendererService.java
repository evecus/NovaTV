package com.github.tvbox.osc.dlna;

import org.fourthline.cling.android.AndroidUpnpServiceImpl;

/**
 * cling UPnP 服务(接收端 DMR):
 * 常驻绑定,负责 SSDP alive 通告、响应 M-SEARCH、承载设备描述/SOAP 控制/GENA 事件。
 */
public class DlnaRendererService extends AndroidUpnpServiceImpl {
    @Override
    protected DlnaRendererConfiguration createConfiguration() {
        return new DlnaRendererConfiguration();
    }
}
