package ym.dreamSkyblock.world

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** 初始地形生成并经Paper flush保存后才写凭据，恢复创建不会重新生成世界。 */
internal class HomeCreationReceipt(private val root: Path) {
    private val io = WorldArchiveIO()
    fun record(id: UUID, worldKey: String, worldUuid: UUID) = io.writeProperties(path(id), mapOf(
        "schema-version" to "1", "home-id" to id.toString(), "world-key" to worldKey, "world-uuid" to worldUuid.toString()
    ))

    fun completed(id: UUID, worldKey: String): Boolean {
        val receipt = io.checked(path(id))
        if (!Files.exists(receipt)) return false
        val fields = io.readProperties(receipt)
        require(fields["schema-version"] == "1" && fields["home-id"] == id.toString() && fields["world-key"] == worldKey) { "家园创建凭据无效: $receipt" }
        UUID.fromString(fields.getValue("world-uuid"))
        return true
    }
    private fun path(id: UUID) = root.resolve("$id.properties")
}
