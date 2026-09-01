plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10" // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm

    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#usage
    id("org.jetbrains.intellij.platform") version "2.18.1" // https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform
    id("org.jetbrains.intellij.platform.grammarkit") version "2.18.1" // https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform.grammarkit

    id("org.jetbrains.kotlinx.kover") version "0.9.9" // https://central.sonatype.com/artifact/org.jetbrains.kotlinx/kover-gradle-plugin/versions
    id("org.babelserver.gradle.test-logger") version "2.1.0" // https://plugins.gradle.org/plugin/org.babelserver.gradle.test-logger
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate(providers.gradleProperty("platformVersion").get())
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2") // For BasePlatformTestCase

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
    compilerOptions {
        // Emit real JVM default methods instead of Kotlin's delegating bridges.
        // Without this, implementing an interface with a deprecated default method
        // (ProjectViewNodeDecorator.decorate(PackageDependenciesNode, ...)) makes
        // the compiler generate a bridge that calls that deprecated method, which
        // the plugin verifier reports as a deprecated-API usage.
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            // 253: DaemonCodeAnalyzer.restart(Object) — the non-deprecated restart
            // overload we call in NostosLspStartupActivity — was introduced in 2025.3.
            sinceBuild = "253"
        }
    }
    signing {
        val home = System.getProperty("user.home")
        certificateChainFile = file("$home/${providers.gradleProperty("signing.certificateChainFile").get()}")
        privateKeyFile = file("$home/${providers.gradleProperty("signing.privateKeyFile").get()}")
        password = providers.gradleProperty("signing.password")
    }
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/sources/jflex"))
            srcDir(layout.buildDirectory.dir("generated/sources/grammar-kit"))
        }
    }
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.GenerateParserTask>("generateParser") {
    sourceFile.set(file("src/main/grammar/Nostos.bnf"))
    targetRootOutputDir.set(layout.buildDirectory.dir("generated/sources/grammar-kit"))
    purgeOldFiles.set(true)
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.GenerateLexerTask>("generateLexer") {
    sourceFile.set(file("src/main/grammar/Nostos.flex"))
    targetRootOutputDir.set(layout.buildDirectory.dir("generated/sources/jflex"))
    purgeOldFiles.set(true)
    dependsOn("generateParser")
}

tasks.named("compileKotlin") {
    dependsOn("generateLexer")
}

tasks.named("compileJava") {
    dependsOn("generateLexer")
}

tasks.test {
    useJUnitPlatform()
    // Load only this plugin (plus the platform) in the test IDE. Since
    // intellij-platform-gradle-plugin 2.18, the test runtime loads all
    // bundled plugins, and the Vue plugin's LSP provider fails its
    // static init in headless tests, making every test that touches VFS fail.
    systemProperty("idea.load.plugins.id", "org.babelserver.intellijnostos")
}

kover {
    reports {
        filters {
            excludes {
                // Coverage is measured on handwritten, unit-testable logic.
                // The classes below are IntelliJ extension points (annotators,
                // contributors, handlers, providers, the startup ProjectActivity)
                // or nostos-lsp transport/process wiring: they can only be
                // exercised meaningfully by integration tests against a running
                // IDE and a live language server, so their line counts would
                // otherwise drown out the logic we can actually test. Pure
                // utility objects with real logic (NostosProjectRoot,
                // NostosMissingManifestNotifier, the wizard scaffold, resolvers)
                // are deliberately NOT excluded and remain covered by unit tests.
                classes(
                    "org.babelserver.intellijnostos.NostosExternalAnnotator*",
                    "org.babelserver.intellijnostos.NostosSemanticHighlighter*",
                    "org.babelserver.intellijnostos.NostosInlayHintsProvider*",
                    "org.babelserver.intellijnostos.NostosLspParameterInfoHandler*",
                    "org.babelserver.intellijnostos.NostosLspDocumentationProvider*",
                    "org.babelserver.intellijnostos.lsp.NostosLspServerManager*",
                    "org.babelserver.intellijnostos.lsp.NostosLspClient*",
                    "org.babelserver.intellijnostos.lsp.NostosLspStartupActivity*",
                    "org.babelserver.intellijnostos.lsp.NostosLspProgressTracker*",
                    "org.babelserver.intellijnostos.lsp.NostosLspRenameHandler*",
                    "org.babelserver.intellijnostos.lsp.NostosWorkspaceSymbolContributor*",
                    "org.babelserver.intellijnostos.lsp.NostosSymbolNavigationItem*",
                    "org.babelserver.intellijnostos.lsp.NostosWorkspaceEdits*",
                    "org.babelserver.intellijnostos.lsp.NostosReferencesCodeVisionProvider*",
                    "org.babelserver.intellijnostos.lsp.NostosHighlightUsagesHandler*",
                    "org.babelserver.intellijnostos.lsp.NostosCallHierarchy*",
                    "org.babelserver.intellijnostos.lsp.NostosCallNodeDescriptor*",
                    "org.babelserver.intellijnostos.lsp.NostosCalleeTreeStructure*",
                    "org.babelserver.intellijnostos.lsp.NostosCallerTreeStructure*",
                    "org.babelserver.intellijnostos.lsp.NostosCodeActionQuickFix*",
                    "org.babelserver.intellijnostos.lsp.NostosFileStatus*",
                    // REPL tool window UI and its server-bound glue; the
                    // response parsers in NostosReplResponses.kt stay covered.
                    "org.babelserver.intellijnostos.lsp.NostosReplToolWindow*",
                    "org.babelserver.intellijnostos.lsp.NostosReplExecuteHandler*",
                    "org.babelserver.intellijnostos.lsp.NostosReplCompletion*",
                    "org.babelserver.intellijnostos.lsp.NostosReplRootType*",
                    // Settings UI (a Configurable) — only exercised by opening
                    // the settings dialogue, not by unit tests.
                    "org.babelserver.intellijnostos.settings.NostosSettingsConfigurable*",
                )
            }
        }
    }
}
