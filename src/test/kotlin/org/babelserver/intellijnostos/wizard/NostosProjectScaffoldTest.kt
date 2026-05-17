package org.babelserver.intellijnostos.wizard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NostosProjectScaffoldTest {

    @Test
    fun manifestEmbedsProjectNameAndDefaultVersion() {
        val toml = NostosProjectScaffold.nostosTomlContent("my-app")
        assertEquals(
            "[project]\nname = \"my-app\"\nversion = \"1.0.0\"\n",
            toml,
        )
    }

    @Test
    fun mainContainsRunnableEntryPoint() {
        val main = NostosProjectScaffold.mainNosContent()
        assertTrue(main.contains("main() = {"), "should declare a main entry point")
        assertTrue(main.contains("println(\"Hello from Nostos!\")"), "should print a greeting")
        assertTrue(main.endsWith("\n"), "should end with a trailing newline")
    }

    @Test
    fun gitignoreCoversNostosCaches() {
        val gitignore = NostosProjectScaffold.gitignoreContent()
        assertTrue(gitignore.contains(".nostos-cache/"), "should ignore the build cache")
        assertTrue(gitignore.contains(".nostos/"), "should ignore the REPL/TUI cache")
    }

    @Test
    fun readmeHeadsWithTheProjectNameAndShowsHowToRun() {
        val readme = NostosProjectScaffold.readmeContent("my-app")
        assertTrue(readme.startsWith("# my-app"), "should head with the project name")
        assertTrue(readme.contains("nostos src/"), "should show how to run the project")
    }
}
