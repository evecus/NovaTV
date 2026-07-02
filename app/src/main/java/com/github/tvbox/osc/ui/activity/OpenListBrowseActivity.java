package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.OpenListFile;
import com.github.tvbox.osc.bean.OpenListFsListData;
import com.github.tvbox.osc.ui.adapter.OpenListFileAdapter;
import com.github.tvbox.osc.util.OpenListApi;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * OpenList 网盘浏览页：登录后展示挂载的网盘目录，可逐层进入，
 * 点击视频/音频文件分别跳转到全屏视频/音乐播放页。
 */
public class OpenListBrowseActivity extends BaseActivity {
    private TextView tvPath;
    private TextView tvEmpty;
    private TextView tvHome;
    private TextView tvLogout;
    private View loadingRoot;
    private TvRecyclerView fileList;
    private OpenListFileAdapter adapter;
    private String currentPath = "/";
    private boolean requesting = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_browse;
    }

    @Override
    protected void init() {
        if (!OpenListApi.isLogin()) {
            jumpActivity(OpenListLoginActivity.class);
            finish();
            return;
        }

        tvPath = findViewById(R.id.tvPath);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvHome   = findViewById(R.id.tvHome);
        tvLogout = findViewById(R.id.tvLogout);
        loadingRoot = findViewById(R.id.loadingRoot);
        fileList = findViewById(R.id.fileList);

        adapter = new OpenListFileAdapter();
        fileList.setAdapter(adapter);
        fileList.setLayoutManager(new V7LinearLayoutManager(this, 1, false));
        fileList.setSpacingWithMargins(0, AutoSizeUtils.mm2px(this, 8));
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                OpenListFile item = (OpenListFile) adapter.getItem(position);
                if (item != null) open(item);
            }
        });

        tvHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpActivity(HomeActivity.class);
                finish();
            }
        });

        tvLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenListApi.logout();
                jumpActivity(OpenListLoginActivity.class);
                finish();
            }
        });

        loadDir("/");
    }

    private boolean isRoot(String path) {
        return path == null || path.isEmpty() || path.equals("/");
    }

    private String parentOf(String path) {
        if (isRoot(path)) return "/";
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int idx = p.lastIndexOf('/');
        if (idx <= 0) return "/";
        return p.substring(0, idx);
    }

    private void loadDir(final String path) {
        if (requesting) return;
        requesting = true;
        loadingRoot.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        fileList.setVisibility(View.GONE);
        OpenListApi.listFiles(path, new OpenListApi.Callback<OpenListFsListData>() {
            @Override
            public void onSuccess(final OpenListFsListData data) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        requesting = false;
                        if (isActivityUnavailable()) return;
                        currentPath = path;
                        tvPath.setText(currentPath);
                        loadingRoot.setVisibility(View.GONE);
                        fileList.setVisibility(View.VISIBLE);

                        List<OpenListFile> files = new ArrayList<>();
                        OpenListFile parentItem = null;
                        if (!isRoot(currentPath)) {
                            parentItem = new OpenListFile();
                            parentItem.name = "..";
                            parentItem.isDir = true;
                            parentItem.parentPath = parentOf(currentPath);
                            files.add(parentItem);
                        }
                        if (data.content != null) {
                            List<OpenListFile> sorted = new ArrayList<>(data.content);
                            for (OpenListFile f : sorted) f.parentPath = currentPath;
                            Collections.sort(sorted, new Comparator<OpenListFile>() {
                                @Override
                                public int compare(OpenListFile a, OpenListFile b) {
                                    if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
                                    return a.name.compareToIgnoreCase(b.name);
                                }
                            });
                            files.addAll(sorted);
                        }
                        adapter.setParentItem(parentItem);
                        adapter.setNewData(files);
                        tvEmpty.setVisibility((data.content == null || data.content.isEmpty()) ? View.VISIBLE : View.GONE);
                        fileList.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!isActivityUnavailable()) fileList.requestFocus();
                            }
                        }, 100);
                    }
                });
            }

            @Override
            public void onError(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        requesting = false;
                        if (isActivityUnavailable()) return;
                        loadingRoot.setVisibility(View.GONE);
                        fileList.setVisibility(View.VISIBLE);
                        Toast.makeText(mContext, TextUtils.isEmpty(msg) ? "目录加载失败" : msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void open(OpenListFile item) {
        if (item.isDir) {
            // “..” 虚拟条目的 parentPath 已是上一级路径
            loadDir(item.name.equals("..") ? item.parentPath : item.fullPath());
            return;
        }
        String path = item.fullPath();
        if (item.isVideo()) {
            Bundle bundle = new Bundle();
            bundle.putString("path", path);
            bundle.putString("name", item.name);
            jumpActivity(OpenListVideoPlayerActivity.class, bundle);
        } else if (item.isAudio()) {
            Bundle bundle = new Bundle();
            bundle.putString("path", path);
            bundle.putString("name", item.name);
            jumpActivity(OpenListAudioPlayerActivity.class, bundle);
        } else {
            Toast.makeText(mContext, "暂不支持播放该类型文件", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (!isRoot(currentPath)) {
                loadDir(parentOf(currentPath));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }
}
