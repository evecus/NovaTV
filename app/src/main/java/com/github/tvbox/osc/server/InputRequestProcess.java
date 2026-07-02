package com.github.tvbox.osc.server;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */
public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return session.getMethod() == NanoHTTPD.Method.POST && "/action".equals(fileName);
    }

    private String param(Map<String, String> params, String key) {
        String v = params.get(key);
        return v != null ? v.trim() : "";
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName,
                                         Map<String, String> params, Map<String, String> files) {
        DataReceiver receiver = remoteServer.getDataReceiver();
        if ("/action".equals(fileName)) {
            String action = param(params, "do");
            if (!action.isEmpty() && receiver != null) {
                switch (action) {
                    case "search":
                        receiver.onTextReceived(param(params, "word"));
                        break;
                    case "api": {
                        String url  = param(params, "url");
                        String name = param(params, "name");
                        if (name.isEmpty()) receiver.onApiReceived(url);
                        else               receiver.onApiReceived(url, name);
                        break;
                    }
                    case "liveApi": {
                        String url  = param(params, "url");
                        String name = param(params, "name");
                        if (name.isEmpty()) receiver.onLiveApiReceived(url);
                        else               receiver.onLiveApiReceived(url, name);
                        break;
                    }
                    case "danmuApi":
                        receiver.onDanmuApiReceived(param(params, "url"));
                        break;
                    case "push":
                        receiver.onPushReceived(param(params, "url"));
                        break;
                    case "openlistConfig":
                        String server   = param(params, "server");
                        String username = param(params, "username");
                        String password = params.containsKey("password") ? params.get("password") : "";
                        receiver.onOpenListConfigReceived(server, username, password);
                        break;
                }
            }
            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
        }
        return RemoteServer.createPlainTextResponse(
                NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
    }
}
