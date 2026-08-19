package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.OpenListBrowseActivity;
import com.github.tvbox.osc.ui.activity.PushActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;

import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity {
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvDate;
    private TextView tvName;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private View currentView;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    private boolean eventBusRegistered = false;
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            Date date = new Date();
            SimpleDateFormat timeFormat = new SimpleDateFormat("M月d日  HH:mm", Locale.CHINA);
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    boolean useCacheConfig = false;

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        eventBusRegistered = true;
        ControlManager.get().startServer();
        initView();
        initViewModel();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        initData();
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvDate = findViewById(R.id.tvDate);
        this.tvName = findViewById(R.id.tvName);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        // 初始化顶部8个图标按钮
        View.OnClickListener topBtnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.topBtnHistory) {
                    jumpActivity(HistoryActivity.class);
                } else if (id == R.id.topBtnLive) {
                    jumpActivity(LivePlayActivity.class);
                } else if (id == R.id.topBtnSearch) {
                    jumpActivity(SearchActivity.class);
                } else if (id == R.id.topBtnPush) {
                    jumpActivity(PushActivity.class);
                } else if (id == R.id.topBtnFavorite) {
                    jumpActivity(CollectActivity.class);
                } else if (id == R.id.topBtnRouteLine) {
                    showSiteSwitch();
                } else if (id == R.id.topBtnOpenList) {
                    jumpActivity(OpenListBrowseActivity.class);
                } else if (id == R.id.topBtnRoute) {
                    com.github.tvbox.osc.ui.dialog.RouteSelectDialog routeDialog =
                        new com.github.tvbox.osc.ui.dialog.RouteSelectDialog(HomeActivity.this);
                    routeDialog.setOnRouteSelectedListener((name, url) -> {
                        // 弹窗内部已写 API_URL，直接触发刷新（重启 HomeActivity 重新加载新线路的分类）
                        refreshHome();
                    });
                    routeDialog.show();
                } else if (id == R.id.topBtnSetting) {
                    jumpActivity(SettingActivity.class);
                }
            }
        };
        int[] topBtnIds = {
            R.id.topBtnHistory, R.id.topBtnLive, R.id.topBtnSearch, R.id.topBtnPush,
            R.id.topBtnFavorite, R.id.topBtnRouteLine, R.id.topBtnOpenList, R.id.topBtnRoute, R.id.topBtnSetting
        };
        for (int btnId : topBtnIds) {
            View btn = findViewById(btnId);
            if (btn != null) {
                btn.setOnClickListener(topBtnClickListener);
                // 焦点视觉:放大 + 文字加粗,失焦还原,跟下方影视卡片的选中放大保持一致风格
                attachTopBarFocusEffect(btn);
            }
        }
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    View firstChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (firstChild != null) {
                        mGridView.setSelectedPosition(0);
                        firstChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    // 焦点即将离开该分类按钮:立即清除它的白色焦点框,
                    // 避免焦点移到别处后旧项仍残留白框
                    sortAdapter.clearFocus(position);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = view.findViewById(R.id.tvTitle);
                            textView.getPaint().setFakeBoldText(false);
                            if (sortFocused == p) {
                                view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                            } else {
                                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_BBFFFFFF));
                                view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                                view.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
                            }
                            textView.invalidate();
                        }

                        public final int p = position;
                    }, 10);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    HomeActivity.this.currentView = view;
                    HomeActivity.this.isDownOrUp = false;
                    view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (!sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    HomeActivity.this.sortFocusView = view;
                    int oldFocus = HomeActivity.this.sortFocused;
                    HomeActivity.this.sortFocused = position;
                    // 刷新焦点框（旧项取消白框，新项加白框；SortAdapter 内部按当前页/焦点判断双层背景）
                    sortAdapter.refreshFocus(oldFocus, position);
                    // 不再自动切换分类页，需用户点击确认键才切
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView == null) return;
                // 点击确认键：切换到该分类（不重复切同一页）
                if (currentSelected == position) return;
                sortChange = true;
                mHandler.removeCallbacks(mDataRunnable);
                mHandler.post(mDataRunnable);
                // 长按弹筛选已迁移到 setOnItemLongClickListener
            }
        });

        // 长按分类按钮 → 弹筛选
        sortAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            BaseLazyFragment baseLazyFragment = fragments.get(position);
            if ((baseLazyFragment instanceof GridFragment)
                    && !sortAdapter.getItem(position).filters.isEmpty()) {
                ((GridFragment) baseLazyFragment).showFilter();
                return true;
            } else if (baseLazyFragment instanceof UserFragment) {
                showSiteSwitch();
                return true;
            }
            return false;
        });

        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                // 焦点在分类行（mGridView）按上/下键：消费事件 + 必要时强制刷新。
                // 正常情况下 HomeActivity.dispatchKeyEvent 会先一步拦截并消费掉上下键，
                // 这里理论上不会再被触发；保留仅作为兜底，同样只操作 currentSelected（当前实际显示的页面），
                // 不用 sortFocused，避免摸到 ViewPager 还未创建的非当前页 Fragment 而空指针崩溃。
                if (direction != View.FOCUS_UP && direction != View.FOCUS_DOWN) {
                    return false;
                }
                if (currentSelected >= 0 && currentSelected < fragments.size()) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if (baseLazyFragment instanceof GridFragment && !((GridFragment) baseLazyFragment).isLoad()) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                }
                return true;
            }
        });
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if(dataInitOk && jarInitOk){
                    String jar=ApiConfig.get().getHomeSourceBean().getJar();
                    String jarUrl=!jar.isEmpty()?jar:ApiConfig.get().getSpider();
                    String jarSource = jarUrl.split(";md5;")[0];
                    File cspCacheDir = new File(FileUtils.getFilePath() + "/csp/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarFullCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarUrl) + ".jar");
                    Toast.makeText(mContext, "jar缓存已清除", Toast.LENGTH_LONG).show();
                    if (!cspCacheDir.exists() && !jarCacheDir.exists() && !jarFullCacheDir.exists()){
                        refreshHome();
                        return;
                    }
                    new Thread(() -> {
                        try {
                            FileUtils.deleteFile(cspCacheDir);
                            FileUtils.deleteFile(jarCacheDir);
                            FileUtils.deleteFile(jarFullCacheDir);
                            ApiConfig.get().clearJarLoader();
                            refreshHome();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();

                }else {
                    jumpActivity(SettingActivity.class);
                }
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                jumpActivity(SettingActivity.class);
                return true;
            }
        });
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 500);
    }

    /**
     * 给顶部按钮附加焦点视觉效果:
     * - 获焦时:整体放大 1.12 倍 + 文字加粗,动画 220ms,跟下方影视卡片焦点放大风格一致
     * - 失焦时:还原
     *
     * 直接从 LinearLayout 子节点里找 TextView,避免给每个按钮的 TextView 单独加 id。
     */
    private void attachTopBarFocusEffect(View btn) {
        TextView tv = findFirstTextView(btn);
        btn.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(220).start();
                if (tv != null) tv.getPaint().setFakeBoldText(true);
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220).start();
                if (tv != null) tv.getPaint().setFakeBoldText(false);
            }
        });
    }

    /** 遍历 ViewGroup 找第一个 TextView 子节点(顶部按钮布局固定为 [ImageView, TextView]) */
    private TextView findFirstTextView(View v) {
        if (v instanceof TextView) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView t = findFirstTextView(vg.getChildAt(i));
                if (t != null) return t;
            }
        }
        return null;
    }


    private boolean skipNextUpdate = false;

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                showSuccess();
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true));
                } else {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
                }
                initViewPager(absXml);
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                if (home != null && home.getName() != null && !home.getName().isEmpty()) tvName.setText(home.getName());
                tvName.clearAnimation();
            }
        });
    }

    private boolean dataInitOk = false;
    private boolean jarInitOk = false;
    private TipDialog mConfigErrorDialog;

    private void initData() {
        if (dataInitOk && jarInitOk) {
            sourceViewModel.getSort(ApiConfig.get().getHomeSourceBean().getKey());
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            if (!useCacheConfig && Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) {
                jumpActivity(LivePlayActivity.class);
            }
            return;
        }
        tvNameAnimation();
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
//                                if (!useCacheConfig) Toast.makeText(HomeActivity.this, "自定义jar加载成功", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        }, 50);
                    }

                    @Override
                    public void notice(String msg) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        dataInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HomeActivity.this, msg+" jar load err", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        },50);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            @Override
            public void notice(String msg) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) {
                            return;
                        }
                        if (mConfigErrorDialog == null)
                            mConfigErrorDialog = new TipDialog(HomeActivity.this, msg, "重试", "取消", new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }
                            });
                        if (!mConfigErrorDialog.isShowing())
                            mConfigErrorDialog.show();
                    }
                });
            }
        }, this);
    }

    private void initViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                        fragments.add(UserFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(UserFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBackPressed() {
        // 打断加载
        if (isLoading()) {
            refreshEmpty();
            return;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return;
        }

        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            doExit();
            return;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.setSelection(0);
            } else {
                doExit();
            }
        } else if (baseLazyFragment instanceof UserFragment && UserFragment.tvHotList.canScrollVertically(-1)) {
            // 如果 UserFragment 列表可以向上滚动，则滚动到顶部
            UserFragment.tvHotList.scrollToPosition(0);
            this.mGridView.setSelection(0);
        } else {
            doExit();
        }
    }

    private void doExit() {
        // 如果两次返回间隔小于 2000 毫秒，则退出应用
        if (System.currentTimeMillis() - mExitTime < 2000) {
            unregisterEventBus();
            ControlManager.get().stopServer();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (activityManager != null) {
                    for (ActivityManager.AppTask appTask : activityManager.getAppTasks()) {
                        appTask.finishAndRemoveTask();
                    }
                } else {
                    finishAndRemoveTask();
                }
            } else {
                AppManager.getInstance().finishAllActivity();
                finish();
            }
        } else {
            // 否则仅提示用户，再按一次退出应用
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mRunnable);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRunnable);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            String pushUrl = event.obj == null ? null : event.obj.toString();
            if (pushUrl == null || pushUrl.isEmpty()) return;
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", pushUrl);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            } else {
                // DLNA/遥控器投屏：当前接口未配置 push_agent，使用裸 URL 直接播放
                Intent castIntent = new Intent(mContext, com.github.tvbox.osc.ui.activity.CastPlayActivity.class);
                castIntent.putExtra("url", pushUrl);
                castIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(castIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
                showFilterIcon((int) event.obj);
            }
        }
    }

    private void showFilterIcon(int count) {
        boolean visible = count > 0;
        currentView.findViewById(R.id.tvFilterColor).setVisibility(visible ? View.VISIBLE : View.GONE);
        currentView.findViewById(R.id.tvFilter).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (sortFocused != currentSelected) {
                    int oldSelected = currentSelected;
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    changeTop(sortFocused != 0);
                    // 切页后刷新"当前页"蓝色高亮（旧项取消蓝，新项加蓝）
                    sortAdapter.refreshSelection(oldSelected, currentSelected);
                    if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                } else if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                    ((GridFragment) baseLazyFragment).forceRefresh();
                }
            }
        }
    };

    private long menuKeyDownTime = 0;
    private static final long LONG_PRESS_THRESHOLD = 2000; // 设置长按的阈值，单位是毫秒
    private boolean lastVerticalKeyConsumedFromGrid = false;
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (topHide < 0)
            return false;
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                menuKeyDownTime = System.currentTimeMillis();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                long pressDuration = System.currentTimeMillis() - menuKeyDownTime;
                if (pressDuration >= LONG_PRESS_THRESHOLD) {
                    jumpActivity(SettingActivity.class);;
                }else {
                    showSiteSwitch();
                }
            }
        }
        // 分类行（mGridView）上下键统一在这里拦截处理，不再依赖 TvRecyclerView 自身的
        // OnInBorderKeyEventListener（无论焦点在"当前页"按钮还是其它按钮上都会先到这里）。
        // 原因：库内部 focusSearch -> scrollToPosition 在某些焦点位置上会拿到空的
        // RecyclerView 引用而空指针崩溃；在事件到达那段逻辑之前就把事件消费掉，
        // 从根本上避免触发该崩溃路径，同时顺带解决"当前页按钮上按上下键无反应"的问题。
        if ((keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) && mGridView != null) {
            boolean wasInGridView = isFocusInsideGridView();
            if (event.getAction() == KeyEvent.ACTION_DOWN && wasInGridView) {
                handleSortRowVerticalKey(keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
                lastVerticalKeyConsumedFromGrid = true;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP && lastVerticalKeyConsumedFromGrid) {
                lastVerticalKeyConsumedFromGrid = false;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** 当前系统焦点是否落在分类行 mGridView 内部（含其子孙 View） */
    private boolean isFocusInsideGridView() {
        View focused = getCurrentFocus();
        if (focused == null) return false;
        for (View v = focused; v != null; ) {
            if (v == mGridView) return true;
            if (!(v.getParent() instanceof View)) break;
            v = (View) v.getParent();
        }
        return false;
    }

    /**
     * 手动处理分类行上/下键，替代交给 TvRecyclerView 自己去 focusSearch。
     * 下键：把焦点交给"当前正在显示"的那一页内容（注意是 currentSelected，不是 sortFocused ——
     *       ViewPager 默认只创建当前页附近的 Fragment，焦点所在的分类按钮如果不是当前页，
     *       它对应的 GridFragment 很可能还没被创建、mGridView 为 null，这时候如果照样调用
     *       forceRefresh()/requestGridFocus() 会摸到未初始化的内容view，是"非当前页按钮按下键闪退"的真正原因）；
     *       未加载则先强制刷新，加载完成后用户可再按一次下键进入。
     * 上键：把焦点交给顶部图标栏第一个可获焦按钮。
     */
    private void handleSortRowVerticalKey(boolean isDown) {
        if (isDown) {
            if (currentSelected < 0 || currentSelected >= fragments.size()) return;
            BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
            if (baseLazyFragment instanceof GridFragment) {
                GridFragment gridFragment = (GridFragment) baseLazyFragment;
                if (!gridFragment.isLoad()) {
                    gridFragment.forceRefresh();
                    return;
                }
                gridFragment.requestGridFocus();
            } else if (baseLazyFragment instanceof UserFragment) {
                if (UserFragment.tvHotList != null) {
                    UserFragment.tvHotList.requestFocus();
                }
            }
        } else {
            View topBar = findViewById(R.id.topIconBar);
            if (topBar != null) {
                topBar.requestFocus();
            }
        }
    }

    byte topHide = 0;

    private void changeTop(boolean hide) {
        // 顶部栏始终显示，不隐藏
        topHide = 0;
    }

    @Override
    protected void onDestroy() {
        dismissHomeDialogs();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        unregisterEventBus();
        if (isFinishing()) {
            ControlManager.get().stopServer();
        }
    }

    private void unregisterEventBus() {
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this);
            eventBusRegistered = false;
        }
    }

    private SelectDialog<SourceBean> mSiteSwitchDialog;

    void showSiteSwitch() {
        if (isActivityUnavailable()) return;
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (select < 0 || select >= sites.size()) select = 0;
        if (mSiteSwitchDialog == null) {
            mSiteSwitchDialog = new SelectDialog<>(HomeActivity.this);
            TvRecyclerView tvRecyclerView = mSiteSwitchDialog.findViewById(R.id.list);
            // 根据 sites 数量动态计算列数
            int spanCount = (int) Math.floor(sites.size() / 20.0);
            spanCount = Math.min(spanCount, 2);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(mSiteSwitchDialog.getContext(), spanCount + 1));
            // 设置对话框宽度
            ConstraintLayout cl_root = mSiteSwitchDialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            clp.width = AutoSizeUtils.mm2px(mSiteSwitchDialog.getContext(), 380 + 200 * spanCount);
            mSiteSwitchDialog.setTip("请选择首页数据源");
        }
        mSiteSwitchDialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
            @Override
            public void click(SourceBean value, int pos) {
                dismissSiteSwitchDialog();
                ApiConfig.get().setSourceBean(value);
                refreshHome();
            }
            @Override
            public String getDisplay(SourceBean val) {
                return val.getName();
            }
        }, new DiffUtil.ItemCallback<SourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem == newItem;
            }
            @Override
            public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getKey().equals(newItem.getKey());
            }
        }, sites, select);
        if (!mSiteSwitchDialog.isShowing())
            mSiteSwitchDialog.show();
    }

    private void refreshHome()
    {
        if (Thread.currentThread() != android.os.Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    refreshHome();
                }
            });
            return;
        }
        if (isActivityUnavailable()) {
            return;
        }
        dismissHomeDialogs();
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // 线路切换后强制不走分类缓存（确保加载的是新线路的真实分类数据）
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", false);
        intent.putExtras(bundle);
        HomeActivity.this.startActivity(intent);
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }

    private void dismissHomeDialogs() {
        dismissConfigErrorDialog();
        dismissSiteSwitchDialog();
    }

    private void dismissConfigErrorDialog() {
        if (mConfigErrorDialog != null) {
            if (mConfigErrorDialog.isShowing()) {
                mConfigErrorDialog.dismiss();
            }
            mConfigErrorDialog = null;
        }
    }

    private void dismissSiteSwitchDialog() {
        if (mSiteSwitchDialog != null) {
            if (mSiteSwitchDialog.isShowing()) {
                mSiteSwitchDialog.dismiss();
            }
            mSiteSwitchDialog = null;
        }
    }

    private void refreshEmpty()
    {
        skipNextUpdate=true;
        showSuccess();
        sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
        initViewPager(null);
        tvName.clearAnimation();
    }

    private void tvNameAnimation()
    {
        AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setStartOffset(20);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        tvName.startAnimation(blinkAnimation);
    }
}
