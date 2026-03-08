plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.10" // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
    id("org.jetbrains.intellij.platform") version "2.12.0" // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#usage
    id("org.babelserver.gradle.test-logger") version "2.0.0"
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
    }
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
    }
}

tasks.test {
    useJUnitPlatform()
}
