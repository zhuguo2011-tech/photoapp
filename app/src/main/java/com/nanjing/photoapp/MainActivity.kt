package com.nanjing.photoapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.nanjing.photoapp.api.ApiClient
import com.nanjing.photoapp.databinding.ActivityMainBinding
import com.nanjing.photoapp.model.Album
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response

// 首页：相册列表 + 顶部滚动公告
// 【新版改进】
// - 打开APP时只加载一次（以前 onCreate 和 onResume 各加载一次，白白多请求一遍）
// - 管理员登录后，带密码的相册也直接显示封面（不用输密码）；登录过期自动退出登录状态
// - 访客点公告可以看全文、一键复制（比如复制里面的微信号）；管理员改公告用多行输入框
// - 网络出错时提示具体原因（连不上/超时/地址不对……）
// - （最终版补充）公告清空后，管理员仍能看到“点这里添加公告”的提示栏（以前清空后就没有入口再添加了）
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlbumAdapter
    private var currentAnnouncement: String = ""
    private var announcementLoaded = false // 公告是否成功读取过（读取失败时不显示“没有公告”的提示）

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = AlbumAdapter(
            emptyList(),
            onClick = { album -> openAlbum(album) },
            onLongClick = { album -> maybeConfirmDeleteAlbum(album) }
        )
        binding.recyclerAlbums.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerAlbums.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            loadAlbums()
            loadAnnouncement()
        }
        binding.fabAddAlbum.setOnClickListener { showCreateAlbumDialog() }

        binding.textAnnouncement.isSelected = true // 让跑马灯滚动起来
        binding.textAnnouncement.setOnClickListener { onAnnouncementClick() }

        // 相册列表和公告在 onResume 里加载（打开APP、从别的页面返回时都会刷新一次）
    }

    override fun onResume() {
        super.onResume()
        updateLoginUi()
        loadAlbums()
        loadAnnouncement()
    }

    private fun toast(msg: String) {
        if (isFinishing) return
        Toast.makeText(this, msg, if (msg.length > 20) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    // 管理员登录过期（新版）：自动退出登录状态
    private fun onAuthExpired() {
        if (!SessionManager.isLoggedIn(this)) return
        SessionManager.logout(this)
        toast("登录已过期，请重新登录")
        updateLoginUi()
    }

    private fun <T> showError(response: Response<T>) {
        val err = ApiClient.parseError(response)
        if (response.code() == 401 && err.needLogin) onAuthExpired()
        toast(err.message)
    }

    private fun loadAnnouncement() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@MainActivity).getAnnouncement()
                if (response.isSuccessful) {
                    val text = response.body()?.announcement ?: ""
                    currentAnnouncement = text
                    announcementLoaded = true
                    renderAnnouncement()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { /* 公告加载失败不影响主功能 */ }
        }
    }

    // 显示公告栏（最终版补充：拆成单独的函数，登录/退出时也会调用）
    // 公告为空时：访客看不到公告栏；管理员会看到一条“点这里添加公告”的提示。
    // （以前公告一旦清空，公告栏就隐藏了，而修改公告的入口正是公告栏，导致再也没法添加新公告）
    private fun renderAnnouncement() {
        val tv = binding.textAnnouncement
        when {
            currentAnnouncement.isNotBlank() -> {
                // 内容没变就不重新设置文字，避免跑马灯从头开始滚
                if (tv.text.toString() != currentAnnouncement) tv.text = currentAnnouncement
                tv.visibility = android.view.View.VISIBLE
                tv.isSelected = true
            }
            announcementLoaded && SessionManager.isLoggedIn(this) -> {
                tv.text = "（当前没有公告，管理员点这里可以添加）"
                tv.visibility = android.view.View.VISIBLE
            }
            else -> tv.visibility = android.view.View.GONE
        }
    }

    private fun onAnnouncementClick() {
        if (!SessionManager.isLoggedIn(this)) {
            // 新版：访客点公告可以看全文，还能一键复制（以前访客点了没反应）
            AlertDialog.Builder(this)
                .setTitle("公告")
                .setMessage(currentAnnouncement)
                .setPositiveButton("知道了", null)
                .setNeutralButton("复制") { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("公告", currentAnnouncement))
                    toast("已复制")
                }
                .show()
            return
        }
        val input = EditText(this)
        input.setText(currentAnnouncement)
        input.minLines = 3
        input.filters = arrayOf(InputFilter.LengthFilter(500))
        AlertDialog.Builder(this)
            .setTitle("修改公告")
            .setMessage("留空则不显示公告，最多500字")
            .setView(input)
            .setPositiveButton("保存") { _, _ -> saveAnnouncement(input.text.toString().trim()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveAnnouncement(text: String) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@MainActivity)
                    .setAnnouncement(token, com.nanjing.photoapp.model.AnnouncementRequest(text))
                if (response.isSuccessful) {
                    toast("公告已更新")
                    loadAnnouncement()
                } else {
                    showError(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(ApiClient.networkErrorMessage(e))
            }
        }
    }

    private fun updateLoginUi() {
        val loggedIn = SessionManager.isLoggedIn(this)
        binding.fabAddAlbum.visibility = if (loggedIn) android.view.View.VISIBLE else android.view.View.GONE
        invalidateOptionsMenu()
        renderAnnouncement() // 最终版补充：公告为空时的“添加公告”提示跟着登录状态显示/隐藏
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val item = menu.findItem(R.id.action_login)
        item.title = if (SessionManager.isLoggedIn(this)) "退出登录" else "登录"
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_login) {
            if (SessionManager.isLoggedIn(this)) {
                SessionManager.logout(this)
                toast("已退出登录")
                updateLoginUi()
                loadAlbums() // 退出后带密码的相册重新显示为“需要密码”
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            return true
        }
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private var albumsLoadSeq = 0

    private fun loadAlbums() {
        val seq = ++albumsLoadSeq
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@MainActivity).getAlbums(
                    SessionManager.getAllViewTokensJson(this@MainActivity),
                    SessionManager.getAuthHeader(this@MainActivity)
                )
                if (seq != albumsLoadSeq) return@launch // 期间又刷新过，丢弃这次的结果
                if (response.headers()["X-Auth-Expired"] == "1") onAuthExpired()
                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()
                    adapter.updateData(list)
                    binding.textEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                } else {
                    showError(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seq == albumsLoadSeq) toast(ApiClient.networkErrorMessage(e))
            } finally {
                if (seq == albumsLoadSeq) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun openAlbum(album: Album) {
        val intent = Intent(this, AlbumDetailActivity::class.java)
        intent.putExtra("album_id", album.id)
        intent.putExtra("album_name", album.name)
        intent.putExtra("album_count", album.photo_count)
        startActivity(intent)
    }

    private fun showCreateAlbumDialog() {
        val input = EditText(this)
        input.hint = "相册名称"
        input.filters = arrayOf(InputFilter.LengthFilter(50))
        AlertDialog.Builder(this)
            .setTitle("新建相册")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    toast("相册名称不能为空")
                } else {
                    createAlbum(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createAlbum(name: String) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@MainActivity).createAlbum(token, com.nanjing.photoapp.model.AlbumCreateRequest(name))
                if (response.isSuccessful) {
                    toast(if (response.body()?.has_password == false) "相册创建成功" else "相册创建成功，默认密码 z394")
                    loadAlbums()
                } else {
                    showError(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(ApiClient.networkErrorMessage(e))
            }
        }
    }

    private fun maybeConfirmDeleteAlbum(album: Album) {
        if (!SessionManager.isLoggedIn(this)) return
        AlertDialog.Builder(this)
            .setTitle("删除相册")
            .setMessage("确定删除相册「${album.name}」吗？相册内所有照片都会被永久删除，不能恢复。")
            .setPositiveButton("下一步") { _, _ -> promptAdminPasswordForDelete(album) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun promptAdminPasswordForDelete(album: Album) {
        val input = EditText(this)
        input.hint = "管理员密码"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("请再次输入管理员密码确认删除")
            .setView(input)
            .setPositiveButton("确认删除") { _, _ ->
                val pwd = input.text.toString()
                if (pwd.isEmpty()) {
                    toast("未输入密码，删除已取消")
                } else {
                    deleteAlbum(album, pwd)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteAlbum(album: Album, password: String) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@MainActivity).deleteAlbum(token, com.nanjing.photoapp.model.DeleteAlbumRequest(album.id, password))
                if (response.isSuccessful) {
                    toast("已删除")
                    SessionManager.clearViewToken(this@MainActivity, album.id)
                    loadAlbums()
                } else {
                    showError(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(ApiClient.networkErrorMessage(e))
            }
        }
    }
}
