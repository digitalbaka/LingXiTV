package com.lingxi.tv.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.lingxi.tv.R
import com.lingxi.tv.ui.search.SearchActivity

/**
 * 主 Activity - 承载 BrowseFragment
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainBrowseFragment())
                .commit()
        }
    }

    fun openSearch() {
        startActivity(Intent(this, SearchActivity::class.java))
    }
}
