package com.andy.etsviewer

import android.app.Application

/**
 * 进程级入口。
 *
 * 这里刻意不做 DI 框架：整个读文件链路只有「一次探测 + 一个 Reader 实例」，
 * 手工持有比引入 Hilt/KSP 更轻，编译也更快。
 */
class EtsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: EtsApplication
            private set
    }
}
