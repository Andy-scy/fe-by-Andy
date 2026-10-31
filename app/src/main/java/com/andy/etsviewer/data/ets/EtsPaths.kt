package com.andy.etsviewer.data.ets

/** E听说 本地数据目录。路径固定，版本间未变（v7.0.71 实测）。 */
object EtsPaths {
    const val PACKAGE = "com.ets100.secondary"
    const val PARENT =
        "/sdcard/Android/data/com.ets100.secondary/files/Download/ETS_secondary"
    const val RESOURCE = "$PARENT/resource"
    const val DATA = "$PARENT/data"

    fun contentJson(dirName: String) = "$RESOURCE/$dirName/content.json"
    fun resJson(dirName: String) = "$RESOURCE/$dirName/res.json"
    fun infoJson(dirName: String) = "$RESOURCE/$dirName/info.json"
}
