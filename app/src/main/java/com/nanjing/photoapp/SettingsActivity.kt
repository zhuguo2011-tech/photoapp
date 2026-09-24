package com.nanjing.photoapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nanjing.photoapp.api.ApiClient
import com.nanjing.photoapp.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editBaseUrl.setText(SessionManager.getBaseUrl(this))

        binding.btnSave.setOnClickListener {
            val url = binding.editBaseUrl.text.toString().trim()
            if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                Toast.makeText(this, "地址要以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SessionManager.setBaseUrl(this, url)
            Toast.makeText(this, "已保存，重新进入相册即可生效", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnReset.setOnClickListener {
            SessionManager.resetBaseUrl(this)
            binding.editBaseUrl.setText(ApiClient.DEFAULT_BASE_URL)
            Toast.makeText(this, "已恢复默认地址", Toast.LENGTH_SHORT).show()
        }
    }
}
