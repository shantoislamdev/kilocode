package ai.kilocode.client.session.ui

import ai.kilocode.client.ui.UiStyle
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.border.Border

/** Static UI tokens and helpers for session-specific Swing surfaces. */
object SessionUiStyle {
    object Prompt {
        const val EDITOR_LINES = 3
        const val EDITOR_CHROME = 16
        const val SEND_BUTTON_SIZE = 24
        const val CORNER_ARC = 6
        const val FOCUS_WIDTH = 2
        const val PANEL_VERTICAL_PADDING = 8
        const val PANEL_HORIZONTAL_PADDING = 12
        const val CONTROL_GAP = 4
        const val SHELL_VERTICAL_PADDING = 6
        const val SHELL_HORIZONTAL_PADDING = 8
    }

    object SessionLayout {
        const val GAP = 4
        const val TRANSCRIPT_PADDING = 12
        const val CARD_LINES = 15
        const val REASONING_LINES = 5
        const val CARD_LAYOUT_GAP = 6
        const val CARD_VERTICAL_PADDING = 8
        const val CARD_HORIZONTAL_PADDING = 12
        const val USER_PROMPT_INDENT = 100
        const val SCROLL_CHROME = 16
        const val SCROLL_INCREMENT = 16
    }

    object RecentSessions {
        const val LIMIT = 5
        const val DESCRIPTION_WIDTH = 250
    }

    object Timeline {
        val READ: Color = JBColor(Color(0x37, 0x94, 0xff), Color(0x37, 0x94, 0xff))
        val WRITE: Color = JBColor(Color(0x00, 0x7f, 0xd4), Color(0x00, 0x7f, 0xd4))
        val TOOL: Color = JBColor(Color(0x00, 0x7a, 0xcc), Color(0x00, 0x7a, 0xcc))
        val SUCCESS: Color = JBColor.namedColor("Label.successForeground", UIUtil.getLabelSuccessForeground())
        val ERROR: Color = JBColor(Color(0xf4, 0x87, 0x71), Color(0xf4, 0x87, 0x71))
        val TEXT: Color = JBColor(Color(0x9d, 0x9d, 0x9d), Color(0x9d, 0x9d, 0x9d))
        val STEP: Color = JBColor(Color(0x4d, 0x4d, 0x4d), Color(0x4d, 0x4d, 0x4d))
    }
}

object Dock {
    fun banner(): Border = JBUI.Borders.compound(
        JBUI.Borders.customLineTop(UiStyle.Colors.line()),
        JBUI.Borders.empty(UiStyle.Gap.small(), UiStyle.Gap.lg(), 0, UiStyle.Gap.lg()),
    )!!

    fun neutral(): Border = JBUI.Borders.compound(
        JBUI.Borders.customLine(UiStyle.Colors.line(), 1),
        JBUI.Borders.empty(UiStyle.Gap.lg(), UiStyle.Gap.pad()),
    )!!

    fun warning(): Border = JBUI.Borders.compound(
        UiStyle.Borders.warning(),
        JBUI.Borders.empty(UiStyle.Gap.lg(), UiStyle.Gap.pad()),
    )!!
}
