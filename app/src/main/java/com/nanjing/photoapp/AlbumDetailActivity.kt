package com.nanjing.photoapp

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.nanjing.photoapp.api.ApiClient
import com.nanjing.photoapp.databinding.ActivityAlbumDetailBinding
import com.nanjing.photoapp.model.IdRequest
import com.nanjing.photoapp.model.Photo
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

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadPhoto(uri)
        }
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
            canDelete = loggedIn,
            onPhotoClick = { photo -> showFullImage(photo) },
            onDeleteClick = { photo -> confirmDeletePhoto(photo) }
        )
        binding.recyclerPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerPhotos.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadPhotos() }
        binding.fabUpload.visibility = if (loggedIn) android.view.View.VISIBLE else android.view.View.GONE
        binding.fabUpload.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        loadPhotos()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (SessionManager.isLoggedIn(this)) {
            menuInflater.inflate(R.menu.album_detail_menu, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_delete_album) {
            confirmDeleteAlbum()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadPhotos() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getPhotos(albumId)
                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()
                    adapter.updateData(list)
                    binding.textEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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

    private fun uploadPhoto(uri: Uri) {
        val token = SessionManager.getAuthHeader(this) ?: return
        binding.progressUpload.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            try {
                // 把选中的图片先拷贝到应用私有的缓存目录，再上传
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val photoPart = MultipartBody.Part.createFormData("photo", tempFile.name, requestFile)
                val albumIdBody = albumId.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                val response = ApiClient.service.uploadPhoto(token, albumIdBody, photoPart)
                tempFile.delete()

                if (response.isSuccessful && response.body()?.error == null) {
                    Toast.makeText(this@AlbumDetailActivity, "上传成功", Toast.LENGTH_SHORT).show()
                    loadPhotos()
                } else {
                    val msg = response.body()?.error ?: ApiClient.errorMessage(response)
                    Toast.makeText(this@AlbumDetailActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AlbumDetailActivity, "上传失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressUpload.visibility = android.view.View.GONE
            }
        }
    }

    private fun confirmDeletePhoto(photo: Photo) {
        AlertDialog.Builder(this)
            .setTitle("删除照片")
            .setMessage("确定删除这张照片吗？删除后不能恢复。")
            .setPositiveButton("删除") { _, _ -> deletePhoto(photo) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deletePhoto(photo: Photo) {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service.deletePhoto(token, IdRequest(photo.id))
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

    private fun confirmDeleteAlbum() {
        AlertDialog.Builder(this)
            .setTitle("删除相册")
            .setMessage("确定删除相册「$albumName」吗？相册内所有照片都会被永久删除，不能恢复。")
            .setPositiveButton("删除") { _, _ -> deleteAlbum() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteAlbum() {
        val token = SessionManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.service.deleteAlbum(token, IdRequest(albumId))
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

    private fun showFullImage(photo: Photo) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val imageView = ImageView(this)
        imageView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setOnClickListener { dialog.dismiss() }
        Glide.with(this).load(photo.url).into(imageView)
        dialog.setContentView(imageView)
        dialog.show()
    }
}
