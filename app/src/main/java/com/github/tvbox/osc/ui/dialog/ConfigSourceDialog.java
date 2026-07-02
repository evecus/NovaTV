package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.ConfigHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 仓库/线路配置弹窗（点播配置地址管理）
 */
public class ConfigSourceDialog extends BaseDialog {

    private LinearLayout llConfigList;
    private TextView tvEmpty;
    private EditText etName, etUrl;
    private List<String> configList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnConfigChangedListener {
        void onChanged();
    }

    private OnConfigChangedListener listener;

    public void setOnConfigChangedListener(OnConfigChangedListener l) {
        this.listener = l;
    }

    public ConfigSourceDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_config_source);
        setCanceledOnTouchOutside(false);

        llConfigList = findViewById(R.id.llConfigList);
        tvEmpty = findViewById(R.id.tvEmpty);
        etName = findViewById(R.id.etName);
        etUrl = findViewById(R.id.etUrl);

        // QR Code
        ImageView ivQRCode = findViewById(R.id.ivQRCode);
        TextView tvAddress = findViewById(R.id.tvAddress);
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(address);
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(
                address + "api.html",
                AutoSizeUtils.mm2px(context, 250),
                AutoSizeUtils.mm2px(context, 250)));

        // Storage permission
        findViewById(R.id.storagePermission).setOnClickListener(v -> {
            if (XXPermissions.isGranted(context, Permission.Group.STORAGE)) {
                Toast.makeText(context, "已获得存储权限", Toast.LENGTH_SHORT).show();
            } else {
                XXPermissions.with(context)
                        .permission(Permission.Group.STORAGE)
                        .request(new OnPermissionCallback() {
                            @Override
                            public void onGranted(List<String> permissions, boolean all) {
                                if (all) Toast.makeText(context, "已获得存储权限", Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onDenied(List<String> permissions, boolean never) {
                                if (never) {
                                    Toast.makeText(context, "请在系统设置中开启存储权限", Toast.LENGTH_SHORT).show();
                                    XXPermissions.startPermissionActivity((Activity) context, permissions);
                                }
                            }
                        });
            }
        });

        // Add button — restoring this at end of edit
        View btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(defaultAddClick);

        loadList();
        refreshList();
    }

    private final View.OnClickListener defaultAddClick = v -> {
        String name = etName.getText().toString().trim();
        String url = etUrl.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(getContext(), "请输入名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(getContext(), "请输入地址", Toast.LENGTH_SHORT).show();
            return;
        }
        etName.setText("");
        etUrl.setText("");
        fetchAndAdd(name, url, -1);
    };

    private void loadList() {
        configList.clear();
        ArrayList<String> saved = Hawk.get(HawkConfig.VOD_CONFIG_LIST, new ArrayList<String>());
        configList.addAll(saved);
    }

    private void saveList() {
        Hawk.put(HawkConfig.VOD_CONFIG_LIST, new ArrayList<>(configList));
        if (listener != null) listener.onChanged();
    }

    private void refreshList() {
        llConfigList.removeAllViews();
        tvEmpty.setVisibility(configList.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < configList.size(); i++) {
            final int pos = i;
            String entry = configList.get(i);
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_config_entry_tv, llConfigList, false);
            TextView tvType = item.findViewById(R.id.tvType);
            TextView tvName = item.findViewById(R.id.tvName);
            tvType.setText(ConfigHelper.isWarehouse(entry) ? "[仓库]" : "[线路]");
            tvName.setText(ConfigHelper.getVodName(entry));
            item.findViewById(R.id.btnEdit).setOnClickListener(ev -> showEditDialog(pos));
            item.findViewById(R.id.btnDelete).setOnClickListener(ev -> {
                configList.remove(pos);
                saveList();
                refreshList();
            });
            llConfigList.addView(item);
        }
    }

    private void fetchAndAdd(String name, String url, int editPos) {
        Toast.makeText(getContext(), "正在解析...", Toast.LENGTH_SHORT).show();
        OkHttpClient client = OkGoHelper.getDefaultClient();
        if (client == null) client = new OkHttpClient();
        Request request = new Request.Builder().url(url).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                mainHandler.post(() -> {
                    String entry = ConfigHelper.buildVodEntry(name, url, null);
                    applyEntry(entry, editPos);
                    Toast.makeText(getContext(), "解析失败，已作为线路保存", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                mainHandler.post(() -> {
                    String entry = ConfigHelper.parseVodEntry(name, url, body);
                    applyEntry(entry, editPos);
                    Toast.makeText(getContext(), ConfigHelper.isWarehouse(entry) ? "已添加仓库：" + name : "已添加线路：" + name, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void applyEntry(String entry, int editPos) {
        if (editPos >= 0 && editPos < configList.size()) {
            configList.set(editPos, entry);
        } else {
            configList.add(entry);
        }
        saveList();
        refreshList();
    }

    private void showEditDialog(int pos) {
        String entry = configList.get(pos);
        etName.setText(ConfigHelper.getVodName(entry));
        etUrl.setText(ConfigHelper.getVodUrl(entry));
        View btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(url)) return;
            etName.setText("");
            etUrl.setText("");
            btnAdd.setOnClickListener(defaultAddClick);
            String oldUrl = ConfigHelper.getVodUrl(configList.get(pos));
            if (url.equals(oldUrl)) {
                String routes = ConfigHelper.getVodRoutes(configList.get(pos));
                configList.set(pos, ConfigHelper.buildVodEntry(name, url, TextUtils.isEmpty(routes) ? null : routes));
                saveList();
                refreshList();
            } else {
                fetchAndAdd(name, url, pos);
            }
        });
    }
}
