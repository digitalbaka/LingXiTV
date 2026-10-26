package com.lingxi.tv.ui.node

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.GatewayNode
import kotlinx.coroutines.launch

/**
 * 节点（线路）切换界面
 * 选择节点后，播放地址将使用该节点的 URL
 */
class NodeSelectActivity : AppCompatActivity() {

    private val repository = LingXiApp.get().repository
    private lateinit var rvNodes: RecyclerView
    private lateinit var tvCurrentNode: TextView
    private lateinit var tvNodeUrl: TextView
    private lateinit var tvQuota: TextView
    private lateinit var adapter: NodeAdapter

    private var nodes: List<GatewayNode> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_select)

        rvNodes = findViewById(R.id.rvNodes)
        tvCurrentNode = findViewById(R.id.tvCurrentNode)
        tvNodeUrl = findViewById(R.id.tvNodeUrl)
        tvQuota = findViewById(R.id.tvQuota)

        rvNodes.layoutManager = LinearLayoutManager(this)
        adapter = NodeAdapter { node -> selectNode(node) }
        rvNodes.adapter = adapter

        findViewById<Button>(R.id.btnReset).setOnClickListener { resetToDefault() }

        updateCurrentInfo()
        loadNodes()
    }

    private fun updateCurrentInfo() {
        val name = AuthManager.nodeName ?: "主站"
        val url = AuthManager.nodeUrl ?: "https://lingxi.xiguastudio.top"
        tvCurrentNode.text = "当前：$name"
        tvNodeUrl.text = "后端地址：$url"
    }

    private fun loadNodes() {
        lifecycleScope.launch {
            val result = repository.getGatewayNodes()
            result.onSuccess { info ->
                nodes = info.nodes.filter { it.enabled }
                adapter.setData(nodes, AuthManager.nodeId)

                info.quota?.let { q ->
                    val usedMb = q.used / (1024 * 1024)
                    val totalMb = q.limit / (1024 * 1024)
                    tvQuota.text = "流量：${usedMb} MB / ${totalMb} MB"
                }
            }.onFailure {
                Toast.makeText(this@NodeSelectActivity, "加载节点失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectNode(node: GatewayNode) {
        AuthManager.nodeId = node.id
        AuthManager.nodeUrl = node.url
        AuthManager.nodeName = node.name
        Toast.makeText(this, "已切换到：${node.name}", Toast.LENGTH_SHORT).show()
        updateCurrentInfo()
        adapter.setData(nodes, node.id)
    }

    private fun resetToDefault() {
        AuthManager.nodeId = 0
        AuthManager.nodeUrl = null
        AuthManager.nodeName = null
        Toast.makeText(this, "已重置为主站", Toast.LENGTH_SHORT).show()
        updateCurrentInfo()
        adapter.setData(nodes, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 内存优化：解除 RecyclerView 对 Adapter 的引用，避免 Activity 泄露
        rvNodes.adapter = null
        nodes = emptyList()
    }

    // ==================== Adapter ====================

    inner class NodeAdapter(private val onSelect: (GatewayNode) -> Unit) :
        RecyclerView.Adapter<NodeAdapter.ViewHolder>() {

        private var items: List<GatewayNode> = emptyList()
        private var currentId: Long = 0

        fun setData(list: List<GatewayNode>, current: Long) {
            items = list
            currentId = current
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvNodeName)
            val tvNote: TextView = view.findViewById(R.id.tvNodeNote)
            val tvCurrent: TextView = view.findViewById(R.id.tvNodeCurrent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_node, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val node = items[position]
            holder.tvName.text = node.name
            holder.tvNote.text = node.note
            holder.tvCurrent.visibility = if (node.id == currentId) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onSelect(node) }
        }

        override fun getItemCount(): Int = items.size
    }
}
