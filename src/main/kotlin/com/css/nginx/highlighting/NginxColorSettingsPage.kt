package com.css.nginx.highlighting

import com.css.nginx.lang.NginxFileType
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

class NginxColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Nginx"
    override fun getIcon() = NginxFileType.icon
    override fun getHighlighter() = NginxSyntaxHighlighter()

    // parameter names are colored by the annotator, so the demo marks them with a tag
    override fun getAdditionalHighlightingTagToDescriptorMap() =
        mapOf("param" to NginxColors.PARAMETER, "re" to NginxColors.REGEX_META, "esc" to NginxColors.REGEX_ESCAPE)

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Directive", NginxColors.DIRECTIVE),
        AttributesDescriptor("String", NginxColors.STRING),
        AttributesDescriptor("Variable", NginxColors.VARIABLE),
        AttributesDescriptor("Parameter name", NginxColors.PARAMETER),
        AttributesDescriptor("Comment", NginxColors.COMMENT),
        AttributesDescriptor("Semicolon", NginxColors.SEMICOLON),
        AttributesDescriptor("Braces", NginxColors.BRACES),
        AttributesDescriptor("Regex//Operator", NginxColors.REGEX_META),
        AttributesDescriptor("Regex//Escape", NginxColors.REGEX_ESCAPE),
    )

    override fun getDemoText() = """
        # main context
        worker_processes auto;
        http {
            proxy_cache_path /var/cache/nginx <param>levels</param>=1:2 <param>keys_zone</param>=static:10m;
            server {
                listen 80;
                location ~* <esc>\\.</esc><re>(</re>css<re>|</re>js<re>)$</re> {
                    expires 30d;
                }
                location / {
                    return 301 "https://${'$'}host${'$'}request_uri";
                }
            }
        }
    """.trimIndent()
}
