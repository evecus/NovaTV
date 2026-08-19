package com.github.tvbox.osc.server.dlna;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.RemoteServer;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.Locale;

/**
 * 轻量 DLNA DMR（数字媒体渲染器）实现：
 * - SSDP 多播发现（响应手机视频 App 的 M-SEARCH，周期性 NOTIFY ssdp:alive）
 * - 设备描述 / 服务 SCPD XML（由 RemoteServer 的 /dlna/* 路由提供）
 * - SOAP 控制处理：SetAVTransportURI / Play / Pause / Stop / GetTransportInfo 等
 * 收到媒体 URL 后复用现有 TYPE_PUSH_URL 推送链路播放。
 */
public class DlnaManager {

    public static final String DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1";
    public static final String AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1";
    public static final String CONNECTION_MANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1";
    private static final String UDN = "uuid:novatv-dlna-0000000001";
    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int CACHE_MS = 1800;

    private static volatile DlnaManager sInstance;
    private Context mContext;
    private MulticastSocket mSsdpSocket;
    private WifiManager.MulticastLock mMulticastLock;
    private Thread mSsdpThread;
    private volatile boolean running = false;

    // DMR 状态
    private String currentURI = "";
    private String transportState = "NO_MEDIA_PRESENT"; // STOPPED / PLAYING / PAUSED / NO_MEDIA_PRESENT

    private DlnaManager() {
    }

    public static DlnaManager get() {
        if (sInstance == null) {
            synchronized (DlnaManager.class) {
                if (sInstance == null) sInstance = new DlnaManager();
            }
        }
        return sInstance;
    }

