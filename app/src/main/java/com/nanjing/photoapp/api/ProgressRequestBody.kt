package com.nanjing.photoapp.api

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream

// 新版新增：带“上传进度”的请求体。
// 边读文件边发送，每发出去一段就回调一次进度（已发送字节数, 总字节数）。
// 另外它是直接从相册的Uri读取数据上传的，不用像以前那样先把整个文件复制到APP缓存里
// （大视频能省下几秒钟的复制时间和几百MB的手机存储空间）。
// openStream 每次都重新打开一次文件：网络出错OkHttp自动重试时会重新从头发送。
class ProgressRequestBody(
    private val mediaType: MediaType?,
    private val length: Long, // 文件大小，不知道就传 -1（会改用分块传输）
    private val openStream: () -> InputStream?,
    private val onProgress: (sent: Long, total: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = length

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val input = openStream() ?: throw IOException("无法读取这个文件")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            while (true) {
                val n = stream.read(buffer)
                if (n == -1) break
                sink.write(buffer, 0, n)
                sent += n
                onProgress(sent, length)
            }
        }
    }
}
