package com.github.tvbox.osc.dlna;

import android.os.Build;

import org.fourthline.cling.binding.xml.ServiceDescriptorBinder;
import org.fourthline.cling.binding.xml.UDA10ServiceDescriptorBinderImpl;
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.model.ServerClientTokens;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;
import org.fourthline.cling.transport.spi.StreamClient;
import org.fourthline.cling.transport.spi.StreamServer;

/**
 * cling UPnP 配置(接收端 DMR):
 * - StreamClient 换 OkHttp(GENA 事件通知)
 * - StreamServer 换原生 Socket 实现(设备描述/SOAP 控制/事件订阅)
 * 接收端不做 ControlPoint 搜索,不限定 exclusive service types。
 */
public class DlnaRendererConfiguration extends AndroidUpnpServiceConfiguration {
    @Override
    @SuppressWarnings("rawtypes")
    public StreamClient createStreamClient() {
        return new OkHttpStreamClient(new OkHttpStreamClient.Configuration(getSyncProtocolExecutorService()) {
            @Override
            public String getUserAgentValue(int majorVersion, int minorVersion) {
                ServerClientTokens tokens = new ServerClientTokens(majorVersion, minorVersion);
                tokens.setOsName("Android");
                tokens.setOsVersion(Build.VERSION.RELEASE);
                return tokens.toString();
            }
        });
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new SocketHttpStreamServer(new SocketHttpStreamServer.Configuration(networkAddressFactory.getStreamListenPort()));
    }

    @Override
    protected ServiceDescriptorBinder createServiceDescriptorBinderUDA10() {
        return new UDA10ServiceDescriptorBinderImpl();
    }
}
