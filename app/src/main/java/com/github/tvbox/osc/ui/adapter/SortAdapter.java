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
 * 首页分类行 adapter：双层状态显示
 * - 当前选中的分类（currentSelected）：蓝色高亮常驻
 * - 当前焦点的分类（sortFocused）：白色边框框住
 * - 优先级：当前页 > 焦点 > 默认
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int currentSelected = 0;
    private int sortFocused = -1;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    public void setCurrentSelected(int position) {
        this.currentSelected = position;
    }

    public void setSortFocused(int position) {
        this.sortFocused = position;
    }

    /** 焦点切换：刷新新旧两项（convert 重设背景与文字） */
    public void refreshFocus(int oldPos, int newPos) {
        this.sortFocused = newPos;
        notifyItemChanged(oldPos);
        notifyItemChanged(newPos);
    }

    /** 当前页切换：刷新新旧两项 */
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
        int position = helper.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;

        if (position == currentSelected) {
            itemView.setBackground(makeBg(0xFF2196F3, 0));
            tv.setTextColor(Color.WHITE);
            tv.getPaint().setFakeBoldText(true);
        } else if (position == sortFocused) {
            itemView.setBackground(makeBg(Color.TRANSPARENT, 0xFFFFFFFF, 3));
            tv.setTextColor(Color.WHITE);
            tv.getPaint().setFakeBoldText(true);
        } else {
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