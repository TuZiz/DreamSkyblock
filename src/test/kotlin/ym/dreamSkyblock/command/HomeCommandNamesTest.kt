package ym.dreamSkyblock.command

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HomeCommandNamesTest {
    @Test fun `all subcommands resolve in Chinese with same admin visibility`() {
        HomeCommandNames.chinese.forEach { (alias, english) ->
            assertEquals(english, HomeCommandNames.resolve(alias))
            assertEquals(english, HomeCommandNames.resolve(english.uppercase()))
            assertEquals(english !in HomeCommandNames.admin, alias in HomeCommandNames.suggestions(false))
            assertTrue(alias in HomeCommandNames.suggestions(true))
        }
        assertEquals("teleport", HomeCommandNames.resolve("TELEPORT"))
        assertEquals("gui", HomeCommandNames.resolve("菜单"))
    }
}
