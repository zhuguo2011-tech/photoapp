package com.nanjing.photoapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nanjing.photoapp.api.ApiClient
import com.nanjing.photoapp.databinding.ActivityLoginBinding
import com.nanjing.photoapp.model.LoginRequest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val username = binding.editUsername.text.toString().trim()
        val password = binding.editPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressLogin.visibility = android.view.View.VISIBLE
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.login(LoginRequest(username, password))
                if (response.isSuccessful && response.body()?.token != null) {
                    val body = response.body()!!
                    SessionManager.saveLogin(this@LoginActivity, body.token!!, body.username ?: username)
                    Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, ApiClient.errorMessage(response), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "网络请求失败，请检查服务器地址和网络连接", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressLogin.visibility = android.view.View.GONE
                binding.btnLogin.isEnabled = true
            }
        }
    }
}
