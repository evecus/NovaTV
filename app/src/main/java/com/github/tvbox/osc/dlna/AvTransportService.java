package com.github.tvbox.osc.dlna;



import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.LOG;

import org.fourthline.cling.binding.annotations.UpnpAction;
import org.fourthline.cling.binding.annotations.UpnpInputArgument;
import org.fourthline.cling.binding.annotations.UpnpOutputArgument;
import org.fourthline.cling.binding.annotations.UpnpService;
import org.fourthline.cling.binding.annotations.UpnpServiceId;
import org.fourthline.cling.binding.annotations.UpnpServiceType;
import org.fourthline.cling.binding.annotations.UpnpStateVariable;
import org.fourthline.cling.model.types.ErrorCode;

import java.beans.PropertyChangeSupport;

/**
 * AVTransport:1 服务(cling 注解 bean):
 * - SetAVTransportURI → TYPE_PUSH_URL(复用现有投屏播放链路)
 * - Play / Pause / Stop → TYPE_CAST_PLAY / PAUSE / STOP
 * - Seek(REL_TIME) → TYPE_CAST_SEEK(新增)
 * - GetPositionInfo / GetTransportInfo 返回真实播放进度(CastPlayActivity 轮询上报)
 */
@UpnpService(
        serviceId = @UpnpServiceId("AVTransport"),
        serviceType = @UpnpServiceType(value = "AVTransport", version = 1)
)
public class AvTransportService {

    @UpnpStateVariable(sendEvents = false, defaultValue = "0")
    private Integer A_ARG_TYPE_InstanceID = 0;

    @UpnpStateVariable(sendEvents = false, defaultValue = "NORMAL")
    private String A_ARG_TYPE_SeekMode = "REL_TIME";

    @UpnpStateVariable(sendEvents = false)
    private String A_ARG_TYPE_SeekTarget = "0:00:00";

    @UpnpStateVariable(sendEvents = true, defaultValue = "NO_MEDIA_PRESENT")
    private String TransportState = "NO_MEDIA_PRESENT";

    @UpnpStateVariable(sendEvents = false, defaultValue = "OK")
    private String TransportStatus = "OK";

    @UpnpStateVariable(sendEvents = false, defaultValue = "1")
    private String TransportPlaySpeed = "1";

    @UpnpStateVariable(sendEvents = true)
    private String AVTransportURI = "";

    @UpnpStateVariable(sendEvents = false)
    private String AVTransportURIMetaData = "";

    @UpnpStateVariable(sendEvents = false, defaultValue = "0", datatype = "ui4")
    private int CurrentTrack = 0;

    @UpnpStateVariable(sendEvents = true, defaultValue = "0:00:00")
    private String CurrentTrackDuration = "0:00:00";

    @UpnpStateVariable(sendEvents = false)
    private String CurrentTrackMetaData = "";

    @UpnpStateVariable(sendEvents = true)
    private String CurrentTrackURI = "";

    @UpnpStateVariable(sendEvents = true, defaultValue = "0:00:00")
    private String RelativeTimePosition = "0:00:00";

    @UpnpStateVariable(sendEvents = false, defaultValue = "0:00:00")
    private String AbsoluteTimePosition = "0:00:00";

    @UpnpStateVariable(sendEvents = false, defaultValue = "2147483647", datatype = "i4")
    private int RelativeCounterPosition = Integer.MAX_VALUE;

    @UpnpStateVariable(sendEvents = false, defaultValue = "2147483647", datatype = "i4")
    private int AbsoluteCounterPosition = Integer.MAX_VALUE;

    @UpnpStateVariable(sendEvents = false, defaultValue = "1", datatype = "ui4")
    private int NumberOfTracks = 1;

    @UpnpStateVariable(sendEvents = false, defaultValue = "0:00:00")
    private String CurrentMediaDuration = "0:00:00";

    @UpnpStateVariable(sendEvents = false, defaultValue = "NOT_IMPLEMENTED")
    private String PossiblePlaybackStorageMedia = "NOT_IMPLEMENTED";

    @UpnpStateVariable(sendEvents = false, defaultValue = "NOT_IMPLEMENTED")
    private String PossibleRecordStorageMedia = "NOT_IMPLEMENTED";

    @UpnpStateVariable(sendEvents = false, defaultValue = "NOT_IMPLEMENTED")
    private String RecordWriteStatus = "NOT_IMPLEMENTED";

    @UpnpStateVariable(sendEvents = false, defaultValue = "NORMAL")
    private String CurrentPlayMode = "NORMAL";

    @UpnpStateVariable(sendEvents = false, defaultValue = "NOT_IMPLEMENTED")
    private String CurrentRecordQualityMode = "NOT_IMPLEMENTED";

