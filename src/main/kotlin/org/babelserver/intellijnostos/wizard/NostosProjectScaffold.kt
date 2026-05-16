package org.babelserver.intellijnostos.wizard

/**
 * Content of the files generated for a new Nostos project.
 *
 * Kept as pure functions, separate from the wizard UI, so the generated
 * content can be unit-tested. The output mirrors what `nostos init` produces.
 */
internal object NostosProjectScaffold {

    /** The hello-world entry point written to main.nos. */
    fun mainNosContent(): String = """
        # Main entry point

        main() = {
            println("Hello from Nostos!")
            0
        }
    """.trimIndent() + "\n"

    /** A minimal nostos.toml manifest for a project with the given name. */
    fun nostosTomlContent(projectName: String): String = """
        [project]
        name = "$projectName"
        version = "0.1.0"
    """.trimIndent() + "\n"

    /** A .gitignore covering the caches nostos writes into a project directory. */
    fun gitignoreContent(): String = """
        # Nostos build cache
        .nostos-cache/

        # Per-definition files for the REPL/TUI
        .nostos/
    """.trimIndent() + "\n"
}
