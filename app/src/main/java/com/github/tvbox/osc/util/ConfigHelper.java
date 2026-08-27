package com.github.tvbox.osc.util;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for managing VOD config list and Live source list.
 * VOD entry format: "name\turl\troutesJson"
 *   routesJson is a JSON array of {name,url} for warehouses, empty string for direct route.
 * Live entry format: "name\turl"
 */
public class ConfigHelper {
    public static final String SEP = "\t";

    // ─────── VOD Config helpers ───────

    public static String buildVodEntry(String name, String url, String routesJson) {
        return name + SEP + url + SEP + (TextUtils.isEmpty(routesJson) ? "" : routesJson);
    }

    public static String getVodName(String entry) {
        if (entry == null) return "";
        String[] p = entry.split("\t", 3);
        return p.length > 0 ? p[0] : "";
    }

    public static String getVodUrl(String entry) {
        if (entry == null) return "";
        String[] p = entry.split("\t", 3);
        return p.length > 1 ? p[1] : "";
    }

    public static String getVodRoutes(String entry) {
        if (entry == null) return "";
        String[] p = entry.split("\t", 3);
        return p.length > 2 ? p[2] : "";
    }

    public static boolean isWarehouse(String entry) {
        return !TextUtils.isEmpty(getVodRoutes(entry));
    }

    /** Returns list of [name, url] pairs. For direct routes, returns single-item list. */
    public static List<String[]> getRouteList(String entry) {
        List<String[]> result = new ArrayList<>();
        String routesJson = getVodRoutes(entry);
        if (!TextUtils.isEmpty(routesJson)) {
            try {
                JSONArray arr = new JSONArray(routesJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    result.add(new String[]{o.optString("name", "线路" + (i + 1)), o.optString("url", "")});
                }
            } catch (Throwable ignored) {}
        }
        if (result.isEmpty()) {
            result.add(new String[]{getVodName(entry), getVodUrl(entry)});
        }
        return result;
    }

    /** Parse response body to determine warehouse routes or treat as direct route */
    public static String parseVodEntry(String name, String url, String body) {
        try {
            if (body != null && body.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(body.trim());
                if (obj.has("urls") && !obj.has("sites")) {
                    JSONArray urls = obj.getJSONArray("urls");
                    JSONArray routes = new JSONArray();
                    for (int i = 0; i < urls.length(); i++) {
                        Object item = urls.get(i);
                        JSONObject route = new JSONObject();
                        if (item instanceof JSONObject) {
                            JSONObject jo = (JSONObject) item;
                            route.put("name", jo.optString("name", "线路" + (i + 1)));
                            String u = jo.optString("url", jo.optString("api", ""));
                            route.put("url", u);
                        } else {
                            route.put("name", "线路" + (i + 1));
                            route.put("url", item.toString());
                        }
                        routes.put(route);
                    }
                    return buildVodEntry(name, url, routes.toString());
                }
            }
        } catch (Throwable ignored) {}
        return buildVodEntry(name, url, null);
    }

    // ─────── Live source helpers ───────

    public static String buildLiveEntry(String name, String url) {
        return name + SEP + url;
    }

    public static String getLiveName(String entry) {
        if (entry == null) return "";
        int idx = entry.indexOf(SEP);
        return idx >= 0 ? entry.substring(0, idx) : entry;
    }

    public static String getLiveUrl(String entry) {
        if (entry == null) return "";
        int idx = entry.indexOf(SEP);
        return idx >= 0 ? entry.substring(idx + SEP.length()) : "";
    }
}
