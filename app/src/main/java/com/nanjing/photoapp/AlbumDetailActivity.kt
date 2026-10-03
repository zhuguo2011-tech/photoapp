package com.nanjing.photoapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nanjing.photoapp.api.ApiClient
import com.nanjing.photoapp.api.ProgressRequestBody
import com.nanjing.photoapp.databinding.ActivityAlbumDetailBinding
import com.nanjing.photoapp.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.FileInputStream

// 相册详情页：照片格子列表、上传、删除、多选、相册密码、删除相册
//
// 【新版改进摘要】
// - 上传：顶部显示进度（第几个、百分比、服务器处理中），可以取消；失败的会列出原因；
//   传完的直接插到最前面，不再整页刷新；上传时屏幕不自动熄灭；HEIC照片自动转JPG；超过大小上限的直接提示
// - 正在上传时按返回键会先确认（以前直接退出，上传被中断）；多选模式下按返回键先退出多选
// - 翻页改成按“上一页最后一张”接着加载（翻页途中有人上传/删除也不会重复或漏掉）
// - 删除照片后只移除对应的格子，列表停在原来的位置
// - 管理员登录后看带密码的相册不用再输相册密码；登录过期会自动退出登录状态
// - 输入相册密码时网络出错，会重新弹出输入框（以前框消失后页面一片空白）
class AlbumDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlbumDetailBinding
    private lateinit var adapter: PhotoAdapter
    private var albumId: Int = -1
    private var albumName: String = ""
    private val currentPhotos = ArrayList<Photo>()
    private val PAGE_SIZE = 100
    private var loadingPhotos = false
    private var hasMorePhotos = true
    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var dragSelectListener: DragSelectTouchListener

    // ===== 新版增加的状态 =====
    private var albumTotal = 0          // 相册一共多少项（服务器告诉的，用于标题栏“共N项”和大图页计数）
    private var loadGeneration = 0      // 每次“从头加载”+1，旧请求回来发现编号对不上就丢弃
    private var loadJob: Job? = null
    private var maxUploadMb = 20        // 上传大小上限（加载照片列表时服务器会告诉实际值）
    private var maxVideoMb = 200
    private var uploading = false
    private var uploadJob: Job? = null
    private var storeVersionSeen = -1   // 打开大图页那一刻 PhotoStore 的版本号
    private var passwordDialog: AlertDialog? = null
    private val pendingUploaded = ArrayList<Photo>() // 多选期间传完的照片，退出多选后再插到最前面

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) uploadMultiple(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        albumId = intent.getIntExtra("album_id", -1)
        albumName = intent.getStringExtra("album_name") ?: "相册"
        albumTotal = intent.getIntExtra("album_count", 0)
        if (albumId <= 0) { finish(); return }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = albumName
        binding.toolbar.setNavigationOnClickListener { handleBack() }

        // 新版：返回键统一走 handleBack（多选模式先退出多选；正在上传先确认）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        val loggedIn = SessionManager.isLoggedIn(this)

        adapter = PhotoAdapter(
            emptyList(),
            canManage = loggedIn,
            onPhotoClick = { position -> openViewer(position) },
            onDeleteClick = { photo -> confirmDeletePhoto(photo) },
            onLongPressEnterSelect = { position -> enterSelectModeAndDrag(position) }
        )
        adapter.onSelectionChanged = { updateSelectToolbar() }
        gridLayoutManager = GridLayoutManager(this, 3)
        binding.recyclerPhotos.layoutManager = gridLayoutManager
        binding.recyclerPhotos.adapter = adapter

        // 拖动多选：多选模式下按住滑过多个格子连续选中，滑到边缘自动滚动
        dragSelectListener = DragSelectTouchListener { start, end, selecting ->
            adapter.setRangeSelected(start, end, selecting)
        }
        dragSelectListener.isPositionSelected = { pos -> adapter.isPositionSelected(pos) }
        dragSelectListener.onDragStart = { adapter.beginDrag() }
        dragSelectListener.onDragEnd = { adapter.endDrag() }
        binding.recyclerPhotos.addOnItemTouchListener(dragSelectListener)

        // 滑到接近底部自动加载下一批
        binding.recyclerPhotos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                maybeLoadMore()
            }
        })

        binding.swipeRefresh.setOnRefreshListener { loadPhotos(reset = true) }

        binding.fabUpload.setOnClickListener {
            if (uploading) {
                toast("正在上传，请等这一批传完")
                return@setOnClickListener
            }
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        binding.fabSelectMode.setOnClickListener { enterSelectMode(null) }
        binding.btnCancelSelect.setOnClickListener { exitSelectMode() }
        binding.btnSelectAll.setOnClickListener { adapter.selectAll() }
        binding.btnBatchDelete.setOnClickListener { confirmBatchDelete() }
        binding.btnCancelUpload.setOnClickListener { confirmCancelUpload() }

        updateManageUi()
        updateSubtitle()
        loadPhotos(reset = true)
    }

    override fun onResume() {
        super.onResume()
        syncFromViewer()
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

    // 返回：多选模式下先退出多选；正在上传时先确认（退出会中断上传）
    private fun handleBack() {
        when {
            adapter.selectionMode -> exitSelectMode()
            uploading -> AlertDialog.Builder(this)
                .setTitle("正在上传")
                .setMessage("还有照片/视频没传完，现在退出会中断上传。确定退出吗？")
                .setPositiveButton("退出") { _, _ ->
                    uploadJob?.cancel()
                    finish()
                }
                .setNegativeButton("继续上传", null)
                .show()
            else -> finish()
        }
    }

    // 登录状态相关的界面（上传按钮、多选按钮、删除按钮、右上角菜单）
    private fun updateManageUi() {
        val loggedIn = SessionManager.isLoggedIn(this)
        val showFabs = loggedIn && !adapter.selectionMode
        binding.fabUpload.visibility = if (showFabs) View.VISIBLE else View.GONE
        binding.fabSelectMode.visibility = if (showFabs && !uploading) View.VISIBLE else View.GONE
        adapter.setCanManage(loggedIn)
        invalidateOptionsMenu()
    }

    private fun updateSubtitle() {
        supportActionBar?.subtitle = if (albumTotal > 0) "共 $albumTotal 项" else null
    }

    private fun updateEmptyTip() {
        binding.textEmpty.visibility =
            if (currentPhotos.isEmpty() && !hasMorePhotos && !loadingPhotos) View.VISIBLE else View.GONE
    }

    // 管理员登录过期（新版）：自动退出登录状态
    private fun onAuthExpired() {
        if (!SessionManager.isLoggedIn(this)) return
        SessionManager.logout(this)
        toast("登录已过期，请重新登录")
        if (adapter.selectionMode) exitSelectMode()
        updateManageUi()
    }

    // 统一处理失败的响应：提示原因；登录失效的自动退出登录
    private fun <T> showError(response: Response<T>) {
        val err = ApiClient.parseError(response)
        if (response.code() == 401 && err.needLogin) onAuthExpired()
        toast(err.message)
    }

    private fun toast(msg: String) {
        if (isFinishing) return
        Toast.makeText(this, msg, if (msg.length > 20) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    // ================= 加载照片（分页 + 自动处理密码保护） =================
    private fun loadPhotos(reset: Boolean) {
        if (reset) {
            loadJob?.cancel()
            loadGeneration++
            loadingPhotos = false
            hasMorePhotos = true
            binding.swipeRefresh.isRefreshing = true
        }
        if (loadingPhotos || !hasMorePhotos) return
        loadingPhotos = true
        val gen = loadGeneration
        // 新版：用“上一页最后一张的id”接着往后翻；同时带上offset，老服务器不认识before_id时照样能用
        val beforeId = if (reset || currentPhotos.isEmpty()) null else currentPhotos.last().id
        val offset = if (reset) 0 else currentPhotos.size

        loadJob = lifecycleScope.launch {
            try {
                val viewToken = SessionManager.getViewToken(this@AlbumDetailActivity, albumId)
                val response = ApiClient.service(this@AlbumDetailActivity).getPhotos(
                    albumId, viewToken, offset, PAGE_SIZE, beforeId,
                    SessionManager.getAuthHeader(this@AlbumDetailActivity)
                )
                if (gen != loadGeneration) return@launch // 期间又重新加载过了，丢弃这次的结果
                if (response.isSuccessful) {
                    val page = response.body() ?: emptyList()
                    readServerHints(response)
                    if (reset) currentPhotos.clear()
                    val known = HashSet<Int>()
                    currentPhotos.forEach { known.add(it.id) }
                    val fresh = page.filter { known.add(it.id) }
                    val start = currentPhotos.size
                    currentPhotos.addAll(fresh)
                    hasMorePhotos = page.size >= PAGE_SIZE
                    if (!hasMorePhotos) albumTotal = currentPhotos.size
                    if (reset) adapter.updateData(ArrayList(currentPhotos)) else adapter.appendData(currentPhotos.subList(start, currentPhotos.size).toList())
                    updateSubtitle()
                    // 第一页如果不够填满屏幕，接着加载，免得滑不动触发不了“加载更多”
                    binding.recyclerPhotos.post { maybeLoadMore() }
                } else {
                    val err = ApiClient.parseError(response)
                    if (response.code() == 401 && err.needPassword) {
                        if (err.authExpired) onAuthExpired()
                        SessionManager.clearViewToken(this@AlbumDetailActivity, albumId)
                        promptAlbumPassword(if (reset) null else "密码已过期或已被修改，请重新输入")
                    } else {
                        if (response.code() == 401 && err.needLogin) onAuthExpired()
                        toast(err.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == loadGeneration) toast(ApiClient.networkErrorMessage(e))
            } finally {
                if (gen == loadGeneration) {
                    loadingPhotos = false
                    binding.swipeRefresh.isRefreshing = false
                    updateEmptyTip()
                }
            }
        }
    }

    // 读取服务器在响应头里告诉我们的信息：相册总数、上传大小上限（新版服务器才有）
    private fun <T> readServerHints(response: Response<T>) {
        val headers = response.headers()
        headers["X-Total-Count"]?.toIntOrNull()?.let { albumTotal = it }
        headers["X-Max-Upload-MB"]?.toIntOrNull()?.let { if (it > 0) maxUploadMb = it }
        headers["X-Max-Video-MB"]?.toIntOrNull()?.let { if (it > 0) maxVideoMb = it }
    }

    private fun maybeLoadMore() {
        if (loadingPhotos || !hasMorePhotos) return
        val visible = gridLayoutManager.childCount
        val total = gridLayoutManager.itemCount
        val firstVisible = gridLayoutManager.findFirstVisibleItemPosition()
        if (firstVisible + visible >= total - 12) {
            loadPhotos(reset = false)
        }
    }

    private fun promptAlbumPassword(hint: String? = null) {
        if (isFinishing || passwordDialog?.isShowing == true) return
        val input = EditText(this)
        input.hint = "请输入相册密码"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        val builder = AlertDialog.Builder(this)
            .setTitle("「$albumName」需要密码")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("确认") { _, _ ->
                val pwd = input.text.toString()
                if (pwd.isEmpty()) {
                    // 原来是提示后直接退出相册，新版改成重新弹出输入框
                    toast("请输入密码")
                    promptAlbumPassword(hint)
                } else {
                    verifyAlbumPassword(pwd)
                }
            }
            .setNegativeButton("返回") { _, _ -> finish() }
        if (hint != null) builder.setMessage(hint)
        passwordDialog = builder.show()
    }

    private fun verifyAlbumPassword(password: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@AlbumDetailActivity)
                    .verifyAlbumPassword(VerifyPasswordRequest(albumId, password))
                val body = response.body()
                if (response.isSuccessful && body != null && (body.view_token != null || body.no_password_needed == true)) {
                    body.view_token?.let { SessionManager.saveViewToken(this@AlbumDetailActivity, albumId, it) }
                    loadPhotos(reset = true)
                } else {
                    val msg = if (response.isSuccessful) (body?.error ?: "密码错误") else ApiClient.parseError(response).message
                    toast(msg)
                    promptAlbumPassword()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 新版：网络出错时重新弹出密码框，可以再试一次（以前框消失后页面一片空白）
                toast(ApiClient.networkErrorMessage(e))
                promptAlbumPassword()
            }
        }
    }

    // ================= 上传（支持多选图片+视频） =================
    private data class PickedFile(val name: String, val size: Long, val mime: String)

    // 读取选中文件的名字、大小、类型
    private fun readPickedFile(uri: Uri, index: Int): PickedFile {
        var name: String? = null
        var size = -1L
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (e: Exception) {
            // 读不到就用下面的默认值
        }
        if (size <= 0) {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length }
            } catch (e: Exception) {
                size = -1L
            }
        }
        val mime = contentResolver.getType(uri) ?: guessMime(name)
        val finalName = name?.takeIf { it.isNotBlank() } ?: ("upload_${System.currentTimeMillis()}_$index." + extForMime(mime))
        return PickedFile(finalName, size, mime)
    }

    private fun guessMime(name: String?): String {
        return when (name?.substringAfterLast('.', "")?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }

    private fun extForMime(mime: String): String = when {
        mime == "image/png" -> "png"
        mime == "image/gif" -> "gif"
        mime == "image/webp" -> "webp"
        mime.startsWith("image/hei") -> "heic"
        mime.startsWith("image/") -> "jpg"
        mime == "video/quicktime" -> "mov"
        mime.startsWith("video/") -> "mp4"
        else -> "bin"
    }

    private fun isHeic(f: PickedFile): Boolean {
        val n = f.name.lowercase()
        return f.mime.contains("heic") || f.mime.contains("heif") || n.endsWith(".heic") || n.endsWith(".heif")
    }

    private fun setUploadStatus(text: String, percent: Int) {
        binding.textUploadStatus.text = text
        binding.progressUploadBar.progress = percent.coerceIn(0, 100)
    }

    private fun uploadMultiple(uris: List<Uri>) {
        if (uploading) {
            toast("正在上传，请等这一批传完")
            return
        }
        val token = SessionManager.getAuthHeader(this)
        if (token == null) {
            toast("请先登录")
            return
        }
        uploading = true
        // 上传时不让屏幕自动熄灭（熄屏后有的手机会断开网络，上传就失败了）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.uploadPanel.visibility = View.VISIBLE
        setUploadStatus("准备上传…", 0)
        updateManageUi()

        uploadJob = lifecycleScope.launch {
            var success = 0
            val fails = ArrayList<String>()
            var cancelled = false
            try {
                for ((i, uri) in uris.withIndex()) {
                    val prefix = "${i + 1}/${uris.size}"
                    var picked = withContext(Dispatchers.IO) { readPickedFile(uri, i) }
                    var tempFile: File? = null
                    try {
                        // HEIC照片先转成JPG
                        if (isHeic(picked)) {
                            if (Build.VERSION.SDK_INT < 28) {
                                fails.add("${picked.name}：HEIC格式的照片需要安卓9及以上的手机才能自动转换，请先转成JPG再上传")
                                continue
                            }
                            setUploadStatus("正在转换格式 $prefix：${picked.name}", 0)
                            val converted = withContext(Dispatchers.IO) { HeicConverter.toJpeg(this@AlbumDetailActivity, uri) }
                            if (converted == null) {
                                fails.add("${picked.name}：HEIC格式转换失败，请先转成JPG再上传")
                                continue
                            }
                            tempFile = converted
                            picked = picked.copy(
                                name = picked.name.substringBeforeLast('.') + ".jpg",
                                size = converted.length(),
                                mime = "image/jpeg"
                            )
                        }

                        // 超过服务器的大小上限，直接提示，不用白传
                        val isVideo = picked.mime.startsWith("video/")
                        val limitMb = if (isVideo) maxVideoMb else maxUploadMb
                        if (picked.size > limitMb * 1024L * 1024L) {
                            fails.add("${picked.name}：${if (isVideo) "视频" else "图片"}超过${limitMb}MB限制")
                            continue
                        }

                        val fileForUpload = tempFile
                        val label = "$prefix：${picked.name}"
                        setUploadStatus("正在上传 $label", 0)
                        var lastPercent = -1
                        val requestBody = ProgressRequestBody(
                            picked.mime.toMediaTypeOrNull(),
                            picked.size,
                            openStream = {
                                if (fileForUpload != null) FileInputStream(fileForUpload) else contentResolver.openInputStream(uri)
                            },
                            onProgress = { sent, total ->
                                val percent = if (total > 0) (sent * 100 / total).toInt() else -1
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    runOnUiThread {
                                        if (total > 0 && sent >= total) {
                                            setUploadStatus(
                                                "服务器处理中 $label" + if (isVideo) "（视频转码可能要几分钟，请耐心等待）" else "",
                                                100
                                            )
                                        } else {
                                            setUploadStatus("正在上传 $label  " + if (percent >= 0) "$percent%" else "", maxOf(percent, 0))
                                        }
                                    }
                                }
                            }
                        )
                        val photoPart = MultipartBody.Part.createFormData("photo", picked.name, requestBody)
                        val albumIdBody = albumId.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                        val response = ApiClient.service(this@AlbumDetailActivity).uploadPhoto(token, albumIdBody, photoPart)
                        val result = response.body()
                        if (response.isSuccessful && result != null && result.error == null && result.id != null) {
                            success++
                            result.toPhoto()?.let { insertUploadedPhoto(it) }
                        } else if (response.isSuccessful) {
                            fails.add("${picked.name}：${result?.error ?: "服务器没有返回结果"}")
                        } else {
                            val err = ApiClient.parseError(response)
                            fails.add("${picked.name}：${err.message}")
                            if (response.code() == 401 && err.needLogin) {
                                onAuthExpired()
                                for (k in i + 1 until uris.size) fails.add("第${k + 1}个：登录已过期，没有上传")
                                break
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        fails.add("${picked.name}：${ApiClient.networkErrorMessage(e)}")
                    } finally {
                        tempFile?.delete() // 新版：不管成功失败都删掉临时文件
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } finally {
                finishUpload(success, fails, cancelled)
            }
        }
    }

    private fun finishUpload(success: Int, fails: List<String>, cancelled: Boolean) {
        uploading = false
        uploadJob = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.uploadPanel.visibility = View.GONE
        updateManageUi()
        updateSubtitle()
        if (isFinishing || isDestroyed) return
        if (fails.isEmpty() && !cancelled) {
            toast("上传完成：成功${success}个")
            return
        }
        val title = if (cancelled) "已取消上传：成功${success}个" else "上传完成：成功${success}个，失败${fails.size}个"
        val msg = StringBuilder()
        fails.take(20).forEach { msg.append("· ").append(it).append('\n') }
        if (fails.size > 20) msg.append("……还有${fails.size - 20}个\n")
        if (cancelled) msg.append("\n（取消前已经传完、正在服务器处理的那一个，服务器会继续保存好）")
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg.toString().trim().ifEmpty { "没有失败的" })
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun confirmCancelUpload() {
        if (!uploading) return
        AlertDialog.Builder(this)
            .setTitle("取消上传？")
            .setMessage("还没传的不会再传了。")
            .setPositiveButton("取消上传") { _, _ -> uploadJob?.cancel() }
            .setNegativeButton("继续上传", null)
            .show()
    }

    // 新上传成功的照片插到最前面（新版：不用整页刷新）
    // （最终版补充）正在多选时先不插入：插到最前面会让所有格子的位置都往后挪一格，
    // 正在拖动选择的范围会错位一格。先放进 pendingUploaded，退出多选时再插到最前面。
    private fun insertUploadedPhoto(photo: Photo) {
        if (currentPhotos.any { it.id == photo.id } || pendingUploaded.any { it.id == photo.id }) return
        albumTotal++
        updateSubtitle()
        if (adapter.selectionMode) {
            pendingUploaded.add(photo)
            return
        }
        insertAtTopNow(photo)
    }

    private fun insertAtTopNow(photo: Photo) {
        if (currentPhotos.any { it.id == photo.id }) return // 比如期间下拉刷新过，列表里已经有了
        val atTop = gridLayoutManager.findFirstVisibleItemPosition() <= 0
        currentPhotos.add(0, photo)
        adapter.insertAtTop(photo)
        binding.textEmpty.visibility = View.GONE
        if (atTop) binding.recyclerPhotos.scrollToPosition(0)
    }

    // 退出多选后，把多选期间传完的照片插到最前面（按上传完成的先后顺序，最新的在最前）
    private fun flushPendingUploaded() {
        if (pendingUploaded.isEmpty()) return
        val list = ArrayList(pendingUploaded)
        pendingUploaded.clear()
        list.forEach { insertAtTopNow(it) }
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
                // 404 表示服务器上已经没有这张了（比如在别的手机上删过），同样从列表里移除
                if (response.isSuccessful || response.code() == 404) {
                    toast("已删除")
                    removeLocally(setOf(photo.id))
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

    // 新版：删除后只移除对应的格子，列表停在原来的位置（以前会整页刷新回到顶部）
    private fun removeLocally(ids: Set<Int>) {
        val before = currentPhotos.size
        currentPhotos.removeAll { ids.contains(it.id) }
        val removed = before - currentPhotos.size
        adapter.removeIds(ids)
        albumTotal = maxOf(currentPhotos.size, albumTotal - removed)
        updateSubtitle()
        updateEmptyTip()
        if (currentPhotos.isEmpty() && hasMorePhotos) {
            loadPhotos(reset = false)
        } else {
            binding.recyclerPhotos.post { maybeLoadMore() }
        }
    }

    // ================= 多选模式与批量删除（新增，不影响上面单个删除） =================
    // 从"多选按钮"进入（不预选任何项）
    private fun enterSelectMode(preSelectId: Int?) {
        if (!SessionManager.isLoggedIn(this)) return
        adapter.setSelectionMode(true)
        if (preSelectId != null) adapter.toggleSelection(preSelectId)
        binding.selectToolbar.visibility = View.VISIBLE
        dragSelectListener.setActive(true)
        updateManageUi()
        updateSelectToolbar()
    }

    // 从长按某个格子进入，并立即以该格子为起点开始拖选（新版：已经在多选模式时长按，也能开始拖选）
    private fun enterSelectModeAndDrag(position: Int) {
        if (!adapter.selectionMode) {
            adapter.setSelectionMode(true)
            binding.selectToolbar.visibility = View.VISIBLE
            dragSelectListener.setActive(true)
            updateManageUi()
        }
        dragSelectListener.startDrag(position) // 长按的这张先选中，接着手指滑动可连续选
        updateSelectToolbar()
    }

    private fun exitSelectMode() {
        adapter.setSelectionMode(false)
        dragSelectListener.setActive(false)
        binding.selectToolbar.visibility = View.GONE
        updateManageUi()
        flushPendingUploaded()
    }

    // 新版：按钮上显示选中了几项；全选后按钮变成“取消全选”
    private fun updateSelectToolbar() {
        val n = adapter.getSelectedIds().size
        binding.btnBatchDelete.text = if (n > 0) "删除选中($n)" else "删除选中"
        binding.btnSelectAll.text = if (adapter.isAllSelected()) "取消全选" else "全选"
    }

    private fun confirmBatchDelete() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) {
            toast("还没选中任何内容")
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
                    val body = response.body()
                    toast("已删除${body?.deleted ?: 0}项")
                    exitSelectMode()
                    val deletedIds = body?.deleted_ids
                    if (deletedIds != null) {
                        removeLocally(deletedIds.toSet())
                    } else {
                        loadPhotos(reset = true) // 老服务器没告诉删了哪些，只能整页刷新
                    }
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
                    toast("未输入密码，删除已取消")
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
                    toast("已删除相册")
                    SessionManager.clearViewToken(this@AlbumDetailActivity, albumId)
                    finish()
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

    // ================= 相册密码管理（管理员） =================
    private fun showSetPasswordDialog() {
        val input = EditText(this)
        input.hint = "新密码（留空 = 取消密码保护）"
        AlertDialog.Builder(this)
            .setTitle("相册密码设置")
            .setMessage("默认密码是 z394，你可以在这里改成别的，或者留空取消密码保护，让任何人都能直接看。")
            .setView(input)
            .setPositiveButton("保存") { _, _ -> setAlbumPassword(input.text.toString().trim()) }
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
                    toast(if (password.isEmpty()) "已取消密码保护" else "密码已更新")
                    // 改密码后服务器会让之前所有的查看令牌失效（包括这台手机的）
                    SessionManager.clearViewToken(this@AlbumDetailActivity, albumId)
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

    // ================= 打开大图/视频查看器（支持左右滑动切换） =================
    private fun openViewer(position: Int) {
        // 不再把整个列表塞进Intent（几百上千张会超出系统上限导致闪退），改用共享内存传递
        PhotoStore.photos = ArrayList(currentPhotos)
        PhotoStore.albumId = albumId
        PhotoStore.totalCount = maxOf(albumTotal, currentPhotos.size)
        PhotoStore.hasMore = hasMorePhotos
        PhotoStore.lastViewedIndex = position
        storeVersionSeen = PhotoStore.version
        val intent = Intent(this, PhotoViewerActivity::class.java)
        intent.putExtra("start_index", position)
        intent.putExtra("album_id", albumId)
        startActivity(intent)
    }

    // 新版：从大图页回来时，如果大图页往后多加载了照片，同步到格子列表；并滚动到刚才看的那张附近
    private fun syncFromViewer() {
        if (storeVersionSeen < 0 || PhotoStore.albumId != albumId) return
        if (PhotoStore.version != storeVersionSeen) {
            val known = HashSet<Int>()
            currentPhotos.forEach { known.add(it.id) }
            val lastId = currentPhotos.lastOrNull()?.id ?: Int.MAX_VALUE
            val extra = PhotoStore.photos.filter { it.id < lastId && known.add(it.id) }
            if (extra.isNotEmpty()) {
                currentPhotos.addAll(extra)
                adapter.appendData(extra)
            }
            hasMorePhotos = PhotoStore.hasMore
            if (!hasMorePhotos) albumTotal = currentPhotos.size
            updateSubtitle()
        }
        val viewed = PhotoStore.photos.getOrNull(PhotoStore.lastViewedIndex)
        if (viewed != null) {
            val pos = currentPhotos.indexOfFirst { it.id == viewed.id }
            if (pos >= 0) {
                val first = gridLayoutManager.findFirstVisibleItemPosition()
                val last = gridLayoutManager.findLastVisibleItemPosition()
                if (pos < first || pos > last) binding.recyclerPhotos.scrollToPosition(pos)
            }
        }
        storeVersionSeen = -1
    }
}
