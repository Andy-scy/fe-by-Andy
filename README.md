# Fe（by Andy）

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](./LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84)](https://developer.android.com)

读取本机 **E听说**（`com.ets100.secondary`）已下载作业、并结构化展示答案的 Android 应用。
Material 3 + Monet 动态取色，通过 Shizuku / Root 读取 `Android/data` 下的本地文件，**不联网、不上传**。

## 来源与授权

本项目**改自 [laststudio/Fuck_ets100](https://github.com/laststudio/Fuck_ets100)**，沿用其数据目录结构、
题型字段与组卷思路；**已获得原作者授权**后开源发布。

界面、组卷与解析逻辑均为重写实现，未直接复制原项目代码。感谢原作者 **@laststudio** 及其团队成员
（[@leitianshuo1337](https://github.com/leitianshuo1337)）的支持。

## 功能

- **作业列表**：按下载时间倒序，最近一份打「最新」徽章，显示 `刚刚下载 / N 分钟前下载 / 今天 21:03 下载`
- **答案速览**：进入试卷后置顶可折叠面板 —— 选择题连成一行（`1、a 2、c 3、b`），其余题取第一条参考答案、每题一行
- **试卷详情**：选择题正确项高亮、同义答案折叠、关键词、听力/朗读原文、转述要点、音频播放
- **读取通道**：Shizuku（shell 身份）/ Root / 本地镜像，自动探测择优，首页可展开查看诊断
- **系统特性**：Material 3 + Monet 取色、edge-to-edge、预测性返回跟手动画

## 使用前提

1. Android 8.0（API 26）及以上；
2. 安装并启动 [Shizuku](https://shizuku.rikka.app/)（或具备 Root）；
3. OPPO / ColorOS 等机型需先在开发者选项打开 **「禁止权限监控」**，否则 Shizuku 只会弹「adb 权限受限」且无允许按钮；
4. 首次打开应用自动弹 Shizuku 授权 → 选「始终允许」，顺手允许「照片、视频和音频」。

## 构建

```bash
./gradlew :app:assembleDebug
# 需要 JDK 21、Android SDK Platform 37、Build-Tools 36+
```

推送 `v*` 标签会自动构建并发布 APK，见 [`.github/workflows/build.yml`](.github/workflows/build.yml)。

## 说明

- 仅读取本机已下载数据，不联网、不上传任何内容；**仅供个人核对作业，请勿用于考试作弊**；
- 与科大讯飞 / E听说 / 畅言 / 各级学校无任何隶属、合作或背书关系；
- 启动图标来自第三方应用的商标图形，**不在 GPL-3.0 授权范围内**，二次分发请自行替换；
- Release 中的 APK 为 **debug 签名**，仅供安装测试，正式分发请自行签名构建。

## 协议

[GPL-3.0](./LICENSE)
