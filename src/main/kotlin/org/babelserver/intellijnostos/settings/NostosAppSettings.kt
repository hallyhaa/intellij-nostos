package org.babelserver.intellijnostos.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import java.io.File

@Service(Service.Level.APP)
@State(
    name = "NostosAppSettings",
    storages = [Storage("nostos.xml")]
)
class NostosAppSettings : PersistentStateComponent<NostosAppSettings.State> {

    class State {
        var nostosPath: String = ""
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    /** Returns the configured path, or auto-detects if not set. */
    fun getEffectiveNostosPath(): String {
        if (myState.nostosPath.isNotBlank()) return myState.nostosPath
        return detectNostos() ?: "nostos"
    }

    companion object {
        fun getInstance(): NostosAppSettings =
            ApplicationManager.getApplication().getService(NostosAppSettings::class.java)

        /** Scans well-known locations for the nostos binary. */
        fun detectNostos(): String? {
            val candidates = buildList {
                // Check PATH via `which`/`where`
                findInPath()?.let { add(it) }

                // Common install locations
                add("/usr/bin/nostos")                 // Linux system package
                add("/usr/local/bin/nostos")           // macOS Intel, Linux manual install
                add("/opt/homebrew/bin/nostos")         // macOS Apple Silicon (Homebrew)
                add("/home/linuxbrew/.linuxbrew/bin/nostos") // Linux Homebrew

                // Home directory locations
                val home = System.getProperty("user.home")
                if (home != null) {
                    add("$home/.nostos/bin/nostos")
                    add("$home/.nostos/versions/current/nostos")
                    add("$home/.local/bin/nostos")
                }
            }
            return candidates.firstOrNull { File(it).canExecute() }
        }

        private fun findInPath(): String? {
            return try {
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val cmd = if (isWindows) "where" else "which"
                val process = ProcessBuilder(cmd, "nostos")
                    .redirectErrorStream(true)
                    .start()
                val path = process.inputStream.bufferedReader().readLine()?.trim()
                process.waitFor()
                if (process.exitValue() == 0 && !path.isNullOrBlank()) path else null
            } catch (_: Exception) {
                null
            }
        }

        /** Runs `nostos --version` and returns the version string, or null. */
        fun getVersion(nostosPath: String): String? {
            return try {
                val process = ProcessBuilder(nostosPath, "--version")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readLine()?.trim()
                process.waitFor()
                if (process.exitValue() == 0 && !output.isNullOrBlank()) output else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
