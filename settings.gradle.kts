pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google") { isAllowInsecureProtocol = true }
        maven("https://maven.aliyun.com/repository/gradle-plugin") { isAllowInsecureProtocol = true }
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") { isAllowInsecureProtocol = true }
        maven("https://maven.aliyun.com/repository/public") { isAllowInsecureProtocol = true }
        maven("https://jitpack.io")
        google()
        mavenCentral()
    }
}
rootProject.name = "DevCodex"
include(":app")
