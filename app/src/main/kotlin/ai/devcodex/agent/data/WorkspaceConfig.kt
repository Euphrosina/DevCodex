package ai.devcodex.agent.data

/** 全局工作区配置：空 = 应用私有目录 filesDir/workspace；非空 = 用户自定义绝对路径 */
object WorkspaceConfig {
    @Volatile
    var dir: String = ""
}
