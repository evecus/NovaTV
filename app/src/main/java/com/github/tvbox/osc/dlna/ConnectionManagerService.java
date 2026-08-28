package com.github.tvbox.osc.dlna;

import org.fourthline.cling.binding.annotations.UpnpAction;
import org.fourthline.cling.binding.annotations.UpnpInputArgument;
import org.fourthline.cling.binding.annotations.UpnpOutputArgument;
import org.fourthline.cling.binding.annotations.UpnpService;
import org.fourthline.cling.binding.annotations.UpnpServiceId;
import org.fourthline.cling.binding.annotations.UpnpServiceType;
import org.fourthline.cling.binding.annotations.UpnpStateVariable;

/**
 * ConnectionManager:1 服务(cling 注解 bean):
 * 告诉投屏端本机可接收的协议(Sink=http-get:*:*:*),纯占位即可。
 */
@UpnpService(
        serviceId = @UpnpServiceId("ConnectionManager"),
        serviceType = @UpnpServiceType(value = "ConnectionManager", version = 1)
)
public class ConnectionManagerService {

    @UpnpStateVariable(sendEvents = false, defaultValue = "0", datatype = "i4")
    private int A_ARG_TYPE_ConnectionID = 0;

    @UpnpStateVariable(sendEvents = false, defaultValue = "-1", datatype = "i4")
    private int A_ARG_TYPE_RcsID = 0;

    @UpnpStateVariable(sendEvents = false, defaultValue = "-1", datatype = "i4")
    private int A_ARG_TYPE_AVTransportID = 0;

    @UpnpStateVariable(sendEvents = false)
    private String A_ARG_TYPE_ProtocolInfo = "";

    @UpnpStateVariable(sendEvents = false)
    private String A_ARG_TYPE_ConnectionManager = "";

    @UpnpStateVariable(sendEvents = false, defaultValue = "OK")
    private String A_ARG_TYPE_ConnectionStatus = "OK";

    @UpnpStateVariable(sendEvents = false, defaultValue = "Input")
    private String A_ARG_TYPE_Direction = "Input";

    @UpnpStateVariable(sendEvents = false)
    private String SourceProtocolInfo = "";

    @UpnpStateVariable(sendEvents = true)
    private String SinkProtocolInfo = "http-get:*:*:*";

    @UpnpStateVariable(sendEvents = false, defaultValue = "0")
    private String CurrentConnectionIDs = "0";

    @UpnpAction(name = "GetProtocolInfo", out = {
            @UpnpOutputArgument(name = "Source", stateVariable = "SourceProtocolInfo", getterName = "getSource"),
            @UpnpOutputArgument(name = "Sink", stateVariable = "SinkProtocolInfo", getterName = "getSink")
    })
    public void getProtocolInfo() {
    }

    public String getSource() {
        return SourceProtocolInfo;
    }

    public String getSink() {
        return SinkProtocolInfo;
    }

    @UpnpAction(name = "GetCurrentConnectionIDs", out = {
            @UpnpOutputArgument(name = "ConnectionIDs", stateVariable = "CurrentConnectionIDs")
    })
    public String getCurrentConnectionIDs() {
        return CurrentConnectionIDs;
    }

    @UpnpAction(name = "GetCurrentConnectionInfo", out = {
            @UpnpOutputArgument(name = "RcsID", stateVariable = "A_ARG_TYPE_RcsID", getterName = "getRcsId"),
            @UpnpOutputArgument(name = "AVTransportID", stateVariable = "A_ARG_TYPE_AVTransportID", getterName = "getAvTransportId"),
            @UpnpOutputArgument(name = "ProtocolInfo", stateVariable = "A_ARG_TYPE_ProtocolInfo", getterName = "getProtocolInfoArg"),
            @UpnpOutputArgument(name = "PeerConnectionManager", stateVariable = "A_ARG_TYPE_ConnectionManager", getterName = "getPeerConnectionManager"),
            @UpnpOutputArgument(name = "PeerConnectionID", stateVariable = "A_ARG_TYPE_ConnectionID", getterName = "getPeerConnectionId"),
            @UpnpOutputArgument(name = "Direction", stateVariable = "A_ARG_TYPE_Direction", getterName = "getDirection"),
            @UpnpOutputArgument(name = "Status", stateVariable = "A_ARG_TYPE_ConnectionStatus", getterName = "getStatus")
    })
    public void getCurrentConnectionInfo(
            @UpnpInputArgument(name = "ConnectionID", stateVariable = "A_ARG_TYPE_ConnectionID") int connectionId) {
    }

    public int getRcsId() {
        return 0;
    }

    public int getAvTransportId() {
        return 0;
    }

    public String getProtocolInfoArg() {
        return "http-get:*:*:*";
    }

    public String getPeerConnectionManager() {
        return "";
    }

    public int getPeerConnectionId() {
        return -1;
    }

    public String getDirection() {
        return "Input";
    }

    public String getStatus() {
        return "OK";
    }
}
