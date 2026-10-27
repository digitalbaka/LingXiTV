package com.lingxi.tv.ui.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.model.Conversation
import kotlinx.coroutines.launch

/**
 * 消息中心 - 会话列表
 */
class MessageActivity : AppCompatActivity() {

    private val repository = LingXiApp.get().repository
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private val adapter = ConversationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        rv = findViewById(R.id.rvMessages)
        tvEmpty = findViewById(R.id.tvEmpty)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            repository.getConversations().onSuccess { page ->
                adapter.setData(page.items)
                tvEmpty.visibility = if (page.items.isEmpty()) View.VISIBLE else View.GONE
            }.onFailure {
                Toast.makeText(this@MessageActivity, "加载失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rv.adapter = null
    }

    class ConversationAdapter : RecyclerView.Adapter<ConversationAdapter.VH>() {
        private var items: List<Conversation> = emptyList()

        fun setData(list: List<Conversation>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvItemTitle)
            val tvSub: TextView = view.findViewById(R.id.tvItemSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_text_row, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            val name = c.nickname.ifEmpty { c.username }
            holder.tvTitle.text = if (c.unread > 0) "$name（${c.unread} 条未读）" else name
            holder.tvSub.text = c.lastMessage
        }

        override fun getItemCount(): Int = items.size
    }
}
