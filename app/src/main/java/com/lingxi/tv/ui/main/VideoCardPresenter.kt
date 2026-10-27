package com.lingxi.tv.ui.main

import android.content.Context
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.lingxi.tv.R
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.util.FormatUtils

/**
 * 视频卡片 Presenter - 使用 ImageCardView
 * 优化：使用 Glide 加载封面，限制图片大小减少内存
 */
class VideoCardPresenter : Presenter() {

    private val CARD_WIDTH = 313
    private val CARD_HEIGHT = 176

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val cardView = ImageCardView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            setCardType(ImageCardView.CARD_TYPE_INFO_UNDER)
            // 始终显示标题等信息，避免未选中卡片看起来"空白"
            setInfoVisibility(ImageCardView.CARD_REGION_VISIBLE_ALWAYS)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val video = item as? Video ?: return
        val cardView = viewHolder.view as ImageCardView
        val context = cardView.context

        cardView.titleText = video.title
        cardView.contentText = buildContentText(video)

        // 加载封面图 - 使用 Glide，自动缓存和内存管理
        val coverUrl = video.getFullCoverUrl(ApiClient.BASE_URL)
        if (coverUrl.isNotEmpty()) {
            Glide.with(context)
                .load(coverUrl)
                .override(CARD_WIDTH, CARD_HEIGHT)
                // RGB_565：封面图不要求 alpha，内存占用减半（TV 内存有限）
                .format(DecodeFormat.PREFER_RGB_565)
                .centerCrop()
                .placeholder(R.drawable.ic_video_placeholder)
                .error(R.drawable.ic_video_placeholder)
                .into(cardView.mainImageView)
        } else {
            cardView.mainImageView.setImageResource(R.drawable.ic_video_placeholder)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        // 清除图片引用，帮助 GC
        cardView.mainImage = null
        cardView.badgeImage = null
    }

    private fun buildContentText(video: Video): String {
        val parts = mutableListOf<String>()
        // 文章/音频没有视频时长概念，标注类型便于区分
        if (video.isArticle) parts.add("文章")
        if (video.isAudio) parts.add("音频")
        if (video.viewCount > 0) {
            parts.add("${FormatUtils.formatCount(video.viewCount)}播放")
        }
        if (video.danmakuCount > 0) {
            parts.add("${video.danmakuCount}弹幕")
        }
        val duration = FormatUtils.formatDuration(video.duration)
        if (duration != "00:00") {
            parts.add(duration)
        }
        return parts.joinToString(" · ")
    }
}
