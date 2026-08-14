package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 设置页 - 弹幕设置弹窗（地址、字号、屏占比、滚动速度、透明度）
 * 左侧为设置项列表，右侧为对应设置项的输入框/按钮/选项。
 */
public class DanmuFullSettingDialog extends BaseDialog {

    private static final String[] ITEM_NAMES = {"地址", "字号", "屏占比", "滚动速度", "透明度"};
    private static final int IDX_API = 0;
    private static final int IDX_SIZE = 1;
    private static final int IDX_RATIO = 2;
    private static final int IDX_SPEED = 3;
    private static final int IDX_ALPHA = 4;

    private static final List<Float> SIZES = Arrays.asList(0.6f, 0.8f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.8f, 2.0f);
    private static final List<Integer> RATIOS = Arrays.asList(10, 20, 30, 50, 75, 100);
    private static final List<Float> SPEEDS = Arrays.asList(2.4f, 1.8f, 1.5f, 1.0f);
    private static final String[] SPEED_NAMES = {"超慢", "慢", "适中", "快"};
    private static final List<Float> ALPHAS = Arrays.asList(1.0f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f);

    private LinearLayout llItemList;
    private TextView tvPanelTitle;
    private View panelApi, panelSize, panelRatio, panelSpeed, panelAlpha;
    private EditText inputApi;
    private LinearLayout llSizeOptions, llRatioOptions, llSpeedOptions, llAlphaOptions;

    private int selectedItem = IDX_API;
    private OnListener listener;

    public interface OnListener {
        void onChange();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public DanmuFullSettingDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_danmu_full_setting);
        setCanceledOnTouchOutside(true);

        llItemList = findViewById(R.id.llDanmuItemList);
        tvPanelTitle = findViewById(R.id.tvDanmuPanelTitle);
        panelApi = findViewById(R.id.panelApi);
        panelSize = findViewById(R.id.panelSize);
        panelRatio = findViewById(R.id.panelRatio);
        panelSpeed = findViewById(R.id.panelSpeed);
        panelAlpha = findViewById(R.id.panelAlpha);
        inputApi = findViewById(R.id.inputApi);
        llSizeOptions = findViewById(R.id.llSizeOptions);
        llRatioOptions = findViewById(R.id.llRatioOptions);
        llSpeedOptions = findViewById(R.id.llSpeedOptions);
        llAlphaOptions = findViewById(R.id.llAlphaOptions);

        initItemList();
        initApiPanel();
        initSizePanel();
        initRatioPanel();
        initSpeedPanel();
        initAlphaPanel();

