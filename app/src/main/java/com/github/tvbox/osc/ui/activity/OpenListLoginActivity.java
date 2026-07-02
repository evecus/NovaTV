package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.ImageView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OpenListApi;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * OpenList 网盘登录页
 */
public class OpenListLoginActivity extends BaseActivity {
    private EditText etServerUrl;
    private EditText etUsername;
    private EditText etPassword;
    private TextView tvError;
    private TextView btnLogin;
    private ProgressBar pbLogin;
    private CheckBox cbSaveLogin;
    private boolean requesting = false;
    private ImageView ivRemoteQR;
    private TextView tvRemoteAddr;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_login;
    }

    @Override
    protected void init() {
        etServerUrl = findViewById(R.id.etServerUrl);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvOpenListError);
        btnLogin = findViewById(R.id.btnLogin);
        pbLogin = findViewById(R.id.pbOpenListLogin);
        cbSaveLogin = findViewById(R.id.cbSaveLogin);

        // 读取"记住登录信息"勾选状态
        boolean saveLogin = com.orhanobut.hawk.Hawk.get(HawkConfig.OPENLIST_SAVE_LOGIN, false);
        cbSaveLogin.setChecked(saveLogin);

        // 回填登录信息
        String lastUrl = OpenListApi.getServerUrl();
        if (!TextUtils.isEmpty(lastUrl)) {
            etServerUrl.setText(lastUrl);
        }
        if (saveLogin) {
            // 已勾选"记住登录信息"：回填用户名和密码
            String lastUser = com.orhanobut.hawk.Hawk.get(HawkConfig.OPENLIST_USERNAME, "");
            String lastPwd = com.orhanobut.hawk.Hawk.get(HawkConfig.OPENLIST_PASSWORD, "");
            if (!TextUtils.isEmpty(lastUser)) etUsername.setText(lastUser);
            if (!TextUtils.isEmpty(lastPwd)) etPassword.setText(lastPwd);
        }
        // 若未勾选，只回填了服务器地址，不回填用户名和密码

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogin();
            }
        });

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            doLogin();
            return true;
        });

        // 远程推送入口 —— 显示 QR 码和访问地址
        ivRemoteQR  = findViewById(R.id.ivRemoteQR);
        tvRemoteAddr = findViewById(R.id.tvRemoteAddr);
        String remoteAddr = ControlManager.get().getAddress(false);
        tvRemoteAddr.setText(remoteAddr);
        if (!remoteAddr.isEmpty()) {
            int qrSize = AutoSizeUtils.mm2px(this, 180);
            ivRemoteQR.setImageBitmap(QRCodeGen.generateBitmap(remoteAddr, qrSize, qrSize));
        }

        etServerUrl.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(etServerUrl.getText().toString())) {
                    etServerUrl.requestFocus();
                } else if (TextUtils.isEmpty(etUsername.getText().toString())) {
                    etUsername.requestFocus();
                } else if (TextUtils.isEmpty(etPassword.getText().toString())) {
                    etPassword.requestFocus();
                } else {
                    btnLogin.requestFocus();
                }
            }
        }, 200);
    }

    private void doLogin() {
        if (requesting) return;
        String url = etServerUrl.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pwd = etPassword.getText().toString();
        if (TextUtils.isEmpty(url)) {
            showError("请输入服务器地址");
            etServerUrl.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(user)) {
            showError("请输入用户名");
            etUsername.requestFocus();
            return;
        }
        requesting = true;
        tvError.setVisibility(View.GONE);
        pbLogin.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        final boolean saveLogin = cbSaveLogin.isChecked();

        OpenListApi.login(url, user, pwd, new OpenListApi.Callback<String>() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    requesting = false;
                    pbLogin.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    // 保存"记住登录信息"勾选状态
                    com.orhanobut.hawk.Hawk.put(HawkConfig.OPENLIST_SAVE_LOGIN, saveLogin);
                    if (saveLogin) {
                        // 勾选时：保存用户名和密码（URL 在 OpenListApi.login 中已保存）
                        com.orhanobut.hawk.Hawk.put(HawkConfig.OPENLIST_USERNAME, user);
                        com.orhanobut.hawk.Hawk.put(HawkConfig.OPENLIST_PASSWORD, pwd);
                    } else {
                        // 未勾选时：清除已保存的用户名和密码
                        com.orhanobut.hawk.Hawk.put(HawkConfig.OPENLIST_USERNAME, "");
                        com.orhanobut.hawk.Hawk.put(HawkConfig.OPENLIST_PASSWORD, "");
                    }

                    Toast.makeText(mContext, "登录成功", Toast.LENGTH_SHORT).show();
                    jumpActivity(OpenListBrowseActivity.class);
                    finish();
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    requesting = false;
                    pbLogin.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    showError(msg);
                });
            }
        });
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
