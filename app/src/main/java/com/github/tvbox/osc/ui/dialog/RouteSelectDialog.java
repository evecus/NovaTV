package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.ConfigHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 线路选择弹窗
 * 左列：配置的仓库/线路名称；右列：选中仓库的子线路（或直接线路名）
 * 点击右列某条线路 → 设置为当前 API_URL
 */
public class RouteSelectDialog extends BaseDialog {

    private LinearLayout llConfigList;
    private LinearLayout llRouteList;
    private List<String> configList = new ArrayList<>();
    private int selectedConfig = 0;

    public interface OnRouteSelectedListener {
        void onSelected(String name, String url);
    }

    private OnRouteSelectedListener listener;

    public void setOnRouteSelectedListener(OnRouteSelectedListener l) {
        this.listener = l;
    }

    public RouteSelectDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_route_select);
        setCanceledOnTouchOutside(true);

        llConfigList = findViewById(R.id.llConfigList);
        llRouteList = findViewById(R.id.llRouteList);

        ArrayList<String> saved = Hawk.get(HawkConfig.VOD_CONFIG_LIST, new ArrayList<String>());
        configList.addAll(saved);

        if (!configList.isEmpty()) {
            renderConfigList();
            renderRouteList(0);
        }
    }

    private void renderConfigList() {
        llConfigList.removeAllViews();
        String currentUrl = Hawk.get(HawkConfig.API_URL, "");
        for (int i = 0; i < configList.size(); i++) {
            final int pos = i;
            String entry = configList.get(i);
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_route_name_tv, llConfigList, false);
            TextView tv = item.findViewById(R.id.tvName);
            tv.setText(ConfigHelper.getVodName(entry));
            // Highlight if current route is in this entry
            boolean isCurrent = isCurrentInEntry(entry, currentUrl);
            if (isCurrent || pos == selectedConfig) {
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            tv.setOnClickListener(v -> {
                selectedConfig = pos;
                renderConfigList();
                renderRouteList(pos);
            });
            llConfigList.addView(item);
        }
    }

    private void renderRouteList(int configPos) {
        llRouteList.removeAllViews();
        if (configPos < 0 || configPos >= configList.size()) return;
        String entry = configList.get(configPos);
        List<String[]> routes = ConfigHelper.getRouteList(entry);
        String currentUrl = Hawk.get(HawkConfig.API_URL, "");

        for (int i = 0; i < routes.size(); i++) {
            String[] route = routes.get(i);
            String routeName = route[0];
            String routeUrl = route[1];

            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_route_name_tv, llRouteList, false);
            TextView tv = item.findViewById(R.id.tvName);
            boolean isSelected = !routeUrl.isEmpty() && routeUrl.equals(currentUrl);
            tv.setText(isSelected ? "● " + routeName : routeName);
            if (isSelected) {
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            tv.setOnClickListener(v -> {
                if (!routeUrl.isEmpty()) {
                    Hawk.put(HawkConfig.API_URL, routeUrl);
                    HistoryHelper.setApiHistory(routeUrl);
                    if (listener != null) listener.onSelected(routeName, routeUrl);
                    dismiss();
                }
            });
            llRouteList.addView(item);
        }
    }

    private boolean isCurrentInEntry(String entry, String currentUrl) {
        if (currentUrl.isEmpty()) return false;
        List<String[]> routes = ConfigHelper.getRouteList(entry);
        for (String[] r : routes) {
            if (currentUrl.equals(r[1])) return true;
        }
        return false;
    }
}
