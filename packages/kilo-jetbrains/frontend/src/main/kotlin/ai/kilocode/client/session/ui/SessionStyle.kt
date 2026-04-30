package ai.kilocode.client.session.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.border.Border
import kotlin.math.roundToInt

/** Shared styling tokens for JetBrains session Swing surfaces. */
object SessionStyle {
    object Size {
        const val WIDTH = 350
        const val LIMIT = 5
        const val LINES = 3
        const val CHROME = 16
        const val BUTTON_WIDTH = 28
        const val BUTTON = 24
        const val SCROLL = 16
    }

    object Space {
        const val XS = 2
        const val SM = 4
        const val MD = 6
        const val LG = 8
        const val XL = 10
        const val PAD = 12
        const val LOGO = 14
        const val RECENT = 28
    }

    object Colors {
        fun bg(): Color = UIUtil.getPanelBackground()

        fun fg(): Color = UIUtil.getLabelForeground()

        fun weak(): Color = UIUtil.getContextHelpForeground()

        fun line(): Color = JBColor.border()

        fun surface(): Color = JBColor.lazy {
            UIManager.getColor("TextField.background") ?: UIUtil.getPanelBackground()
        }

        fun error(): Color = JBColor.namedColor("Label.errorForeground", UIUtil.getErrorForeground())

        fun warning(): Color = JBColor.lazy {
            UIManager.getColor("Component.warningFocusColor")
                ?: UIManager.getColor("Label.warningForeground")
                ?: UIUtil.getContextHelpForeground()
        }

        fun running(): Color = JBColor.namedColor("ProgressBar.foreground", UIUtil.getLabelForeground())
    }

    object Insets {
        fun transcript(): Border = JBUI.Borders.empty(Space.PAD, Space.PAD)

        fun empty(): Border = JBUI.Borders.empty(Space.PAD)

        fun prompt(): Border = JBUI.Borders.empty(Space.LG, Space.PAD, Space.LG, Space.PAD)

        fun header(): Border = JBUI.Borders.empty(Space.LG, Space.XL)

        fun body(): Border = JBUI.Borders.empty(Space.LG, Space.XL)

        fun progress(): Border = JBUI.Borders.empty(Space.MD, 0, Space.SM, 0)
    }

    object Borders {
        fun card(): Border = JBUI.Borders.customLine(Colors.line(), 1)

        fun cardTop(): Border = JBUI.Borders.customLineTop(Colors.line())

        fun warning(): Border = JBUI.Borders.customLine(Colors.warning(), 1)

        fun picker(): Border = JBUI.Borders.compound(
            RoundedLineBorder(Colors.line(), JBUI.scale(Space.MD)),
            JBUI.Borders.empty(Space.XS, Space.LG),
        )!!

        fun user(): Border = JBUI.Borders.compound(
            JBUI.Borders.customLineTop(Colors.line()),
            JBUI.Borders.empty(Space.LG, 0, Space.SM, 0),
        )!!

        fun assistant(): Border = JBUI.Borders.empty(Space.SM, 0)
    }

    object Dock {
        fun banner(): Border = JBUI.Borders.compound(
            JBUI.Borders.customLineTop(Colors.line()),
            JBUI.Borders.empty(Space.SM, Space.LG, 0, Space.LG),
        )!!

        fun neutral(): Border = JBUI.Borders.compound(
            JBUI.Borders.customLine(Colors.line(), 1),
            JBUI.Borders.empty(Space.LG, Space.PAD),
        )!!

        fun warning(): Border = JBUI.Borders.compound(
            Borders.warning(),
            JBUI.Borders.empty(Space.LG, Space.PAD),
        )!!
    }

    object Gap {
        fun inline() = JBUI.scale(Space.MD)

        fun regular() = JBUI.scale(Space.LG)

        fun small() = JBUI.scale(Space.SM)

        fun turn() = JBUI.scale(Space.PAD)

        fun part() = JBUI.scale(Space.SM)

        fun scroll() = JBUI.scale(Size.SCROLL)

        fun layout(gap: Int = Space.LG) = BorderLayout(JBUI.scale(gap), 0)
    }

    object Fonts {
        private fun scheme() = EditorColorsManager.getInstance().globalScheme

        /** Editor font size is the accessibility baseline for all session chat text. */
        fun editorSize(): Int = scheme().editorFontSize

        /** Editor font family is used for transcript text, reasoning text, and tool output. */
        fun editorFamily(): String = scheme().editorFontName

        /** Plain editor-family font for user prompts, assistant text, reasoning, and tool output. */
        fun transcriptFont(): Font = editorFont(Font.PLAIN, editorSize())

        /** Editor-family font scaled down from the editor size for secondary transcript chrome. */
        fun smallEditorFont(): Font = editorFont(Font.PLAIN, scaledSize(JBFont.small()))

        /** Bold editor-family font at editor size for primary tool and reasoning labels. */
        fun boldEditorFont(): Font = editorFont(Font.BOLD, editorSize())

        /** IDE UI label font family resized to the editor font size for session chrome. */
        fun uiFont(): Font = JBUI.Fonts.label().deriveFont(editorSize().toFloat())

        /** IDE UI small font proportions scaled from the editor font size baseline. */
        fun smallUiFont(): Font {
            val font = JBFont.small()
            return font.deriveFont(scaledSize(font).toFloat())
        }

        /** Bold IDE UI font resized to the editor font size for prominent chrome labels. */
        fun boldUiFont(): Font = uiFont().deriveFont(Font.BOLD)

        /** Apply the global editor scheme to embedded editor components used by the chat UI. */
        fun applyToEditor(editor: EditorEx) {
            editor.setColorsScheme(scheme())
            editor.setFontSize(editorSize())
        }

        private fun editorFont(style: Int, size: Int): Font = Font(editorFamily(), style, size)

        private fun scaledSize(font: Font): Int {
            val base = JBUI.Fonts.label().size.coerceAtLeast(1)
            val ratio = font.size.toFloat() / base
            return (editorSize() * ratio).roundToInt().coerceAtLeast(1)
        }
    }

    object Buttons {
        fun icon(button: JButton) {
            button.isFocusable = false
            button.setRequestFocusEnabled(false)
            button.isContentAreaFilled = false
            button.isBorderPainted = false
            button.isOpaque = false
            button.border = JBUI.Borders.empty()
        }
    }

    object Components {
        fun transparent(component: JComponent) {
            component.isOpaque = false
        }
    }
}
