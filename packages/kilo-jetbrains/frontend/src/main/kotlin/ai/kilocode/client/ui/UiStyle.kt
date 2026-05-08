package ai.kilocode.client.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.UIManager

/** Static UI tokens and helpers for JetBrains session Swing surfaces. */
object UiStyle {
    object Size {
        const val WIDTH = 350
        const val BUTTON_WIDTH = 28
    }

    object Colors {
        fun bg(): Color = UIUtil.getPanelBackground()

        fun fg(): Color = UIUtil.getLabelForeground()

        fun weak(): Color = UIUtil.getContextHelpForeground()

        /** Uses the editor background so chat cards feel native beside editor content. */
        fun panel(): Color = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }

        fun headerBar(): Color = JBUI.CurrentTheme.ToolWindow.headerBackground(false)

        fun error(): Color = JBColor.namedColor("Label.errorForeground", UIUtil.getErrorForeground())

        fun warning(): Color = JBColor.lazy {
            UIManager.getColor("Component.warningFocusColor")
                ?: UIManager.getColor("Label.warningForeground")
                ?: UIUtil.getContextHelpForeground()
        }

        fun running(): Color = JBColor.namedColor("ProgressBar.foreground", UIUtil.getLabelForeground())

        fun picker(): Color = JBColor.lazy {
            UIManager.getColor("ComboBoxButton.background")
                ?: UIManager.getColor("ComboBox.nonEditableBackground")
                ?: UIUtil.getPanelBackground()
        }

        fun pickerHover(): Color = JBUI.CurrentTheme.ActionButton.hoverBackground()

        internal fun contrast(base: Color, delta: Int): Color {
            val step = if (bright(base)) -delta else delta
            return Color(
                (base.red + step).coerceIn(0, 255),
                (base.green + step).coerceIn(0, 255),
                (base.blue + step).coerceIn(0, 255),
                base.alpha,
            )
        }

        internal fun blend(base: Color, over: Color, alpha: Float): Color {
            val inv = 1f - alpha
            return Color(
                (base.red * inv + over.red * alpha).toInt().coerceIn(0, 255),
                (base.green * inv + over.green * alpha).toInt().coerceIn(0, 255),
                (base.blue * inv + over.blue * alpha).toInt().coerceIn(0, 255),
                base.alpha,
            )
        }

        internal fun bright(color: Color): Boolean =
            (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) >= 128
    }

    object Borders {
        fun warning() = JBUI.Borders.customLine(Colors.warning(), 1)

        fun picker() = JBUI.Borders.empty(Gap.xs(), Gap.lg())
    }

    object Gap {
        fun xs() = JBUI.scale(2)

        fun md() = JBUI.scale(6)

        fun lg() = JBUI.scale(8)

        fun small() = JBUI.scale(4)

        fun pad() = JBUI.scale(12)

        fun layout(gap: Int = lg()) = BorderLayout(gap, 0)
    }

    object Components {
        fun transparent(component: JComponent) {
            component.isOpaque = false
        }
    }
}
