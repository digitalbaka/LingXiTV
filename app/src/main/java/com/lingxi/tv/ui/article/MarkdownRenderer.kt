package com.lingxi.tv.ui.article

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * 极简 Markdown 渲染器（无第三方依赖，离线打包环境无法新增库）。
 * 支持：#~###### 标题、**粗体**、*斜体*、`行内代码`、```代码块```、
 * - 无序列表、1. 有序列表、> 引用、--- 分割线、[链接](url)、![图片](url) 占位。
 * 输出 SpannableStringBuilder 直接喂给 TextView。
 */
object MarkdownRenderer {

    /** 解析结果块：文本段 或 图片 */
    sealed class Block {
        data class Text(val text: CharSequence) : Block()
        data class Image(val url: String, val alt: String) : Block()
    }

    private val COLOR_CODE_BG = Color.parseColor("#33FFFFFF")
    private val COLOR_QUOTE = Color.parseColor("#FF9E9E9E")

    /** 纯文本渲染（不含图片块，图片以占位文字显示） */
    fun render(markdown: String): CharSequence = parseBlocks(markdown)
        .filterIsInstance<Block.Text>()
        .fold(SpannableStringBuilder()) { acc, b -> acc.append(b.text) }

    /**
     * 解析为块序列：图片独占一行时抽出为 Image 块，其余为 Text 块。
     * @param imageBaseUrl 相对图片地址（/static/...）的拼接前缀
     */
    fun parseBlocks(markdown: String, imageBaseUrl: String = ""): List<Block> {
        val blocks = mutableListOf<Block>()
        var textBuf = SpannableStringBuilder()
        var inCodeBlock = false

        fun flushText() {
            if (textBuf.isNotBlank()) {
                blocks.add(Block.Text(textBuf))
            }
            textBuf = SpannableStringBuilder()
        }

        val lines = markdown.replace("\r\n", "\n").split("\n")
        for (rawLine in lines) {
            val line = rawLine.trimEnd()

            if (line.trimStart().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                continue
            }
            if (!inCodeBlock) {
                // 独占一行的图片：![alt](url)
                val img = Regex("^\\s*!\\[([^]]*)]\\(([^)\\s]+)[^)]*\\)\\s*$").find(line)
                if (img != null) {
                    flushText()
                    var url = img.groupValues[2]
                    if (url.startsWith("/")) url = imageBaseUrl.trimEnd('/') + url
                    blocks.add(Block.Image(url, img.groupValues[1]))
                    continue
                }
            }
            processLine(textBuf, line, inCodeBlock)
        }
        flushText()
        return blocks
    }

    /** 处理单行（标题/引用/列表/分割线/普通段落），追加到 out */
    private fun processLine(out: SpannableStringBuilder, line: String, inCodeBlock: Boolean) {
        if (inCodeBlock) {
            appendCode(out, line.ifEmpty { " " })
            out.append('\n')
            return
        }
        when {
            // 分割线
            line.matches(Regex("^\\s*([-*_])\\1{2,}\\s*$")) -> {
                out.append("────────────────")
                out.append('\n')
            }
            // 标题
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val text = line.drop(level).trim()
                val start = out.length
                appendInline(out, text)
                val size = when (level) {
                    1 -> 1.6f; 2 -> 1.45f; 3 -> 1.3f; else -> 1.15f
                }
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(size), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.append('\n')
            }
            // 引用
            line.trimStart().startsWith(">") -> {
                val text = line.trimStart().removePrefix(">").trim()
                val start = out.length
                appendInline(out, text)
                out.setSpan(QuoteSpan(COLOR_QUOTE, 12, 24), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.append('\n')
            }
            // 无序列表
            line.trimStart().matches(Regex("^[-*+]\\s+.*")) -> {
                out.append("  • ")
                appendInline(out, line.trimStart().drop(2))
                out.append('\n')
            }
            // 有序列表
            line.trimStart().matches(Regex("^\\d+[.、]\\s+.*")) -> {
                val t = line.trimStart()
                val idx = t.indexOfFirst { it == '.' || it == '、' }
                out.append("  " + t.substring(0, idx + 1) + " ")
                appendInline(out, t.substring(idx + 1).trimStart())
                out.append('\n')
            }
            // 空行
            line.isEmpty() -> out.append('\n')
            // 普通段落
            else -> {
                appendInline(out, line)
                out.append('\n')
            }
        }
    }

    /** 行内元素：**粗体**、*斜体*、`代码`、[链接](url)、![图片](url) */
    private fun appendInline(out: SpannableStringBuilder, text: String) {
        var s = text
        // 图片：![alt](url) → [图片： alt]（TV 端不内联加载，避免阅读页内存膨胀）
        s = s.replace(Regex("!\\[([^]]*)]\\([^)]*\\)")) { m ->
            val alt = m.groupValues[1].ifEmpty { "图片" }
            "🖼 $alt"
        }
        // 链接：[text](url) → text（着色，TV 无法点击）
        s = s.replace(Regex("\\[([^]]+)]\\(([^)]*)\\)")) { m -> m.groupValues[1] }

        // 按 token 顺序处理：代码、粗体、斜体
        val pattern = Regex("(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(\\*[^*]+\\*)")
        var last = 0
        for (m in pattern.findAll(s)) {
            if (m.range.first > last) out.append(s.substring(last, m.range.first))
            val token = m.value
            val start = out.length
            when {
                token.startsWith("**") -> {
                    out.append(token.removeSurrounding("**"))
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                token.startsWith("`") -> {
                    out.append(token.removeSurrounding("`"))
                    out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(COLOR_CODE_BG), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                token.startsWith("*") -> {
                    out.append(token.removeSurrounding("*"))
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            last = m.range.last + 1
        }
        if (last < s.length) out.append(s.substring(last))
    }

    private fun appendCode(out: SpannableStringBuilder, code: String) {
        val start = out.length
        out.append(code)
        out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(BackgroundColorSpan(COLOR_CODE_BG), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(RelativeSizeSpan(0.9f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
