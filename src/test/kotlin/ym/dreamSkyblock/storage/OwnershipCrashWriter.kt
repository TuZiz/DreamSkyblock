package ym.dreamSkyblock.storage

import ym.dreamSkyblock.model.Home
import ym.dreamSkyblock.model.HomeTemplate
import java.nio.file.Path
import java.util.UUID

/** Separate disposable JVM: intentionally skips close hooks to exercise surviving WAL/evidence. */
object OwnershipCrashWriter {
    @JvmStatic fun main(args: Array<String>) {
        val repository = SqliteHomeRepository(Path.of(args[0]))
        val home = Home(UUID.fromString(args[1]), "crash-owner", "skyblock", HomeTemplate.Point(0.0,100.0,0.0),48.0)
        home.owners.add(UUID.fromString(args[2]));home.status="CREATING"
        repository.save(HomeSnapshot.fromHome(home))
        Runtime.getRuntime().halt(0)
    }
}
