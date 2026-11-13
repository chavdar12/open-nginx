import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.2.4")
        testFramework(TestFrameworkType.Platform)
        testBundledModule("intellij.spellchecker")
        testBundledPlugin("tanvd.grazi") // spellcheck inspection lives in Grazie
        testPlugin(
            "com.cppcxy.Intellij-EmmyLua",
            "0.24.0.115-IDEA262"
        ) // EmmyLua2: tests the optional Lua injection against a real Lua plugin
        testPlugin(
            "com.redhat.devtools.lsp4ij",
            "0.21.0"
        ) // EmmyLua2 needs it, or the platform silently disables EmmyLua2

        // Add plugin dependencies for compilation here, for example:
        // bundledPlugin("com.intellij.java")
    }
}
