package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

/**
 * 首页分类行 adapter:双层状态显示
 * - 当前选中的分类 (currentSelected):文字下方显示蓝色横线作为标识,无填充背景
 * - 当前焦点的分类 (focusedPos):白色边框框住,焦点离开后自动消失
 * - 优先级:当前页 > 焦点 > 默认
 *
 * 重要:这里不能给 itemView 设置 setOnFocusChangeListener —— TvRecyclerView 库自身会
 * 给 itemView 设置该监听器来驱动 onItemSelected / onItemPreSelected 回调,
 * 一旦被覆盖,HomeActivity 的 sortFocused 就不会更新,遥控器点击切页会失效。
 * 因此焦点框的刷新完全由 HomeActivity 在 onItemSelected / onItemPreSelected
 * 回调里通过 refreshFocus / clearFocus 驱动。
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int currentSelected = 0;
    /** 当前焦点位置(仅用于 UI),由 HomeActivity 回调维护 */
    private int focusedPos = -1;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    public void setCurrentSelected(int position) {
        this.currentSelected = position;
    }

    /**
     * 焦点切换 (onItemSelected 触发):更新焦点位置并刷新新旧两项。
     * 旧项取消白框、新项加白框。
     */
    public void refreshFocus(int oldPos, int newPos) {
        this.focusedPos = newPos;
        if (oldPos >= 0 && oldPos < getItemCount()) notifyItemChanged(oldPos);
        if (newPos >= 0 && newPos < getItemCount()) notifyItemChanged(newPos);
    }

    /**
     * 焦点离开 (onItemPreSelected 触发):清除指定项的白色焦点框。
     */
    public void clearFocus(int pos) {
        if (focusedPos != pos) return;
        focusedPos = -1;
        if (pos >= 0 && pos < getItemCount()) notifyItemChanged(pos);
    }

    /**
     * 切换当前选中分类 (点击确认键):刷新新旧两项。
     */
    public void refreshSelection(int oldPos, int newPos) {
        this.currentSelected = newPos;
        notifyItemChanged(oldPos);
        notifyItemChanged(newPos);
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        View itemView = helper.itemView;
        TextView tv = helper.getView(R.id.tvTitle);
        View underline = helper.getView(R.id.tvUnderline);
        int position = helper.getLayoutPosition();
        if (position == RecyclerView.NO_POSITION) position = helper.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;

        boolean isSelected = (position == currentSelected);
        boolean isFocused = (position == focusedPos);

        if (isSelected) {
            // 当前选中分类:显示下方蓝色横线,移除填充背景
            underline.setVisibility(View.VISIBLE);
            if (isFocused) {
                // 选中且焦点在身:蓝色横线 + 白色焦点框同时显示,遥控器焦点可见
                itemView.setBackground(makeBg(Color.TRANSPARENT, 0xFFFFFFFF, 3));
            } else {
                itemView.setBackground(makeBg(Color.TRANSPARENT, 0));
            }
            tv.setTextColor(Color.WHITE);
            tv.getPaint().setFakeBoldText(true);
        } else if (isFocused) {
            // 当前焦点:白色边框
            underline.setVisibility(View.GONE);
            itemView.setBackground(makeBg(Color.TRANSPARENT, 0xFFFFFFFF, 3));
            tv.setTextColor(Color.WHITE);
            tv.getPaint().setFakeBoldText(true);
        } else {
            // 默认状态
            underline.setVisibility(View.GONE);
            itemView.setBackground(makeBg(Color.TRANSPARENT, 0));
            tv.setTextColor(0xFFBBBBBB);
            tv.getPaint().setFakeBoldText(false);
        }
    }

    private GradientDrawable makeBg(int fillColor, int strokeColor) {
        return makeBg(fillColor, strokeColor, 0);
    }

    private GradientDrawable makeBg(int fillColor, int strokeColor, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(15));
        gd.setColor(fillColor);
        if (strokeWidth > 0) {
            gd.setStroke(dp(strokeWidth), strokeColor);
        }
        return gd;
    }

    private int dp(int v) {
        if (mContext == null) return v;
        return (int) (v * mContext.getResources().getDisplayMetrics().density);
    }
}