    public synchronized void start(Context context) {
        if (running) return;
        mContext = context.getApplicationContext();
        running = true;
        try {
            WifiManager wifi = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                mMulticastLock = wifi.createMulticastLock("NovaTV_DLNA");
                mMulticastLock.setReferenceCounted(false);
                mMulticastLock.acquire();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        mSsdpThread = new Thread(new SsdpRunnable(), "dlna-ssdp");
        mSsdpThread.setDaemon(true);
        mSsdpThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (mSsdpSocket != null) {
            try {
                mSsdpSocket.close();
            } catch (Throwable ignored) {
            }
        }
        if (mMulticastLock != null) {
            try {
                mMulticastLock.release();
            } catch (Throwable ignored) {
            }
        }
        mSsdpThread = null;
    }

    public boolean isRunning() {
        return running;
    }

    // ─── SSDP ────────────────────────────────────────────────────────────────

    private class SsdpRunnable implements Runnable {
        @Override
        public void run() {
            try {
                mSsdpSocket = new MulticastSocket(SSDP_PORT);
                mSsdpSocket.setReuseAddress(true);
                InetAddress group = InetAddress.getByName(SSDP_ADDR);
                mSsdpSocket.joinGroup(group);
                mSsdpSocket.setSoTimeout(1000);
            } catch (Throwable th) {
                th.printStackTrace();
                running = false;
                return;
            }
            long lastNotify = 0;
            byte[] buf = new byte[4096];
            while (running) {
                // 周期性 NOTIFY ssdp:alive
                long now = System.currentTimeMillis();
                if (now - lastNotify > 60_000) {
                    lastNotify = now;
                    notifyAlive();
                }
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    mSsdpSocket.receive(packet);
                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), "UTF-8");
                    if (msg.startsWith("M-SEARCH")) {
                        handleMSearch(msg, packet);
                    }
                } catch (SocketTimeoutException ignored) {
                } catch (IOException ignored) {
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
            try {
                mSsdpSocket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void handleMSearch(String msg, DatagramPacket packet) {
        String st = null;
        for (String line : msg.split("\r\n")) {
            if (line.toLowerCase(Locale.US).startsWith("st:")) {
                st = line.substring(3).trim();
                break;
            }
        }
        if (st == null) return;
        String[] targets = { "ssdp:all", "upnp:rootdevice", DEVICE_TYPE, AV_TRANSPORT, CONNECTION_MANAGER,
                "urn:schemas-upnp-org:service:ContentDirectory:1", "urn:schemas-upnp-org:device:MediaServer:1" };
        boolean match = false;
        for (String t : targets) {
            if (st.equalsIgnoreCase(t) || st.equalsIgnoreCase(UDN)) {
                match = true;
                break;
            }
        }
        if (!match) return;

        String host = getLocalIpAddress();
        if (host == null || host.isEmpty()) return;
        String location = "http://" + host + ":" + RemoteServer.serverPort + "/dlna/description.xml";
        StringBuilder resp = new StringBuilder();
        resp.append("HTTP/1.1 200 OK\r\n");
        resp.append("CACHE-CONTROL: max-age=").append(CACHE_MS).append("\r\n");
        resp.append("EXT:\r\n");
        resp.append("LOCATION: ").append(location).append("\r\n");
        resp.append("SERVER: NovaTV/1.0 UPnP/1.0 NovaTV\r\n");
        resp.append("ST: ").append(st).append("\r\n");
        resp.append("USN: ").append(usnFor(st)).append("\r\n");
        resp.append("BOOTID.UPNP.ORG: 1\r\n");
        resp.append("CONFIGID.UPNP.ORG: 1\r\n");
        resp.append("\r\n");
        try {
            DatagramPacket out = new DatagramPacket(resp.toString().getBytes("UTF-8"),
                    resp.length(), packet.getAddress(), packet.getPort());
            mSsdpSocket.send(out);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void notifyAlive() {
        try {
            String host = getLocalIpAddress();
            if (host == null || host.isEmpty()) return;
            String location = "http://" + host + ":" + RemoteServer.serverPort + "/dlna/description.xml";
            String[] sts = { "upnp:rootdevice", DEVICE_TYPE, AV_TRANSPORT, CONNECTION_MANAGER };
            for (String st : sts) {
                StringBuilder msg = new StringBuilder();
                msg.append("NOTIFY * HTTP/1.1\r\n");
                msg.append("HOST: ").append(SSDP_ADDR).append(":").append(SSDP_PORT).append("\r\n");
                msg.append("CACHE-CONTROL: max-age=").append(CACHE_MS).append("\r\n");
                msg.append("LOCATION: ").append(location).append("\r\n");
                msg.append("NT: ").append(st).append("\r\n");
                msg.append("NTS: ssdp:alive\r\n");
                msg.append("SERVER: NovaTV/1.0 UPnP/1.0 NovaTV\r\n");
                msg.append("USN: ").append(usnFor(st)).append("\r\n");
                msg.append("BOOTID.UPNP.ORG: 1\r\n");
                msg.append("CONFIGID.UPNP.ORG: 1\r\n");
                msg.append("\r\n");
                DatagramPacket out = new DatagramPacket(msg.toString().getBytes("UTF-8"), msg.length(),
                        InetAddress.getByName(SSDP_ADDR), SSDP_PORT);
                mSsdpSocket.send(out);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private String usnFor(String st) {
        if (st.equalsIgnoreCase("upnp:rootdevice")) return UDN + "::upnp:rootdevice";
        if (st.equalsIgnoreCase(DEVICE_TYPE)) return UDN + "::" + DEVICE_TYPE;
        if (st.equalsIgnoreCase(AV_TRANSPORT)) return UDN + "::" + AV_TRANSPORT;
        if (st.equalsIgnoreCase(CONNECTION_MANAGER)) return UDN + "::" + CONNECTION_MANAGER;
        if (st.equalsIgnoreCase(UDN)) return UDN;
        return UDN + "::" + st;
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a.getAddress().length == 4) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    // ─── 设备描述 / SCPD XML ─────────────────────────────────────────────────

    public String getFriendlyName() {
        String model = Build.MODEL;
        return (model == null || model.isEmpty() ? "NovaTV" : model) + " (NovaTV)";
    }

    public String getDeviceDescription() {
        String host = getLocalIpAddress();
        String base = "http://" + (host == null ? "127.0.0.1" : host) + ":" + RemoteServer.serverPort;
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\n"
                + "  <specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "  <device>\n"
                + "    <deviceType>" + DEVICE_TYPE + "</deviceType>\n"
                + "    <friendlyName>" + xmlEscape(getFriendlyName()) + "</friendlyName>\n"
                + "    <manufacturer>NovaTV</manufacturer>\n"
                + "    <manufacturerURL>http://novatv.local</manufacturerURL>\n"
                + "    <modelDescription>NovaTV DLNA Media Renderer</modelDescription>\n"
                + "    <modelName>NovaTV</modelName>\n"
                + "    <modelNumber>1.0</modelNumber>\n"
                + "    <serialNumber>1</serialNumber>\n"
                + "    <UDN>" + UDN + "</UDN>\n"
                + "    <serviceList>\n"
                + "      <service>\n"
                + "        <serviceType>" + AV_TRANSPORT + "</serviceType>\n"
                + "        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>\n"
                + "        <SCPDURL>/dlna/AVTransport.xml</SCPDURL>\n"
                + "        <controlURL>/dlna/control/AVTransport</controlURL>\n"
                + "        <eventSubURL>/dlna/event/AVTransport</eventSubURL>\n"
                + "      </service>\n"
                + "      <service>\n"
                + "        <serviceType>" + CONNECTION_MANAGER + "</serviceType>\n"
                + "        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>\n"
                + "        <SCPDURL>/dlna/ConnectionManager.xml</SCPDURL>\n"
                + "        <controlURL>/dlna/control/ConnectionManager</controlURL>\n"
                + "        <eventSubURL>/dlna/event/ConnectionManager</eventSubURL>\n"
                + "      </service>\n"
                + "    </serviceList>\n"
                + "  </device>\n"
                + "</root>\n";
    }

    public String getAvTransportScpd() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n"
                + "  <specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "  <actionList>\n"
                + "    <action><name>SetAVTransportURI</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n"
                + "        <argument><name>CurrentURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\n"
                + "        <argument><name>CurrentURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>Play</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n"
                + "        <argument><name>Speed</name><direction>in</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>Pause</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>Stop</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>GetTransportInfo</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n"
                + "        <argument><name>CurrentTransportState</name><direction>out</direction><relatedStateVariable>TransportState</relatedStateVariable></argument>\n"
                + "        <argument><name>CurrentTransportStatus</name><direction>out</direction><relatedStateVariable>TransportStatus</relatedStateVariable></argument>\n"
                + "        <argument><name>CurrentSpeed</name><direction>out</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>GetPositionInfo</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n"
                + "        <argument><name>Track</name><direction>out</direction><relatedStateVariable>CurrentTrack</relatedStateVariable></argument>\n"
                + "        <argument><name>TrackDuration</name><direction>out</direction><relatedStateVariable>CurrentTrackDuration</relatedStateVariable></argument>\n"
                + "        <argument><name>TrackMetaData</name><direction>out</direction><relatedStateVariable>CurrentTrackMetaData</relatedStateVariable></argument>\n"
                + "        <argument><name>TrackURI</name><direction>out</direction><relatedStateVariable>CurrentTrackURI</relatedStateVariable></argument>\n"
                + "        <argument><name>RelTime</name><direction>out</direction><relatedStateVariable>RelativeTimePosition</relatedStateVariable></argument>\n"
                + "        <argument><name>AbsTime</name><direction>out</direction><relatedStateVariable>AbsoluteTimePosition</relatedStateVariable></argument>\n"
                + "        <argument><name>RelCount</name><direction>out</direction><relatedStateVariable>RelativeCounterPosition</relatedStateVariable></argument>\n"
                + "        <argument><name>AbsCount</name><direction>out</direction><relatedStateVariable>AbsoluteCounterPosition</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>GetMediaInfo</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n"
                + "        <argument><name>NrTracks</name><direction>out</direction><relatedStateVariable>NumberOfTracks</relatedStateVariable></argument>\n"
                + "        <argument><name>MediaDuration</name><direction>out</direction><relatedStateVariable>CurrentMediaDuration</relatedStateVariable></argument>\n"
                + "        <argument><name>CurrentURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\n"
                + "        <argument><name>CurrentURIMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\n"
                + "        <argument><name>NextURI</name><direction>out</direction><relatedStateVariable>NextAVTransportURI</relatedStateVariable></argument>\n"
                + "        <argument><name>NextURIMetaData</name><direction>out</direction><relatedStateVariable>NextAVTransportURIMetaData</relatedStateVariable></argument>\n"
                + "        <argument><name>PlayMedium</name><direction>out</direction><relatedStateVariable>PossiblePlaybackStorageMedia</relatedStateVariable></argument>\n"
                + "        <argument><name>RecordMedium</name><direction>out</direction><relatedStateVariable>PossibleRecordStorageMedia</relatedStateVariable></argument>\n"
                + "        <argument><name>WriteStatus</name><direction>out</direction><relatedStateVariable>RecordWriteStatus</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>GetTransportSettings</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n"
                + "        <argument><name>PlayMode</name><direction>out</direction><relatedStateVariable>CurrentPlayMode</relatedStateVariable></argument>\n"
                + "        <argument><name>RecQualityMode</name><direction>out</direction><relatedStateVariable>CurrentRecordQualityMode</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "  </actionList>\n"
                + "  <serviceStateTable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>AVTransportURIMetaData</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>NextAVTransportURI</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>NextAVTransportURIMetaData</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>TransportState</name><dataType>string</dataType><allowedValueList>\n"
                + "      <allowedValue>STOPPED</allowedValue><allowedValue>PLAYING</allowedValue><allowedValue>TRANSITIONING</allowedValue>\n"
                + "      <allowedValue>PAUSED_PLAYBACK</allowedValue><allowedValue>PAUSED_RECORDING</allowedValue><allowedValue>NO_MEDIA_PRESENT</allowedValue></allowedValueList></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>TransportStatus</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>TransportPlaySpeed</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>CurrentTrack</name><dataType>ui4</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>CurrentTrackDuration</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>CurrentTrackMetaData</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>CurrentTrackURI</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>RelativeTimePosition</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>AbsoluteTimePosition</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>RelativeCounterPosition</name><dataType>i4</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>AbsoluteCounterPosition</name><dataType>i4</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>NumberOfTracks</name><dataType>ui4</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>CurrentMediaDuration</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>PossiblePlaybackStorageMedia</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>PossibleRecordStorageMedia</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>RecordWriteStatus</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>CurrentPlayMode</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>CurrentRecordQualityMode</name><dataType>string</dataType></stateVariable>\n"
                + "  </serviceStateTable>\n"
                + "</scpd>\n";
    }

    public String getConnectionManagerScpd() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n"
                + "  <specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "  <actionList>\n"
                + "    <action><name>GetProtocolInfo</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>Source</name><direction>out</direction><relatedStateVariable>SourceProtocolInfo</relatedStateVariable></argument>\n"
                + "        <argument><name>Sink</name><direction>out</direction><relatedStateVariable>SinkProtocolInfo</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>GetCurrentConnectionIDs</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>ConnectionIDs</name><direction>out</direction><relatedStateVariable>CurrentConnectionIDs</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "    <action><name>GetCurrentConnectionInfo</name>\n"
                + "      <argumentList>\n"
                + "        <argument><name>ConnectionID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\n"
                + "        <argument><name>RcsID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_RcsID</relatedStateVariable></argument>\n"
                + "        <argument><name>AVTransportID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_AVTransportID</relatedStateVariable></argument>\n"
                + "        <argument><name>ProtocolInfo</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ProtocolInfo</relatedStateVariable></argument>\n"
                + "        <argument><name>PeerConnectionManager</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionManager</relatedStateVariable></argument>\n"
                + "        <argument><name>PeerConnectionID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\n"
                + "        <argument><name>Direction</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Direction</relatedStateVariable></argument>\n"
                + "        <argument><name>Status</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionStatus</relatedStateVariable></argument>\n"
                + "      </argumentList>\n"
                + "    </action>\n"
                + "  </actionList>\n"
                + "  <serviceStateTable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>SourceProtocolInfo</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>SinkProtocolInfo</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>CurrentConnectionIDs</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionID</name><dataType>i4</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_RcsID</name><dataType>i4</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_AVTransportID</name><dataType>i4</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ProtocolInfo</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionManager</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_Direction</name><dataType>string</dataType></stateVariable>\n"
                + "    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionStatus</name><dataType>string</dataType></stateVariable>\n"
                + "  </serviceStateTable>\n"
                + "</scpd>\n";
    }

    // ─── SOAP 控制 ───────────────────────────────────────────────────────────

    /**
     * 处理 SOAP 控制请求，返回 SOAP 响应体（不含 HTTP 头）。
     *
     * @param service     "AVTransport" 或 "ConnectionManager"
     * @param soapAction  SOAPAction 头
     * @param body        POST 请求体
     */
    public String handleControl(String service, String soapAction, String body) {
        String action = soapAction;
        if (action != null) {
            int i = action.lastIndexOf('#');
            if (i >= 0) action = action.substring(i + 1);
            i = action.indexOf('"');
            if (i >= 0) action = action.replace("\"", "").trim();
        }
        if (action == null || action.isEmpty()) {
            // 从 body 里提取 <u:ActionName
            int idx = body.indexOf("<u:");
            if (idx >= 0) {
                int end = body.indexOf(" ", idx);
                if (end < 0) end = body.indexOf(">", idx);
                if (end > idx + 3) action = body.substring(idx + 3, end);
            }
        }
        if (action == null || action.isEmpty()) {
            return soapFault("actionNameMissing", "Missing action name");
        }
        try {
            switch (action) {
                case "SetAVTransportURI": {
                    String uri = extractTag(body, "CurrentURI");
                    String meta = extractTag(body, "CurrentURIMetaData");
                    setCurrentUri(uri, meta);
                    return soapResponse("SetAVTransportURIResponse");
                }
                case "Play": {
                    transportState = "PLAYING";
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_CAST_PLAY, null));
                    return soapResponse("PlayResponse");
                }
                case "Pause": {
                    transportState = "PAUSED_PLAYBACK";
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_CAST_PAUSE, null));
                    return soapResponse("PauseResponse");
                }
                case "Stop": {
                    transportState = "STOPPED";
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_CAST_STOP, null));
                    return soapResponse("StopResponse");
                }
                case "GetTransportInfo": {
                    String state = transportState == null ? "NO_MEDIA_PRESENT" : transportState;
                    return soapResponse("GetTransportInfoResponse",
                            "<CurrentTransportState>" + state + "</CurrentTransportState>"
                                    + "<CurrentTransportStatus>OK</CurrentTransportStatus>"
                                    + "<CurrentSpeed>1</CurrentSpeed>");
                }
                case "GetPositionInfo": {
                    return soapResponse("GetPositionInfoResponse",
                            "<Track>0</Track>"
                                    + "<TrackDuration>0:00:00</TrackDuration>"
                                    + "<TrackMetaData></TrackMetaData>"
                                    + "<TrackURI>" + xmlEscape(currentURI) + "</TrackURI>"
                                    + "<RelTime>0:00:00</RelTime>"
                                    + "<AbsTime>0:00:00</AbsTime>"
                                    + "<RelCount>0</RelCount>"
                                    + "<AbsCount>0</AbsCount>");
                }
                case "GetMediaInfo": {
                    return soapResponse("GetMediaInfoResponse",
                            "<NrTracks>1</NrTracks>"
                                    + "<MediaDuration>0:00:00</MediaDuration>"
                                    + "<CurrentURI>" + xmlEscape(currentURI) + "</CurrentURI>"
                                    + "<CurrentURIMetaData></CurrentURIMetaData>"
                                    + "<NextURI></NextURI>"
                                    + "<NextURIMetaData></NextURIMetaData>"
                                    + "<PlayMedium>NONE</PlayMedium>"
                                    + "<RecordMedium>NOT_IMPLEMENTED</RecordMedium>"
                                    + "<WriteStatus>NOT_IMPLEMENTED</WriteStatus>");
                }
                case "GetTransportSettings": {
                    return soapResponse("GetTransportSettingsResponse",
                            "<PlayMode>NORMAL</PlayMode><RecQualityMode>NOT_IMPLEMENTED</RecQualityMode>");
                }
                case "GetProtocolInfo": {
                    return soapResponse("GetProtocolInfoResponse",
                            "<Source>http-get:*:audio/mpeg:*</Source>"
                                    + "<Sink>http-get:*:*:*</Sink>");
                }
                case "GetCurrentConnectionIDs": {
                    return soapResponse("GetCurrentConnectionIDsResponse", "<ConnectionIDs>0</ConnectionIDs>");
                }
                case "GetCurrentConnectionInfo": {
                    return soapResponse("GetCurrentConnectionInfoResponse",
                            "<RcsID>0</RcsID>"
                                    + "<AVTransportID>0</AVTransportID>"
                                    + "<ProtocolInfo>http-get:*:*:*</ProtocolInfo>"
                                    + "<PeerConnectionManager></PeerConnectionManager>"
                                    + "<PeerConnectionID>-1</PeerConnectionID>"
                                    + "<Direction>Input</Direction>"
                                    + "<Status>OK</Status>");
                }
                default:
                    return soapFault("InvalidAction", "Unsupported action: " + action);
            }
        } catch (Throwable th) {
            th.printStackTrace();
            return soapFault("InternalError", th.getMessage());
        }
    }

