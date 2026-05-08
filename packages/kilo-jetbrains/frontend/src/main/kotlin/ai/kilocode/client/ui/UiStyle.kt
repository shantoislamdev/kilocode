package ai.kilocode.client.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.border.Border

/** Static UI tokens and helpers for JetBrains session Swing surfaces. */
object UiStyle {
    object Size {
        const val WIDTH = 350
        const val BUTTON_WIDTH = 28
    }

    object Colors {
        internal const val BORDER_DELTA = 64
        internal const val HOVER_ALPHA = 0.35f

        fun bg(): Color = UIUtil.getPanelBackground()

        fun fg(): Color = UIUtil.getLabelForeground()

        fun weak(): Color = UIUtil.getContextHelpForeground()

        /** Creates a visible separator against editor-derived transcript surfaces. */
        fun line(): Color = JBColor.lazy { contrast(panel(), BORDER_DELTA) }

        fun surface(): Color = panel()

        /** Uses the editor background so chat cards feel native beside editor content. */
        fun panel(): Color = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }

        fun panelHover(): Color = JBColor.lazy { blend(panel(), line(), HOVER_ALPHA) }

        fun header(): Color = panel()

        fun headerBar(): Color = JBUI.CurrentTheme.ToolWindow.headerBackground(false)

        /** Local hover color for collapsible transcript card headers. */
        fun headerHover(): Color = panelHover()

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

    object Insets {
        fun empty(): Border = JBUI.Borders.empty(Gap.pad())

        fun header(): Border = JBUI.Borders.empty(Gap.lg(), Gap.lg())

        fun body(): Border = JBUI.Borders.empty(Gap.lg(), Gap.pad())
    }

    object Borders {
        fun card(): Border = cardBorder()

        fun cardBorder(): Border = JBUI.Borders.customLine(Colors.line(), 1)

        fun cardTop(): Border = JBUI.Borders.customLineTop(Colors.line())

        fun warning(): Border = JBUI.Borders.customLine(Colors.warning(), 1)

        fun picker(): Border = JBUI.Borders.empty(Gap.xs(), Gap.lg())
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
