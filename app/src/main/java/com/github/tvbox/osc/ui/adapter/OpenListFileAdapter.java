package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.OpenListFile;

import java.util.ArrayList;

public class OpenListFileAdapter extends BaseQuickAdapter<OpenListFile, BaseViewHolder> {
    // 标记“上一级目录”虚拟条目
    private OpenListFile parentItem;

    public OpenListFileAdapter() {
        super(R.layout.item_openlist_file, new ArrayList<>());
    }

    public void setParentItem(OpenListFile parentItem) {
        this.parentItem = parentItem;
    }

    @Override
    protected void convert(BaseViewHolder helper, OpenListFile item) {
        boolean isParent = item == parentItem;
        helper.setText(R.id.tvName, isParent ? ".." : item.name);
        if (isParent) {
            helper.setImageResource(R.id.ivIcon, R.drawable.icon_folder);
            helper.setText(R.id.tvInfo, "返回上级");
        } else if (item.isDir) {
            helper.setImageResource(R.id.ivIcon, R.drawable.icon_folder);
            helper.setText(R.id.tvInfo, "进入");
        } else if (item.isVideo()) {
            helper.setImageResource(R.id.ivIcon, R.drawable.icon_video);
            helper.setText(R.id.tvInfo, item.formattedSize());
        } else if (item.isAudio()) {
            helper.setImageResource(R.id.ivIcon, R.drawable.icon_audio);
            helper.setText(R.id.tvInfo, item.formattedSize());
        } else {
            helper.setImageResource(R.id.ivIcon, R.drawable.icon_file);
            helper.setText(R.id.tvInfo, item.formattedSize());
        }
    }
}
