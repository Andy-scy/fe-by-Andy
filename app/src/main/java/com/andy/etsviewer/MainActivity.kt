package com.andy.etsviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.andy.etsviewer.data.fs.ShizukuBridge
import com.andy.etsviewer.ui.AppViewModel
import com.andy.etsviewer.ui.EtsReaderApp
import com.andy.etsviewer.ui.theme.EtsViewerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private var permissionRequested = false

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏绘制 + 由 Compose 处理 insets，配合 Android 15 强制 edge-to-edge。
        enableEdgeToEdge()
        setContent {
            EtsViewerTheme {
                EtsReaderApp(viewModel = viewModel)
            }
        }
        ensureStoragePermission()
    }

    override fun onResume() {
        super.onResume()
        maybeAutoRequestShizuku()
    }

    /**
     * 服务在运行但本应用尚未授权时，自动弹一次授权框（每个安装周期一次）。
     *
     * 换机场景下最容易踩的坑就是：Shizuku 明明启动了，但新装的应用还没被授权，
     * 用户会以为「应用坏了」。这里主动把授权入口推给用户。
     */
    private fun maybeAutoRequestShizuku() {
        if (!ShizukuBridge.installed(this)) return
        if (!ShizukuBridge.alive() || ShizukuBridge.granted()) return
        if (ShizukuBridge.autoAskedBefore(this)) return
        ShizukuBridge.markAutoAsked(this)
        ShizukuBridge.requestPermission { granted ->
            if (granted) viewModel.refresh()
        }
    }

    /**
     * legacy 存储模式下，读外部存储仍需 READ_EXTERNAL_STORAGE 运行时权限。
     */
    private fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Shizuku 服务在运行、但应用还没被授权时，主动弹一次授权框。
     * 授权成功后立即重新扫描，避免用户手动点刷新。
     */
    private fun maybeRequestShizuku() {
        if (permissionRequested) return
        if (!ShizukuBridge.installed(this) || !ShizukuBridge.alive() || ShizukuBridge.granted()) return
        permissionRequested = true
        ShizukuBridge.requestPermission { granted ->
            permissionRequested = false
            if (granted) viewModel.refresh()
        }
    }
}
