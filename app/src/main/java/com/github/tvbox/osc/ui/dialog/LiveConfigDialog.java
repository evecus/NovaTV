package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.ConfigHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 直播地址配置弹窗
 */
public class LiveConfigDialog extends BaseDialog {

    private LinearLayout llSourceList;
    private TextView tvEmpty;
    private EditText etName, etUrl;
    private List<String> sourceList = new ArrayList<>();

    public interface OnLiveChangedListener {
        void onChanged(String liveUrl);
    }

    private OnLiveChangedListener listener;

    public void setOnLiveChangedListener(OnLiveChangedListener l) {
        this.listener = l;
    }

    public LiveConfigDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_live_config);
        setCanceledOnTouchOutside(false);

        llSourceList = findViewById(R.id.llSourceList);
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
        sourceList.add(ConfigHelper.buildLiveEntry(name, url));
        saveList();
        refreshList();
    };

    private void loadList() {
        sourceList.clear();
        ArrayList<String> saved = Hawk.get(HawkConfig.LIVE_SOURCE_LIST, new ArrayList<String>());
        sourceList.addAll(saved);
    }

    private void saveList() {
        Hawk.put(HawkConfig.LIVE_SOURCE_LIST, new ArrayList<>(sourceList));
    }

    private void refreshList() {
        llSourceList.removeAllViews();
        tvEmpty.setVisibility(sourceList.isEmpty() ? View.VISIBLE : View.GONE);
        String currentLiveUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");

        for (int i = 0; i < sourceList.size(); i++) {
            final int pos = i;
            String entry = sourceList.get(i);
            String eName = ConfigHelper.getLiveName(entry);
            String eUrl = ConfigHelper.getLiveUrl(entry);
            boolean isSelected = !eUrl.isEmpty() && eUrl.equals(currentLiveUrl);

            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_live_source_tv, llSourceList, false);
            TextView tvName = item.findViewById(R.id.tvName);
            tvName.setText(isSelected ? "● " + eName : eName);
            if (isSelected) {
                tvName.setTextColor(Color.WHITE);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            // Click to select this live source
            tvName.setOnClickListener(v -> {
                Hawk.put(HawkConfig.LIVE_API_URL, eUrl);
                HistoryHelper.setLiveApiHistory(eUrl);
                refreshList();
                if (listener != null) listener.onChanged(eUrl);
            });

            item.findViewById(R.id.btnEdit).setOnClickListener(v -> {
                etName.setText(eName);
                etUrl.setText(eUrl);
                View btnAdd = findViewById(R.id.btnAdd);
                btnAdd.setOnClickListener(ev -> {
                    String newName = etName.getText().toString().trim();
                    String newUrl = etUrl.getText().toString().trim();
                    if (!TextUtils.isEmpty(newName) && !TextUtils.isEmpty(newUrl)) {
                        sourceList.set(pos, ConfigHelper.buildLiveEntry(newName, newUrl));
                        if (eUrl.equals(currentLiveUrl)) {
                            Hawk.put(HawkConfig.LIVE_API_URL, newUrl);
                            HistoryHelper.setLiveApiHistory(newUrl);
                            if (listener != null) listener.onChanged(newUrl);
                        }
                        saveList();
                        refreshList();
                        etName.setText("");
                        etUrl.setText("");
                        btnAdd.setOnClickListener(defaultAddClick);
                    }
                });
            });

            item.findViewById(R.id.btnDelete).setOnClickListener(v -> {
                if (eUrl.equals(currentLiveUrl)) {
                    Hawk.put(HawkConfig.LIVE_API_URL, "");
                    if (listener != null) listener.onChanged("");
                }
                sourceList.remove(pos);
                saveList();
                refreshList();
            });

            llSourceList.addView(item);
        }
    }
}
