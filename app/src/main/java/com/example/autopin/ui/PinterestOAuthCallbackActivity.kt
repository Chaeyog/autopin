package com.example.autopin.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.autopin.MainActivity
import com.example.autopin.data.pinterest.PinterestRepositoryProvider

class PinterestOAuthCallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data
        if (data != null) {
            PinterestRepositoryProvider.getInstance(this).handleOAuthRedirect(data)
        }

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        finish()
    }
}
