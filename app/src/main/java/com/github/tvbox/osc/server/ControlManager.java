package com.github.tvbox.osc.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.util.ConfigHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.OpenListApi;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2021/1/4
 * @description:
 */
public class ControlManager {
    private static ControlManager instance;
    private RemoteServer mServer = null;
    public static Context mContext;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ControlManager() {}

    public static ControlManager get() {
        if (instance == null) {
            synchronized (ControlManager.class) {
                if (instance == null) instance = new ControlManager();
            }
        }
        return instance;
    }

    public static void init(Context context) {
        mContext = context;
    }

    public String getAddress(boolean local) {
        if (mServer == null || !mServer.isStarting()) startServer();
        if (mServer == null || !mServer.isStarting()) return "";
        return local ? mServer.getLoadAddress() : mServer.getServerAddress();
    }

    /** 把条目写入 VOD_CONFIG_LIST（按 URL 去重），不改变当前激活配置 */
    private static void saveVodEntry(String entry, String url) {
        ArrayList<String> list = Hawk.get(HawkConfig.VOD_CONFIG_LIST, new ArrayList<String>());
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (url.equals(ConfigHelper.getVodUrl(list.get(i)))) {
                list.set(i, entry);
                found = true;
                break;
            }
        }
        if (!found) list.add(entry);
        Hawk.put(HawkConfig.VOD_CONFIG_LIST, list);
        boolean isWarehouse = ConfigHelper.isWarehouse(entry);
        mainHandler.post(() -> Toast.makeText(mContext,
                isWarehouse ? "仓库已添加到配置列表" : "线路已添加到配置列表",
                Toast.LENGTH_SHORT).show());
    }

    public void startServer() {
        if (mServer != null && mServer.isStarting()) return;
        do {
            mServer = new RemoteServer(RemoteServer.serverPort, mContext);
            mServer.setDataReceiver(new DataReceiver() {

                @Override
                public void onTextReceived(String text) {
                    if (!TextUtils.isEmpty(text)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("title", text);
                        intent.setAction(SearchReceiver.action);
                        intent.setPackage(mContext.getPackageName());
                        intent.setComponent(new ComponentName(mContext, SearchReceiver.class));
                        intent.putExtras(bundle);
                        mContext.sendBroadcast(intent);
                    }
                }

                @Override
                public void onApiReceived(String url) {
                    onApiReceived(url, "远程推送");
                }

                @Override
                public void onApiReceived(String url, String name) {
                    if (TextUtils.isEmpty(url)) return;
                    final String entryName = TextUtils.isEmpty(name) ? "远程推送" : name;
                    // 异步 fetch URL，判断是仓库还是线路（与 ConfigSourceDialog 逻辑一致）
                    OkHttpClient client = OkGoHelper.getDefaultClient();
                    if (client == null) client = new OkHttpClient();
                    Request request = new Request.Builder().url(url).get().build();
                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NotNull Call call, @NotNull IOException e) {
                            // 网络失败，作为线路保存
                            String entry = ConfigHelper.buildVodEntry(entryName, url, null);
                            saveVodEntry(entry, url);
                        }

                        @Override
                        public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                            String body = response.body() != null ? response.body().string() : "";
                            // parseVodEntry 会判断是仓库还是线路
                            String entry = ConfigHelper.parseVodEntry(entryName, url, body);
                            saveVodEntry(entry, url);
                        }
                    });
                    // 兼容旧 ApiDialog 文本填充（如果弹窗恰好打开）
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_API_URL_CHANGE, url));
                }

                @Override
                public void onLiveApiReceived(String url) {
                    onLiveApiReceived(url, "直播推送");
                }

                @Override
                public void onLiveApiReceived(String url, String name) {
                    if (TextUtils.isEmpty(url)) return;
                    final String entryName = TextUtils.isEmpty(name) ? "直播推送" : name;
                    // 写入 LIVE_SOURCE_LIST（LiveConfigDialog 使用的 key）
                    ArrayList<String> list = Hawk.get(HawkConfig.LIVE_SOURCE_LIST, new ArrayList<String>());
                    String entry = ConfigHelper.buildLiveEntry(entryName, url);
                    boolean found = false;
                    for (int i = 0; i < list.size(); i++) {
                        if (url.equals(ConfigHelper.getLiveUrl(list.get(i)))) {
                            list.set(i, entry);
                            found = true;
                            break;
                        }
                    }
                    if (!found) list.add(entry);
                    Hawk.put(HawkConfig.LIVE_SOURCE_LIST, list);
                    // 记录历史（不改变当前激活直播源）
                    HistoryHelper.setLiveApiHistory(url);
                    // 兼容旧 ApiDialog
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_LIVE_API_URL_CHANGE, url));
                    // Toast 通知
                    mainHandler.post(() -> Toast.makeText(mContext,
                            "直播源已添加到列表：" + entryName, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onDanmuApiReceived(String url) {
                    Hawk.put(HawkConfig.DANMU_API, TextUtils.isEmpty(url) ? "" : url);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
                }

                @Override
                public void onPushReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, url));
                }

                @Override
                public void onOpenListConfigReceived(String serverUrl, String username, String password) {
                    if (TextUtils.isEmpty(serverUrl)) return;
                    OpenListApi.login(serverUrl, username, password, new OpenListApi.Callback<String>() {
                        @Override
                        public void onSuccess(String token) {
                            EventBus.getDefault().post(
                                    new RefreshEvent(RefreshEvent.TYPE_OPENLIST_LOGIN, serverUrl));
                        }

                        @Override
                        public void onError(String msg) {
                            Hawk.put(HawkConfig.OPENLIST_SERVER_URL, OpenListApi.normalizeUrl(serverUrl));
                            Hawk.put(HawkConfig.OPENLIST_USERNAME, username);
                        }
                    });
                }
            });
            try {
                mServer.start();
                com.github.catvod.Proxy.set(RemoteServer.serverPort);
                IjkMediaPlayer.setDotPort(Hawk.get(HawkConfig.DOH_URL, 0) > 0, RemoteServer.serverPort);
                break;
            } catch (IOException ex) {
                RemoteServer.serverPort++;
                mServer.stop();
            }
        } while (RemoteServer.serverPort < 9999);
    }

    public void stopServer() {
        if (mServer != null && mServer.isStarting()) mServer.stop();
        mServer = null;
    }
}
