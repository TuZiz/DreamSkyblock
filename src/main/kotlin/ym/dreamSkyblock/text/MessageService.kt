package ym.dreamSkyblock.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration

class MessageService(@Volatile private var messages: YamlConfiguration) {
    private val mini = MiniMessage.miniMessage()

    fun reload(messages: YamlConfiguration) {
        this.messages = messages
    }

    fun component(key: String, placeholders: Map<String, String> = emptyMap()): Component {
        val raw = messages.getString(key) ?: "<#FF5555>Missing message: $key"
        return componentRaw(raw, placeholders)
    }

    fun componentRaw(raw: String, placeholders: Map<String, String> = emptyMap()): Component {
        val resolvers = placeholders.map { Placeholder.parsed(it.key, it.value) }.toTypedArray()
        return mini.deserialize("<italic:false>$raw", *resolvers)
    }

    fun list(key: String, placeholders: Map<String, String> = emptyMap()): List<Component> =
        listRaw(messages.getStringList(key), placeholders)

    fun listRaw(raws: List<String>, placeholders: Map<String, String> = emptyMap()): List<Component> = raws.map { raw ->
            val resolvers = placeholders.map { Placeholder.parsed(it.key, it.value) }.toTypedArray()
            mini.deserialize("<italic:false>$raw", *resolvers)
        }

    fun send(sender: CommandSender, key: String, placeholders: Map<String, String> = emptyMap()) = sender.sendMessage(component(key, placeholders))
}
