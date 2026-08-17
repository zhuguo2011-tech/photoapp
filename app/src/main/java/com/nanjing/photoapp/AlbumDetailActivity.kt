package com.nanjing.photoapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.Gson
import com.nanjing.photoapp.api.ApiClient
import com.nanjing.photoapp.databinding.ActivityAlbumDetailBinding
import com.nanjing.photoapp.model.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class AlbumDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlbumDetailBinding
    private lateinit var adapter: PhotoAdapter
    private var albumId: Int = -1
    private var albumName: String = ""
    private var currentPhotos: List<Photo> = emptyList()

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) uploadMultiple(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        albumId = intent.getIntExtra("album_id", -1)
        albumName = intent.getStringExtra("album_name") ?: "相册"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = albumName
        binding.toolbar.setNavigationOnClickListener { finish() }

        val loggedIn = SessionManager.isLoggedIn(this)

        adapter = PhotoAdapter(
            emptyList(),
            canManage = loggedIn,
            onPhotoClick = { position -> openViewer(position) },
            onDeleteClick = { photo -> confirmDeletePhoto(photo) },
            onLongPressEnterSelect = { photo -> enterSelectMode(photo.id) }
        )
        binding.recyclerPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerPhotos.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadPhotos() }
        binding.fabUpload.visibility = if (loggedIn) android.view.View.VISIBLE else android.view.View.GONE
        binding.fabSelectMode.visibility = if (loggedIn) android.view.View.VISIBLE else android.view.View.GONE

        binding.fabUpload.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        binding.fabSelectMode.setOnClickListener { enterSelectMode(null) }
        binding.btnCancelSelect.setOnClickListener { exitSelectMode() }
        binding.btnSelectAll.setOnClickListener { adapter.selectAll() }
        binding.btnBatchDelete.setOnClickListener { confirmBatchDelete() }

        loadPhotos()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (SessionManager.isLoggedIn(this)) {
            menuInflater.inflate(R.menu.album_detail_menu, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete_album -> { confirmDeleteAlbum(); return true }
            R.id.action_set_password -> { showSetPasswordDialog(); return true }
        }
        return super.onOptionsItemSelected(item)
    }

    // ================= 加载照片（自动处理密码保护） =================
    private fun loadPhotos() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val viewToken = SessionManager.getViewToken(this@AlbumDetailActivity, albumId)
                val response = ApiClient.service(this@AlbumDetailActivity).getPhotos(albumId, viewToken)
                if (response.isSuccessful) {
                    currentPhotos = response.body() ?: emptyList()
                    adapter.updateData(currentPhotos)
                    binding.textEmpty.visibility = if (currentPhotos.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                } else if (response.code() == 401) {
                    // 需要密码，或者密码令牌过期了
                    SessionManager.clearViewToken(this@AlbumDetailActivity, albumId)
                    promptAlbumPassword()
                } else {
                    Toast.makeText(this@AlbumDetailActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AlbumDetailActivity, "网络请求失败，请检查服务器地址和网络连接", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun promptAlbumPassword() {
        val input = EditText(this)
        input.hint = "请输入相册密码"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("「$albumName」需要密码")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("确认") { _, _ ->
                val pwd = input.text.toString()
                if (pwd.isEmpty()) {
                    Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    verifyAlbumPassword(pwd)
                }
            }
            .setNegativeButton("返回") { _, _ -> finish() }
            .show()
    }

    private fun verifyAlbumPassword(password: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@AlbumDetailActivity)
                    .verifyAlbumPassword(VerifyPasswordRequest(albumId, password))
                if (response.isSuccessful && response.body()?.view_token != null) {
                    SessionManager.saveViewToken(this@AlbumDetailActivity, albumId, response.body()!!.view_token!!)
                    loadPhotos()
                } else {
                    Toast.makeText(this@AlbumDetailActivity, response.body()?.error ?: "密码错误", Toast.LENGTH_SHORT).show()
                    promptAlbumPassword()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AlbumDetailActivity, "网络请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= 上传（支持多选图片+视频） =================
    private fun uploadMultiple(uris: List<Uri>) {
        val token = SessionManager.getAuthHeader(this) ?: return
        binding.progressUpload.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            var success = 0
            var fail = 0
            for ((i, uri) in uris.withIndex()) {
                Toast.makeText(this@AlbumDetailActivity, "上传中 ${i + 1}/${uris.size}", Toast.LENGTH_SHORT).show()
                try {
                    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                    val ext = if (mimeType.startsWith("video")) "mp4" else "jpg"
                    val inputStream = contentResolver.openInputStream(uri)
                    val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}_$i.$ext")
                    inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }

                    val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                    val photoPart = MultipartBody.Part.createFormData("photo", tempFile.name, requestFile)
                    val albumIdBody = albumId.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                    val response = ApiClient.service(this@AlbumDetailActivity).uploadPhoto(token, albumIdBody, photoPart)
                    tempFile.delete()

                    if (response.isSuccessful && response.body()?.error == null) success++
                    else fail++
                } catch (e: Exception) {
                    fail++
                }
            }
            binding.progressUpload.visibility = android.view.View.GONE
            Toast.makeText(
                this@AlbumDetailActivity,
                "上传完成：成功${success}个" + if (fail > 0) "，失败${fail}个" else "",
                Toast.LENGTH_LONG
            ).show()
            loadPhotos()
        }
    }

    // ================= 单个删除（保留原有方式不变） =================
    private fun confirmDeletePhoto(photo: Photo) {
        AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("确定删除这项内容吗？删除后不能恢复。")
            .setPositiveButton("删除") { _, _ -> deletePhoto(photo) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deletePhoto(photo: Photo) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@AlbumDetailActivity).deletePhoto(token, IdRequest(photo.id))
                if (response.isSuccessful) {
                    Toast.makeText(this@AlbumDetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                    loadPhotos()
                } else {
                    Toast.makeText(this@AlbumDetailActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AlbumDetailActivity, "网络请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= 多选模式与批量删除（新增，不影响上面单个删除） =================
    private fun enterSelectMode(preSelectId: Int?) {
        adapter.setSelectionMode(true)
        if (preSelectId != null) adapter.toggleSelection(preSelectId)
        binding.selectToolbar.visibility = android.view.View.VISIBLE
        binding.fabUpload.visibility = android.view.View.GONE
        binding.fabSelectMode.visibility = android.view.View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun exitSelectMode() {
        adapter.setSelectionMode(false)
        binding.selectToolbar.visibility = android.view.View.GONE
        val loggedIn = SessionManager.isLoggedIn(this)
        binding.fabUpload.visibility = if (loggedIn) android.view.View.VISIBLE else android.view.View.GONE
        binding.fabSelectMode.visibility = if (loggedIn) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun confirmBatchDelete() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) {
            Toast.makeText(this, "还没选中任何内容", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("确定删除选中的 ${ids.size} 项吗？")
            .setMessage("删除后不能恢复")
            .setPositiveButton("删除") { _, _ -> batchDelete(ids) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun batchDelete(ids: Set<Int>) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@AlbumDetailActivity)
                    .deletePhotosBatch(token, BatchDeleteRequest(ids.toList()))
                if (response.isSuccessful) {
                    Toast.makeText(this@AlbumDetailActivity, "已删除${response.body()?.deleted ?: 0}项", Toast.LENGTH_SHORT).show()
                    exitSelectMode()
                    loadPhotos()
                } else {
                    Toast.makeText(this@AlbumDetailActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AlbumDetailActivity, "网络请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= 删除整个相册 =================
    private fun confirmDeleteAlbum() {
        AlertDialog.Builder(this)
            .setTitle("删除相册")
            .setMessage("确定删除相册「$albumName」吗？相册内所有内容都会被永久删除，不能恢复。")
            .setPositiveButton("下一步") { _, _ -> promptAdminPasswordForDelete() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun promptAdminPasswordForDelete() {
        val input = EditText(this)
        input.hint = "管理员密码"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("请再次输入管理员密码确认删除")
            .setView(input)
            .setPositiveButton("确认删除") { _, _ ->
                val pwd = input.text.toString()
                if (pwd.isEmpty()) {
                    Toast.makeText(this, "未输入密码，删除已取消", Toast.LENGTH_SHORT).show()
                } else {
                    deleteAlbum(pwd)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteAlbum(password: String) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@AlbumDetailActivity).deleteAlbum(token, DeleteAlbumRequest(albumId, password))
                if (response.isSuccessful) {
                    Toast.makeText(this@AlbumDetailActivity, "已删除相册", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AlbumDetailActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AlbumDetailActivity, "网络请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= 相册密码管理（管理员） =================
    private fun showSetPasswordDialog() {
        val input = EditText(this)
        input.hint = "新密码（留空 = 取消密码保护）"
        AlertDialog.Builder(this)
            .setTitle("相册密码设置")
            .setMessage("默认密码是 z394，你可以在这里改成别的，或者留空取消密码保护，让任何人都能直接看。")
            .setView(input)
            .setPositiveButton("保存") { _, _ -> setAlbumPassword(input.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setAlbumPassword(password: String) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@AlbumDetailActivity)
                    .setAlbumPassword(token, SetPasswordRequest(albumId, password))
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@AlbumDetailActivity,
                        if (password.isEmpty()) "已取消密码保护" else "密码已更新",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@AlbumDetailActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AlbumDetailActivity, "网络请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= 打开大图/视频查看器（支持左右滑动切换） =================
    private fun openViewer(position: Int) {
        val json = Gson().toJson(currentPhotos)
        val intent = Intent(this, PhotoViewerActivity::class.java)
        intent.putExtra("photos_json", json)
        intent.putExtra("start_index", position)
        startActivity(intent)
    }
}
