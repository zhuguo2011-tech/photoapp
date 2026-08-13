package com.nanjing.photoapp

import android.content.Intent
import android.os.Bundle
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
import com.nanjing.photoapp.model.IdRequest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlbumAdapter

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

        binding.swipeRefresh.setOnRefreshListener { loadAlbums() }
        binding.fabAddAlbum.setOnClickListener { showCreateAlbumDialog() }

        loadAlbums()
    }

    override fun onResume() {
        super.onResume()
        updateLoginUi()
        loadAlbums()
    }

    private fun updateLoginUi() {
        val loggedIn = SessionManager.isLoggedIn(this)
        binding.fabAddAlbum.visibility = if (loggedIn) android.view.View.VISIBLE else android.view.View.GONE
        invalidateOptionsMenu()
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
                Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
                updateLoginUi()
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadAlbums() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getAlbums()
                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()
                    adapter.updateData(list)
                    binding.textEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                } else {
                    Toast.makeText(this@MainActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "网络请求失败，请检查服务器地址和网络连接", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun openAlbum(album: Album) {
        val intent = Intent(this, AlbumDetailActivity::class.java)
        intent.putExtra("album_id", album.id)
        intent.putExtra("album_name", album.name)
        startActivity(intent)
    }

    private fun showCreateAlbumDialog() {
        val input = EditText(this)
        input.hint = "相册名称"
        AlertDialog.Builder(this)
            .setTitle("新建相册")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "相册名称不能为空", Toast.LENGTH_SHORT).show()
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
                val response = ApiClient.service.createAlbum(token, com.nanjing.photoapp.model.AlbumCreateRequest(name))
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "相册创建成功", Toast.LENGTH_SHORT).show()
                    loadAlbums()
                } else {
                    Toast.makeText(this@MainActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "网络请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun maybeConfirmDeleteAlbum(album: Album) {
        if (!SessionManager.isLoggedIn(this)) return
        AlertDialog.Builder(this)
            .setTitle("删除相册")
            .setMessage("确定删除相册「${album.name}」吗？相册内所有照片都会被永久删除，不能恢复。")
            .setPositiveButton("删除") { _, _ -> deleteAlbum(album) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteAlbum(album: Album) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service.deleteAlbum(token, IdRequest(album.id))
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                    loadAlbums()
                } else {
                    Toast.makeText(this@MainActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "网络请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
