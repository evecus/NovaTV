package com.github.tvbox.osc.dlna;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.github.tvbox.osc.util.LOG;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;

import java.util.UUID;

/**
 * DLNA 接收端(DMR)管理器:
 * - 绑定 DlnaRendererService(cling),注册 MediaRenderer:1 LocalDevice
 * - 手机投屏 App 的 M-SEARCH/描述/SOAP 控制/GENA 事件全部由 cling 承接
 * - 播放仍走原有事件链路(AvTransportService 发 EventBus),本类只做进度桥:
 *   CastPlayActivity 轮询上报 position/duration → GetPositionInfo 返回真实进度
 */
public class DlnaRendererManager implements ServiceConnection {

    private static final DlnaRendererManager INSTANCE = new DlnaRendererManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AndroidUpnpService upnpService;
    private LocalDevice device;
    private WifiManager.MulticastLock multicastLock;
    private boolean binding;

    // 播放进度桥(CastPlayActivity 上报)
    private volatile long positionMs = 0;
    private volatile long durationMs = 0;

    private DlnaRendererManager() {
    }

    public static DlnaRendererManager get() {
        return INSTANCE;
    }

    public synchronized void init(Context context) {
        if (upnpService != null || binding) return;
        Context appContext = context.getApplicationContext();
        acquireMulticastLock(appContext);
        binding = appContext.bindService(new Intent(context, DlnaRendererService.class), this, Context.BIND_AUTO_CREATE);
        if (!binding) releaseMulticastLock();
    }

    public synchronized void release(Context context) {
        try {
            if (upnpService != null && device != null) {
                upnpService.getRegistry().removeDevice(device);
            }
            context.getApplicationContext().unbindService(this);
        } catch (Exception ignored) {
        }
        upnpService = null;
        device = null;
        binding = false;
        releaseMulticastLock();
    }

    public boolean isRunning() {
        return upnpService != null && device != null;
    }

    /** CastPlayActivity 每隔 500ms 上报真实播放进度,供 GetPositionInfo 查询 */
    public void updatePlaybackState(long position, long duration) {
        this.positionMs = Math.max(0, position);
        this.durationMs = Math.max(0, duration);
    }

    public long getPosition() {
        return positionMs;
    }

    public long getDuration() {
        return durationMs;
    }

    // ─── ServiceConnection ──────────────────────────────────────────────────

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binding = false;
        upnpService = (AndroidUpnpService) service;
        registerDevice();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        upnpService = null;
        device = null;
        binding = false;
    }

    private void registerDevice() {
        if (upnpService == null || device != null) return;
        try {
            String model = Build.MODEL == null || Build.MODEL.isEmpty() ? "NovaTV" : Build.MODEL;

            LocalService<AvTransportService> avTransport =
                    new AnnotationLocalServiceBinder().read(AvTransportService.class);
            avTransport.setManager(new DefaultServiceManager<AvTransportService>(avTransport) {
                @Override
                protected AvTransportService createServiceInstance() throws Exception {
                    return new AvTransportService();
                }
            });

            LocalService<ConnectionManagerService> connectionManager =
                    new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
            connectionManager.setManager(new DefaultServiceManager<ConnectionManagerService>(connectionManager) {
                @Override
                protected ConnectionManagerService createServiceInstance() throws Exception {
                    return new ConnectionManagerService();
                }
            });

            device = new LocalDevice(
                    new DeviceIdentity(new UDN(UUID.nameUUIDFromBytes(("novatv-dmr-" + model).getBytes()))),
                    new UDADeviceType("MediaRenderer", 1),
                    new DeviceDetails(
                            model + " (NovaTV)",
                            new ManufacturerDetails("NovaTV"),
                            new ModelDetails("NovaTV", "NovaTV DLNA Media Renderer", "1.0")
                    ),
                    new Icon[0],
                    new LocalService[]{avTransport, connectionManager}
            );
            upnpService.getRegistry().addDevice(device);
            LOG.i("dlna-render MediaRenderer registered: " + model + " (NovaTV), udn=" + device.getIdentity().getUdn());
        } catch (Throwable th) {
            LOG.e("dlna-render register failure: " + th.getMessage());
            th.printStackTrace();
        }
    }

    // ─── MulticastLock ──────────────────────────────────────────────────────

    private void acquireMulticastLock(Context context) {
        try {
            if (multicastLock != null && multicastLock.isHeld()) return;
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return;
            multicastLock = wifiManager.createMulticastLock("NovaTV_DLNA");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception ignored) {
        }
        multicastLock = null;
    }
}
