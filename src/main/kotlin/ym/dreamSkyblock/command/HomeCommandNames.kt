package ym.dreamSkyblock.command

import java.util.Locale

/** One vocabulary for execution and completion, including administrator commands. */
internal object HomeCommandNames {
    val chinese = linkedMapOf(
        "菜单" to "gui", "创建" to "create", "传送" to "tp", "邀请" to "invite",
        "接受" to "accept", "拒绝" to "deny", "退出" to "leave", "删除" to "delete",
        "设置" to "settings", "帮助" to "help", "重载" to "reload", "备份" to "backup",
        "恢复" to "restore", "迁移" to "migrate"
    )
    val admin = setOf("reload", "backup", "restore", "migrate")
    fun resolve(name: String): String = name.lowercase(Locale.ROOT).let { chinese[it] ?: it }
    fun suggestions(administrator: Boolean): List<String> =
        (chinese.values + chinese.keys).filter { administrator || resolve(it) !in admin }
}