    @UpnpStateVariable(sendEvents = false, defaultValue = "0")
    private String NextAVTransportURI = "";

    @UpnpStateVariable(sendEvents = false, defaultValue = "0")
    private String NextAVTransportURIMetaData = "";

    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public PropertyChangeSupport getPropertyChangeSupport() {
        return propertyChangeSupport;
    }

    // ─── 控制动作 ───────────────────────────────────────────────────────────

    @UpnpAction(name = "SetAVTransportURI")
    public void setAVTransportURI(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId,
            @UpnpInputArgument(name = "CurrentURI", stateVariable = "AVTransportURI") String currentURI,
            @UpnpInputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData") String currentURIMetaData) {
        LOG.i("dlna-render SetAVTransportURI: " + currentURI);
        AVTransportURI = currentURI == null ? "" : currentURI;
        AVTransportURIMetaData = currentURIMetaData == null ? "" : currentURIMetaData;
        CurrentTrackURI = AVTransportURI;
        CurrentTrackMetaData = AVTransportURIMetaData;
        TransportState = "PLAYING";
        notifyState();
        // 复用现有链路:HomeActivity 收到后按是否配置 push_agent 分发到 DetailActivity/CastPlayActivity
        org.greenrobot.eventbus.EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, AVTransportURI));
    }

    @UpnpAction(name = "Play")
    public void play(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId,
            @UpnpInputArgument(name = "Speed", stateVariable = "TransportPlaySpeed") String speed) {
        LOG.i("dlna-render Play");
        TransportState = "PLAYING";
        notifyState();
        org.greenrobot.eventbus.EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_CAST_PLAY, null));
    }

    @UpnpAction(name = "Pause")
    public void pause(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId) {
        LOG.i("dlna-render Pause");
        TransportState = "PAUSED_PLAYBACK";
        notifyState();
        org.greenrobot.eventbus.EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_CAST_PAUSE, null));
    }

    @UpnpAction(name = "Stop")
    public void stop(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId) {
        LOG.i("dlna-render Stop");
        TransportState = "STOPPED";
        notifyState();
        org.greenrobot.eventbus.EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_CAST_STOP, null));
    }

    @UpnpAction(name = "Seek")
    public void seek(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId,
            @UpnpInputArgument(name = "Unit", stateVariable = "A_ARG_TYPE_SeekMode") String unit,
            @UpnpInputArgument(name = "Target", stateVariable = "A_ARG_TYPE_SeekTarget") String target) {
        LOG.i("dlna-render Seek unit=" + unit + " target=" + target);
        long ms = parseUpnpTime(target);
        if (ms < 0) {
            throw new IllegalArgumentException(ErrorCode.INVALID_ARGS.getDescription());
        }
        org.greenrobot.eventbus.EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_CAST_SEEK, ms));
    }

    // ─── 查询动作 ───────────────────────────────────────────────────────────

    @UpnpAction(name = "GetTransportInfo", out = {
            @UpnpOutputArgument(name = "CurrentTransportState", stateVariable = "TransportState", getterName = "getTransportState"),
            @UpnpOutputArgument(name = "CurrentTransportStatus", stateVariable = "TransportStatus", getterName = "getTransportStatus"),
            @UpnpOutputArgument(name = "CurrentSpeed", stateVariable = "TransportPlaySpeed", getterName = "getTransportSpeed")
    })
    public void getTransportInfo(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId) {
    }

    public String getTransportState() {
        return TransportState;
    }

    public String getTransportStatus() {
        return TransportStatus;
    }

    public String getTransportSpeed() {
        return TransportPlaySpeed;
    }

    @UpnpAction(name = "GetPositionInfo", out = {
            @UpnpOutputArgument(name = "Track", stateVariable = "CurrentTrack", getterName = "getTrack"),
            @UpnpOutputArgument(name = "TrackDuration", stateVariable = "CurrentTrackDuration", getterName = "getTrackDuration"),
            @UpnpOutputArgument(name = "TrackMetaData", stateVariable = "CurrentTrackMetaData", getterName = "getTrackMetaData"),
            @UpnpOutputArgument(name = "TrackURI", stateVariable = "CurrentTrackURI", getterName = "getTrackURI"),
            @UpnpOutputArgument(name = "RelTime", stateVariable = "RelativeTimePosition", getterName = "getRelTime"),
            @UpnpOutputArgument(name = "AbsTime", stateVariable = "AbsoluteTimePosition", getterName = "getAbsTime"),
            @UpnpOutputArgument(name = "RelCount", stateVariable = "RelativeCounterPosition", getterName = "getRelCount"),
            @UpnpOutputArgument(name = "AbsCount", stateVariable = "AbsoluteCounterPosition", getterName = "getAbsCount")
    })
    public void getPositionInfo(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId) {
        // 进度从 CastPlayActivity 的轮询上报(DlnaRendererManager)读取,刷新状态变量
        long position = DlnaRendererManager.get().getPosition();
        long duration = DlnaRendererManager.get().getDuration();
        RelativeTimePosition = formatUpnpTime(position);
        AbsoluteTimePosition = RelativeTimePosition;
        CurrentTrackDuration = formatUpnpTime(duration);
        CurrentMediaDuration = CurrentTrackDuration;
    }

    public int getTrack() {
        return CurrentTrack;
    }

    public String getTrackDuration() {
        return CurrentTrackDuration;
    }

    public String getTrackMetaData() {
        return CurrentTrackMetaData;
    }

    public String getTrackURI() {
        return CurrentTrackURI;
    }

    public String getRelTime() {
        return RelativeTimePosition;
    }

    public String getAbsTime() {
        return AbsoluteTimePosition;
    }

    public int getRelCount() {
        return RelativeCounterPosition;
    }

    public int getAbsCount() {
        return AbsoluteCounterPosition;
    }

    @UpnpAction(name = "GetMediaInfo", out = {
            @UpnpOutputArgument(name = "NrTracks", stateVariable = "NumberOfTracks", getterName = "getNrTracks"),
            @UpnpOutputArgument(name = "MediaDuration", stateVariable = "CurrentMediaDuration", getterName = "getMediaDuration"),
            @UpnpOutputArgument(name = "CurrentURI", stateVariable = "AVTransportURI", getterName = "getCurrentUri"),
            @UpnpOutputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData", getterName = "getCurrentUriMetaData"),
            @UpnpOutputArgument(name = "NextURI", stateVariable = "NextAVTransportURI", getterName = "getNextUri"),
            @UpnpOutputArgument(name = "NextURIMetaData", stateVariable = "NextAVTransportURIMetaData", getterName = "getNextUriMetaData"),
            @UpnpOutputArgument(name = "PlayMedium", stateVariable = "PossiblePlaybackStorageMedia", getterName = "getPlayMedium"),
            @UpnpOutputArgument(name = "RecordMedium", stateVariable = "PossibleRecordStorageMedia", getterName = "getRecordMedium"),
            @UpnpOutputArgument(name = "WriteStatus", stateVariable = "RecordWriteStatus", getterName = "getWriteStatus")
    })
    public void getMediaInfo(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId) {
    }

    public int getNrTracks() {
        return NumberOfTracks;
    }

    public String getMediaDuration() {
        return CurrentMediaDuration;
    }

    public String getCurrentUri() {
        return AVTransportURI;
    }

    public String getCurrentUriMetaData() {
        return AVTransportURIMetaData;
    }

    public String getNextUri() {
        return NextAVTransportURI;
    }

    public String getNextUriMetaData() {
        return NextAVTransportURIMetaData;
    }

    public String getPlayMedium() {
        return PossiblePlaybackStorageMedia;
    }

    public String getRecordMedium() {
        return PossibleRecordStorageMedia;
    }

    public String getWriteStatus() {
        return RecordWriteStatus;
    }

    @UpnpAction(name = "GetTransportSettings", out = {
            @UpnpOutputArgument(name = "PlayMode", stateVariable = "CurrentPlayMode", getterName = "getPlayMode"),
            @UpnpOutputArgument(name = "RecQualityMode", stateVariable = "CurrentRecordQualityMode", getterName = "getRecQualityMode")
    })
    public void getTransportSettings(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") Integer instanceId) {
    }

    public String getPlayMode() {
        return CurrentPlayMode;
    }

    public String getRecQualityMode() {
        return CurrentRecordQualityMode;
    }

    // ─── 辅助 ───────────────────────────────────────────────────────────────

    private void notifyState() {
        propertyChangeSupport.firePropertyChange("TransportState", null, TransportState);
        propertyChangeSupport.firePropertyChange("AVTransportURI", null, AVTransportURI);
        propertyChangeSupport.firePropertyChange("CurrentTrackURI", null, CurrentTrackURI);
        propertyChangeSupport.firePropertyChange("CurrentTrackDuration", null, CurrentTrackDuration);
    }

    /** UPnP H:MM:SS → ms;解析失败返回 -1 */
    private static long parseUpnpTime(String value) {
        if (value == null || value.isEmpty()) return -1;
        try {
            String[] parts = value.split(":");
            if (parts.length < 2 || parts.length > 3) return -1;
            long seconds = 0;
            for (String part : parts) seconds = seconds * 60 + Long.parseLong(part.trim());
            return seconds * 1000;
        } catch (Exception e) {
            return -1;
        }
    }

    /** ms → UPnP H:MM:SS */
    private static String formatUpnpTime(long ms) {
        if (ms <= 0) return "0:00:00";
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