        selectItem(IDX_API);
    }

    private void initItemList() {
        llItemList.removeAllViews();
        for (int i = 0; i < ITEM_NAMES.length; i++) {
            final int pos = i;
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_route_name_tv, llItemList, false);
            TextView tv = item.findViewById(R.id.tvName);
            tv.setText(ITEM_NAMES[i]);
            item.setTag(tv);
            tv.setOnClickListener(v -> selectItem(pos));
            llItemList.addView(item);
        }
    }

    private void selectItem(int pos) {
        selectedItem = pos;
        // Highlight left list
        for (int i = 0; i < llItemList.getChildCount(); i++) {
            TextView tv = (TextView) llItemList.getChildAt(i).getTag();
            boolean sel = i == pos;
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            tv.setText((sel ? "● " : "") + ITEM_NAMES[i]);
        }
        // Show corresponding panel
        panelApi.setVisibility(pos == IDX_API ? View.VISIBLE : View.GONE);
        panelSize.setVisibility(pos == IDX_SIZE ? View.VISIBLE : View.GONE);
        panelRatio.setVisibility(pos == IDX_RATIO ? View.VISIBLE : View.GONE);
        panelSpeed.setVisibility(pos == IDX_SPEED ? View.VISIBLE : View.GONE);
        panelAlpha.setVisibility(pos == IDX_ALPHA ? View.VISIBLE : View.GONE);
        tvPanelTitle.setText("弹幕" + ITEM_NAMES[pos]);
    }

    private void initApiPanel() {
        inputApi.setText(Hawk.get(HawkConfig.DANMU_API, ""));
        String defaultApi = DanmakuApi.getDisplayApiUrl();
        inputApi.setHint(defaultApi.isEmpty() ? "请输入弹幕搜索地址" : defaultApi);
        findViewById(R.id.apiDefault).setOnClickListener(v -> {
            DanmakuApi.setUseDefault(true);
            inputApi.setText("");
            notifyChanged();
        });
        findViewById(R.id.apiSubmit).setOnClickListener(v -> saveApi(inputApi.getText().toString().trim()));
        inputApi.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                saveApi(inputApi.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void saveApi(String api) {
        DanmakuApi.setCustomApi(api);
        notifyChanged();
    }

    private void initSizePanel() {
        float current = DanmuHelper.getSizeScale();
        List<View> chips = new ArrayList<>();
        for (Float size : SIZES) {
            TextView chip = createChip(String.format("%.1fx", size));
            chips.add(chip);
            llSizeOptions.addView(chip);
        }
        highlightChips(chips, SIZES, current);
        for (int i = 0; i < SIZES.size(); i++) {
            final float value = SIZES.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setSizeScale(value);
                highlightChips(chips, SIZES, value);
                notifyChanged();
            });
        }
    }

    private void initRatioPanel() {
        int current = DanmuHelper.getScreenRatio();
        List<View> chips = new ArrayList<>();
        for (Integer ratio : RATIOS) {
            TextView chip = createChip(ratio + "%");
            chips.add(chip);
            llRatioOptions.addView(chip);
        }
        highlightChipsInt(chips, RATIOS, current);
        for (int i = 0; i < RATIOS.size(); i++) {
            final int value = RATIOS.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setScreenRatio(value);
                highlightChipsInt(chips, RATIOS, value);
                // 屏占比改变了弹幕轨道画布的高度，弹幕库需要用新的高度重新
                // prepare() 才能正确重新计算轨道，否则会用旧高度下算好的
                // 轨迹继续绘制，导致弹幕重叠或看起来没有变化。
                notifyChanged(true);
            });
        }
    }

    private void initSpeedPanel() {
        float current = DanmuHelper.getSpeed();
        List<View> chips = new ArrayList<>();
        for (int i = 0; i < SPEEDS.size(); i++) {
            TextView chip = createChip(SPEED_NAMES[i]);
            chips.add(chip);
            llSpeedOptions.addView(chip);
        }
        highlightChips(chips, SPEEDS, current);
        for (int i = 0; i < SPEEDS.size(); i++) {
            final float value = SPEEDS.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setSpeed(value);
                highlightChips(chips, SPEEDS, value);
                notifyChanged();
            });
        }
    }

    private void initAlphaPanel() {
        float current = DanmuHelper.getAlpha();
        List<View> chips = new ArrayList<>();
        for (Float alpha : ALPHAS) {
            TextView chip = createChip(String.valueOf(alpha));
            chips.add(chip);
            llAlphaOptions.addView(chip);
        }
        highlightChips(chips, ALPHAS, current);
        for (int i = 0; i < ALPHAS.size(); i++) {
            final float value = ALPHAS.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setAlpha(value);
                highlightChips(chips, ALPHAS, value);
                notifyChanged();
            });
        }
    }

    private TextView createChip(String text) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = getContext().getResources().getDimensionPixelSize(R.dimen.vs_8);
        tv.setLayoutParams(lp);
        tv.setBackgroundResource(R.drawable.button_danmu_setting);
        tv.setFocusable(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, getContext().getResources().getDimensionPixelSize(R.dimen.vs_12),
                0, getContext().getResources().getDimensionPixelSize(R.dimen.vs_12));
        tv.setText(text);
        tv.setTextColor(getContext().getResources().getColor(R.color.dialog_text_primary));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getContext().getResources().getDimension(R.dimen.ts_22));
        return tv;
    }

    private void highlightChips(List<View> chips, List<Float> values, float selected) {
        for (int i = 0; i < chips.size(); i++) {
            boolean sel = Math.abs(values.get(i) - selected) < 0.001f;
            markChip((TextView) chips.get(i), sel);
        }
    }

    private void highlightChipsInt(List<View> chips, List<Integer> values, int selected) {
        for (int i = 0; i < chips.size(); i++) {
            boolean sel = values.get(i) == selected;
            markChip((TextView) chips.get(i), sel);
        }
    }

    private void markChip(TextView tv, boolean selected) {
        tv.setSelected(selected);
        tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(selected
                ? getContext().getResources().getColor(R.color.dialog_control_stroke_focused)
                : getContext().getResources().getColor(R.color.dialog_text_primary));
    }

    private void notifyChanged() {
        notifyChanged(false);
    }

    /**
     * @param reload 是否需要重新 prepare 弹幕（画布尺寸变化，如屏占比调整时必须为 true，
     *               否则弹幕库会继续用旧尺寸算出的轨迹绘制，造成重叠或视觉上无变化）
     */
    private void notifyChanged(boolean reload) {
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, reload));
        if (listener != null) listener.onChange();
    }
}
