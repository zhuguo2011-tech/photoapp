package com.nanjing.photoapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nanjing.photoapp.api.ApiClient
import com.nanjing.photoapp.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 服务器设置：每月换端口后在这里改地址，不用重新编译APP
// 【新版改进】
// - 输入的地址自动整理：只填“IP:端口”会自动补上 /photoapp/；粘贴网页版链接也行；
//   中文输入法打出来的全角冒号会自动改成半角
// - 新增“测试连接”按钮：保存前先试一下通不通，不通会说明原因
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editBaseUrl.setText(SessionManager.getBaseUrl(this))

        binding.btnSave.setOnClickListener {
            val url = SessionManager.normalizeBaseUrl(binding.editBaseUrl.text.toString())
            if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                Toast.makeText(this, "地址要以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.editBaseUrl.setText(url)
            SessionManager.setBaseUrl(this, url)
            Toast.makeText(this, "已保存，重新进入相册即可生效", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnTest.setOnClickListener { testConnection() }

        binding.btnReset.setOnClickListener {
            SessionManager.resetBaseUrl(this)
            binding.editBaseUrl.setText(ApiClient.DEFAULT_BASE_URL)
            Toast.makeText(this, "已恢复默认地址", Toast.LENGTH_SHORT).show()
        }
    }

    // 新版：用输入框里的地址试着读一次相册列表，看看能不能连上
    private fun testConnection() {
        val url = SessionManager.normalizeBaseUrl(binding.editBaseUrl.text.toString())
        if (url.isEmpty()) {
            Toast.makeText(this, "请先填写服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        binding.editBaseUrl.setText(url)
        binding.textTestResult.text = "正在测试 $url …"
        binding.btnTest.isEnabled = false
        lifecycleScope.launch {
            val result = try {
                val response = ApiClient.serviceFor(url).getAlbums()
                val list = response.body()
                if (response.isSuccessful && list != null) {
                    "✅ 连接成功！服务器上有 ${list.size} 个相册。\n别忘了点下面的“保存”。"
                } else {
                    "❌ 连接失败：" + ApiClient.parseError(response).message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                "❌ 地址格式不对，请检查（例如 http://146.56.204.247:62225/photoapp/）"
            } catch (e: Exception) {
                "❌ 连接失败：" + ApiClient.networkErrorMessage(e)
            }
            binding.textTestResult.text = result
            binding.btnTest.isEnabled = true
        }
    }
}
