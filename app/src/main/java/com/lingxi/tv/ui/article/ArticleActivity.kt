package com.lingxi.tv.ui.article

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.model.displayName
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文章阅读页（kind=article）
 * 文章正文存于详情接口的 description 字段（Markdown 格式），
 * 与视频不同：无播放地址，不适合走播放器，单独阅读体验。
 */
class ArticleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ARTICLE_ID = "extra_article_id"
    }

    private val repository = LingXiApp.get().repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)

        val id = intent.getLongExtra(EXTRA_ARTICLE_ID, -1)
        if (id <= 0) {
            Toast.makeText(this, "文章ID无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadArticle(id)
    }

    private fun loadArticle(id: Long) {
        val tvTitle = findViewById<TextView>(R.id.tvArticleTitle)
        val tvMeta = findViewById<TextView>(R.id.tvArticleMeta)
        val tvIntro = findViewById<TextView>(R.id.tvArticleIntro)
        val layoutBody = findViewById<LinearLayout>(R.id.layoutArticleBody)
        val ivCover = findViewById<ImageView>(R.id.ivArticleCover)

        lifecycleScope.launch {
            repository.getVideoDetail(id).onSuccess { detail ->
                val v = detail.video
                if (v == null) {
                    tvIntro.text = "文章内容为空"
                    return@onSuccess
                }
                tvTitle.text = v.title

                val date = if (v.publishedAt > 0)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(v.publishedAt))
                else ""
                tvMeta.text = listOf(
                    v.up.displayName(),
                    date,
                    "${v.viewCount} 阅读"
                ).filter { it.isNotEmpty() }.joinToString("  ·  ")

                val coverUrl = v.getFullCoverUrl(ApiClient.BASE_URL)
                if (coverUrl.isNotEmpty()) {
                    ivCover.visibility = View.VISIBLE
                    Glide.with(this@ArticleActivity).load(coverUrl)
                        .format(DecodeFormat.PREFER_RGB_565)
                        .centerCrop()
                        .into(ivCover)
                }

                // 简介（有则显示，灰色小字）
                if (v.description.isNotBlank()) {
                    tvIntro.text = v.description
                } else {
                    tvIntro.visibility = View.GONE
                }

                // 正文：文章主体在 content 字段（Markdown），description 只是简介
                val markdown = v.content.ifBlank { v.description }
                renderBody(layoutBody, markdown)

                // 焦点落标题，遥控器上下键滚动正文
                tvTitle.post { tvTitle.requestFocus() }
            }.onFailure {
                tvIntro.text = "加载失败：${it.message}"
            }
        }
    }

    /** 把 Markdown 块渲染成 文本TextView + 图片ImageView 序列 */
    private fun renderBody(container: LinearLayout, markdown: String) {
        container.removeAllViews()
        if (markdown.isBlank()) {
            container.addView(makeTextView("（本文无正文）"))
            return
        }
        val blocks = MarkdownRenderer.parseBlocks(markdown, ApiClient.BASE_URL)
        for (block in blocks) {
            when (block) {
                is MarkdownRenderer.Block.Text ->
                    container.addView(makeTextView(block.text))
                is MarkdownRenderer.Block.Image ->
                    container.addView(makeImageView(block.url, block.alt))
            }
        }
    }

    private fun makeTextView(text: CharSequence): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setLineSpacing(0f, 1.5f)
            val p = (8 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
    }

    private fun makeImageView(url: String, alt: String): ImageView {
        val margin = (12 * resources.displayMetrics.density).toInt()
        val iv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = margin; bottomMargin = margin }
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = alt
        }
        Glide.with(this).load(url)
            .format(DecodeFormat.PREFER_RGB_565)
            .fitCenter()
            .into(iv)
        return iv
    }
}
