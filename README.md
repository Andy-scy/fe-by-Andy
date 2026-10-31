# Fe（by Andy）

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](./LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84)](https://developer.android.com)

一个**独立实现**的 Android 应用：读取设备上 E听说（`com.ets100.secondary`）已下载作业的
`content.json`，把题干、选项、参考答案、听力原文、转述要点结构化展示出来。

> 应用名 **Fe**，主界面标题为 `Fe by Andy`。
> 仅实现「本地已下载内容的读取与展示」：不含云端接口、不含账号体系、不做任何绕过授权的事情。
> 原项目 `laststudio/Fuck_ets100` 仅作**思路参考**（数据目录结构、题型字段、零宽字符绕过），本项目代码为全新编写。

## 功能一览

- **首页**：按「下载时间」倒序列出本地作业，最近一份打 `最新` 徽章，并显示 `刚刚下载 / N 分钟前下载 / 今天 21:03 下载`
- **答案速览**：进入试卷后置顶一块可折叠面板，纯文本排版 —— 选择题连成一行（`1、a 2、c 3、b`），其余题取第一条参考答案、每题一行，超长可点开
- **试卷详情**：题型分组、选择题正确项高亮、同义答案折叠、关键词、听力/朗读原文、转述要点、音频播放
- **读取通道**：Shizuku（shell 身份）/ Root / 本地镜像 三选一自动探测，首页可查看每条通道的诊断状态
- **系统特性**：Material 3 + Monet 动态取色、edge-to-edge、预测性返回跟手动画（Android 17 已实测系统预览生效）

## 使用前提

1. 手机已安装并启动 [Shizuku](https://shizuku.rikka.app/)（或用 Root）；
2. 部分厂商（如 OPPO/ColorOS）需要用 adb 启动 Shizuku 时，先在开发者选项打开 **「禁止权限监控」**，
   否则 Shizuku 只会弹「adb 权限受限」且没有允许按钮；
3. 首次打开应用会自动弹 Shizuku 授权框 → 选「始终允许」；顺手把「访问照片、视频和音频」也允许掉。

## 免责声明

- 本项目仅供**个人在自己设备上核对已下载的作业**使用，请勿用于考试作弊等违反学校规定或平台协议的场景；
- 项目与科大讯飞 / E听说 / 畅言 / 各级学校**无任何隶属、合作或背书关系**；
- 读取的是本机已下载到 `Android/data` 下的数据，不联网、不上传任何内容；
- **图标说明**：`app/src/main/res/mipmap-*` 中的图标来自第三方应用（E听说 学生版）的商标图形，
  仅作个人识别用途，**不属于本项目 GPL-3.0 授权范围**。若你要分发本项目的构建产物，请先替换为自己的图标。

## 技术栈

| 项 | 选择 |
| :--- | :--- |
| UI | Jetpack Compose + Material 3（`material3 1.5.0-alpha24`），Compose BOM 2026.06.01 |
| 取色 | Android 12+ 莫奈动态取色（`dynamicLight/DarkColorScheme`），低版本回退品牌绿 |
| 返回 | 自研极简返回栈 + `PredictiveBackHandler` 跟手动画（Android 16/17 可延伸为跨应用预览） |
| 架构 | 单模块；`data/fs`（4 条读取通道）+ `data/ets`（扫描/解析）+ `ui`（Compose） |
| 构建 | AGP 9.3.1 / Gradle 9.6.1 / JDK 21 / `compileSdk 37` |

## 构建

```bash
# 需要 JDK 21、Android SDK Platform 37、Build-Tools 36+
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```


## 读取通道（按探测顺序）

| 通道 | 原理 | Android 15（OPPO PFZM10）实测 |
| :--- | :--- | :--- |
| `DIRECT` | `Android/\u200Bdata` 零宽字符路径规范化绕过 | ❌ 已失效（`resolved = null`，4 个零宽字符全部试过） |
| `SHIZUKU` | 反射调用私有的 `Shizuku.newProcess`，以 shell 身份 `ls`/`find`/`cat` | ✅ **当前生效**（需开启开发者选项「禁止权限监控」后再授权） |
| `ROOT` | `su -c` | — 本机无 root |
| `MIRROR` | 读应用可访问的本地副本（Download / 应用外部目录 / 内部目录） | ✅ 可用兜底（镜像已移除，现由 Shizuku 直读原始目录） |

### 已验证的「不依赖 Shizuku / ADB」的替代路径（全部不可行）

| 尝试 | 结论 |
| :--- | :--- |
| `targetSdk=29` + `requestLegacyExternalStorage` + `READ_EXTERNAL_STORAGE` | ❌ 已授权仍 `Permission denied`；API 35 关闭了 legacy 豁免 |
| `/sdcard/Android/media/com.ets100.secondary` | ❌ 该目录不存在，E听说 不往可读位置写 |
| 公共目录 `/sdcard/ets100`、`Download` 自动落盘 | ❌ 无此类目录 |
| 借用 E听说 导出的 ContentProvider | ❌ 只有第三方 SDK 的 Provider（getui/AGC/SLS/picture-lib），无文件级导出 |
| MediaStore 索引 | ❌ 答案 JSON 不在媒体库里 |

**结论：在 Android 15 上，非 Root 应用读取别的应用 `Android/data` 只有 Shizuku（shell 身份）这一条路；否则必须走镜像副本。**

> 补充实测：`targetSdk=29 + requestLegacyExternalStorage` 在 Android 15 上**也不再**能读其它应用的
> `Android/data`（已授权 `READ_EXTERNAL_STORAGE` 仍 Permission denied）。legacy 豁免在 API 35 已关闭。

### 换新手机 / 重装后的正确姿势

1. 安装 APK 后**第一次启动**，应用会自动弹「要允许 ETS 阅读器使用 Shizuku 吗？」→ 点「始终允许」；
   （首次还会弹一次「访问照片、视频和音频文件」→ 允许，否则读图/音频素材会失败）
2. 若当时点了拒绝：回到首页点「授权 Shizuku（直读原始目录）」即可重新弹；
3. 若提示「Shizuku 服务未运行」：先启动 Shizuku 服务（重装/重启后 Shizuku 需要重新激活）。

> 实测（Android 17 / realme RMX8899）：新装后未授权时首页提示「Shizuku 服务已在运行，点下方按钮授权后即可直读原始目录」，
> 授权后立刻列出 **55 份**本地作业；系统级预测性返回预览（窗口缩放 + 返回箭头）也已确认生效。

### 让 Shizuku 直接读（本机已验证可用）

1. 手机「开发者选项」打开 **「禁止权限监控」**（ColorOS/OPPO；不开的话 Shizuku 只弹「adb 权限受限」且没有允许按钮）；
2. 电脑执行一次：`adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`；
3. 打开应用 → 首页点「授权 Shizuku（直读原始目录）」→「始终允许」。

授权后首页显示「读取通道：Shizuku（shell 身份）」，直接读原始目录；移除镜像副本后仍可正常扫描（已实测）。

### 镜像通道（当前可用）

```bash
adb shell cp -r /sdcard/Android/data/com.ets100.secondary/files/Download/ETS_secondary /sdcard/Download/
```

应用会自动读取 `/sdcard/Download/ETS_secondary`（也支持 `<externalFilesDir>/ETS_secondary` 与
`<filesDir>/ETS_secondary`）。用任意文件管理器把目录拷到 `Download` 亦可，无需 adb。

## 组卷逻辑

1. **data 索引线**：`data/*` 每个 JSON 描述一份作业（`paperId` + `fileNameData[]` +
   `sectionData[].caption`）。同一 `paperId` 会存在多份，按 `paperId` 去重。
2. **res 模板线**：带 `res.json` 的目录是整卷模板（`{{stN}}` 顺序）；content 目录按 `info.stid`
   升序排名后与模板题型序列做连续匹配（样本中 10 个 section 与 10 段模板完全对齐）。
3. **孤立内容**：未匹配到的 content 目录单独成卷，保证不丢数据。

## 题型解析（实测字段）

| `structure_type` | 答案位置 | 备注 |
| :--- | :--- | :--- |
| `collector.choose` | `info.xtlist[].answer` + `xxlist[]{xx_mc,xx_nr}` | 答案可能是 `A` / `A,C` / `AC` |
| `collector.role` | `info.question[].std[].value`（每题 7–71 条同义答案） | `answer` 字段为空串，不是答案；题号在 `xh` |
| `collector.picture` | `info.std[].value`（同题多篇范文）+ `info.keypoint` | 不是一个 `std` 一道题 |
| `collector.read` | 无答案，展示 `info.value` 原文 | 另有 `info.translate`/`info.ai` |
| `collector.fill` | `info.std[]{xth,value}` | 样本未出现，已按文档实现 |

文本清洗：剥离 `ets_thN` / `ets_smN` 前缀、HTML 标签、零宽空格，`</p><p>`→换行，实体反转义。

## 构建 / 安装

```bash
gradle :app:assembleDebug        # 或 ./gradlew
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 作业排序与「最新」标记

本地文件里**没有作业名**（`data/` 只有 paperId + 题型标题，`temp/` 只有班级/账号元数据），
因此用「下载时间」来区分：

- 每份作业的 `updatedMs` = max(它的 `data/` 索引文件 mtime, 各 section 目录 mtime)；
- 首页按此**倒序**排列，最近下载的排第一，并打上「最新」徽章；
- 每张卡片显示相对时间：`刚刚下载` / `N 分钟前下载` / `今天 HH:mm 下载` / `昨天 HH:mm 下载` / `MM-dd HH:mm 下载`；
- **未归类内容**（没有 data 索引又没有匹配模板的散装题包）默认折叠，但 **24 小时内新下载的一律显示**，避免刚下的卷被藏起来。

> 组卷修复：模板匹配原先按 `stid` 排序取第一段匹配，会被旧卷抢走模板，导致新下载的卷落到「未归类」。
> 现改为**先按 mtime 聚类（60s 阈值）再做题型序列对齐**，一次下载产生的一组 section 才能正确认领自己的模板。

## 答案速览（置顶面板）

进入任意试卷详情，顶部固定一块可折叠的「答案速览」，规则：

- **题号全卷连续**（1…N），与下方详情卡片一致；同一题型有多个分组时标注「第 N 组」；
- **选择题**：直接给选项字母（多个答案则 `A,C`）；
- **问答题 / 信息转述 / 听后转述 / 填空**：默认取**第一条参考答案**，压成单行（超 80 字符省略）；
- **朗读类**：无答案，标注「朗读原文（无答案）」；
- 折叠后保留一行极简摘要，例如 `1A 2C | 3B 4C | 5B 6B | 听后回答 7·Thirteen. | …`；
- 点击面板里的小节标题可跳转到对应小节。

## 已实现

- [x] 三条通道自动探测 + 镜像兜底，诊断卡片展示每条通道状态
- [x] 组卷：data 索引（同 paperId 多份索引按 baseDirs 并集合并，结果与列举顺序无关）+ res 模板 `{{stN}}`×`stid` 排名匹配 + 孤立内容兜底
- [x] 解析 6 种 `structure_type`，选择题正确项高亮、同义答案折叠、关键词、听力/朗读原文折叠、转述要点
- [x] 试卷命名：「地区+学段+总分」（如「北京初中 · 40 分卷」），不再直接暴露 8 位目录名
- [x] 音频播放：本地通道直连路径，Shizuku/Root 通道按需 `cat` 到 `cache/material/<目录>_<文件>` 再播（实测 AudioTrack 正常起播）
- [x] 性能：用一次 `find` 取到哪些目录真的有 `content.json`/`res.json`，避免对 16 个目录逐个盲目 `cat`（失败数为 0）
- [x] 预测性返回跟手动画（缩放+变暗），Android 15 上返回手势已生效
- [x] 置顶「答案速览」面板（连续题号 / 选择题字母 / 其他取第一条答案 / 可折叠 / 点击跳转）
- [x] 修复 Shizuku 通道 `exitValue()` 抛 `IllegalArgumentException: process hasn't exited` 导致扫描为 0 份的问题（改为读到 EOF + stderr 判失败）
- [x] 修复 `find -maxdepth` 在 OPPO/Android 15 FUSE 上超时返回空结果的问题（改用逐目录 `ls` 判断文件存在）

## 待办

- [ ] 材料图片（`material/`）预览
- [ ] 预测性返回：在 Android 17 设备上验证跨应用预览效果
- [ ] release 签名与 R8 打包
- [ ] 作业标题（当前用 `作业 <paperId>`，可从服务端作业名补全）
