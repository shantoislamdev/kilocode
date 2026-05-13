package ai.kilocode.client.session.scroll

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.util.SVGLoader
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.Icon

private const val OPAQUE_ALPHA = 255

internal object ScrollButtonIcon {
    private val icon = IconLoader.getIcon("/icons/scroll-bottom.svg", ScrollButtonIcon::class.java)

    fun create(): Icon = icon.colorizeIfPossible(
        fillColor = JBUI.CurrentTheme.Button.defaultButtonColorStart(),
        borderColor = JBUI.CurrentTheme.Button.defaultButtonForeground(),
    )
}

private fun Icon.colorizeIfPossible(fillColor: Color, borderColor: Color = fillColor): Icon =
    (this as? CachedImageIcon)?.createWithPatcher(colorPatcher = object : SVGLoader.SvgElementColorPatcherProvider, SvgAttributePatcher {
        private val digest = longArrayOf(0L, 440413911775177385)

        override fun digest(): LongArray {
            digest[0] = toLong(fillColor.rgb, borderColor.rgb)
            return digest
        }

        override fun patchColors(attributes: MutableMap<String, String>) {
            when (attributes["id"]) {
                "ScrollButton.Background" -> setAttribute(attributes, "fill", fillColor)
                "ScrollButton.Foreground" -> setAttribute(attributes, "stroke", borderColor)
            }
        }

        override fun attributeForPath(path: String) = this

        private fun setAttribute(attributes: MutableMap<String, String>, key: String, color: Color) {
            if (!attributes.containsKey(key) || attributes[key] == "none") return
            attributes[key] = "rgb(${color.red},${color.green},${color.blue})"
            val alpha = color.alpha
            if (alpha != OPAQUE_ALPHA) {
                attributes["$key-opacity"] = "${alpha / OPAQUE_ALPHA.toFloat()}"
            }
        }

        private fun toLong(high: Int, low: Int): Long {
            return (high.toLong() shl 32) or (low.toLong() and 0xFFFFFFFFL)
        }
    }) ?: this
