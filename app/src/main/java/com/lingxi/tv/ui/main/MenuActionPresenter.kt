package com.lingxi.tv.ui.main

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.lingxi.tv.R

/**
 * 菜单项 Presenter - 显示一个可聚焦的文本卡片
 * 用于账号入口行（登录/节点切换等）
 */
class MenuActionPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val textView = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(360, 120)
            setPadding(24, 16, 24, 16)
            gravity = Gravity.CENTER
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 16f
            setBackgroundColor(context.getColor(R.color.card_bg))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? MainBrowseFragment.MenuAction ?: return
        (viewHolder.view as TextView).text = action.label
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as TextView).text = ""
    }
}