    private synchronized void setCurrentUri(String uri, String meta) {
        currentURI = uri == null ? "" : uri;
        transportState = "PLAYING";
        if (!currentURI.isEmpty()) {
            // 复用现有推送播放链路（与 /push/ 一致）
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, currentURI));
        }
    }

    // ─── SOAP 辅助 ───────────────────────────────────────────────────────────

    private String soapResponse(String actionName) {
        return soapResponse(actionName, "");
    }

    private String soapResponse(String actionName, String inner) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n"
                + "  <s:Body>\n"
                + "    <u:" + actionName + " xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">\n"
                + inner
                + "\n    </u:" + actionName + ">\n"
                + "  </s:Body>\n"
                + "</s:Envelope>\n";
    }

    private String soapFault(String errorCode, String desc) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n"
                + "  <s:Body>\n"
                + "    <s:Fault>\n"
                + "      <faultcode>s:Client</faultcode>\n"
                + "      <faultstring>UPnPError</faultstring>\n"
                + "      <detail>\n"
                + "        <UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">\n"
                + "          <errorCode>" + errorCode + "</errorCode>\n"
                + "          <errorDescription>" + xmlEscape(desc) + "</errorDescription>\n"
                + "        </UPnPError>\n"
                + "      </detail>\n"
                + "    </s:Fault>\n"
                + "  </s:Body>\n"
                + "</s:Envelope>\n";
    }

    private String extractTag(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int i = xml.indexOf(open);
        if (i < 0) {
            // 带命名空间前缀 <ns0:CurrentURI>
            int p = xml.indexOf(tag + ">");
            if (p >= 0) {
                int s = xml.lastIndexOf('<', p);
                open = xml.substring(s, p + tag.length() + 1);
                close = "</" + tag + ">";
                i = p;
            } else {
                return "";
            }
        }
        int j = xml.indexOf(close, i);
        if (j < 0) return "";
        String v = xml.substring(i + open.length(), j);
        return v == null ? "" : v;
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
