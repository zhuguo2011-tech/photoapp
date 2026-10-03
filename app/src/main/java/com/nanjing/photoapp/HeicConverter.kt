package com.nanjing.photoapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.annotation.RequiresApi
import java.io.File

// 新版新增：把 HEIC/HEIF 格式的照片（苹果手机、部分安卓手机的“高效格式”照片）转成 JPG 再上传。
// 原因：服务器生成缩略图用的 PHP 和网页浏览器大多不认识 HEIC，直接传上去别人看不了。
// 只有安卓9（API 28）及以上的手机系统自带 HEIC 解码能力，更老的手机会提示用户先自己转换。
// 转换时会自动按照片的拍摄方向转正；超大照片会缩小到最长边4096像素以内，防止手机内存不够。
object HeicConverter {

    private const val MAX_SIDE = 4096

    @RequiresApi(28)
    fun toJpeg(context: Context, uri: Uri): File? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                if (w > MAX_SIDE || h > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / maxOf(w, h)
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
                // 要把图片重新压缩成JPG，需要普通内存里的位图（硬件位图不能直接压缩）
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val file = File(context.cacheDir, "heic_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            bitmap.recycle()
            file
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
