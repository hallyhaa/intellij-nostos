plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.10" // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm

    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#usage
    id("org.jetbrains.intellij.platform") version "2.13.1" // https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform
    id("org.jetbrains.intellij.platform.grammarkit") version "2.13.1" // https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform.grammarkit

    id("org.jetbrains.kotlinx.kover") version "0.9.7" // https://kotlin.github.io/kotlinx-kover/gradle-plugin/
    id("org.babelserver.gradle.test-logger") version "2.0.0" // https://plugins.gradle.org/plugin/org.babelserver.gradle.test-logger
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
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "251.*"
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
    pathToParser.set("org/babelserver/intellijnostos/parser/NostosParser.java")
    pathToPsiRoot.set("org/babelserver/intellijnostos/psi")
    purgeOldFiles.set(true)
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.GenerateLexerTask>("generateLexer") {
    sourceFile.set(file("src/main/grammar/Nostos.flex"))
    targetOutputDir.set(layout.buildDirectory.dir("generated/sources/jflex/org/babelserver/intellijnostos"))
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
}
