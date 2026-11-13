package com.css.nginx.highlighting

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Any color scheme (bundled or from a theme plugin) must color nginx files through the standard language keys. */
class NginxColorSchemeTest : BasePlatformTestCase() {
    private val keys = NginxColorSettingsPage().attributeDescriptors.map { it.key }

    fun testEveryKeyFallsBackToAStandardLanguageColor() {
        for (key in keys) {
            assertTrue(
                "${key.externalName} has no fallback",
                key.fallbackAttributeKey?.externalName?.startsWith("DEFAULT_") == true
            )
        }
    }

    fun testEveryInstalledSchemeColorsNginxLikeOtherLanguages() {
        val schemes = EditorColorsManager.getInstance().allSchemes
        assertTrue(schemes.isNotEmpty())
        for (scheme in schemes) for (key in keys) {
            assertEquals(
                "${scheme.name}: ${key.externalName}",
                scheme.getAttributes(key.fallbackAttributeKey),
                scheme.getAttributes(key)
            )
        }
    }
}
