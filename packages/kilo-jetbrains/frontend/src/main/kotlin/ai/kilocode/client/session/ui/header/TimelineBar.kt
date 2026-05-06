package ai.kilocode.client.session.ui.header

import ai.kilocode.client.session.model.Compaction
import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Reasoning
import ai.kilocode.client.session.model.Text
import ai.kilocode.client.session.model.TimelineItem
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.ToolKind
import ai.kilocode.client.ui.UiStyle
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.JPanel

internal class TimelineBar : JPanel() {
    lateinit var part: Content
        private set
    var active: Boolean = false
        private set
    var barHeight: Int = JBUI.scale(8)
        private set

    init {
        isOpaque = true
    }

    fun setItem(item: TimelineItem, height: Int) {
        part = item.part
        active = item.active
        toolTipText = item.title
        barHeight = height
        background = color(item)
    }

    private fun color(item: TimelineItem): Color {
        val part = item.part
        if (part is Tool && part.state == ToolExecState.ERROR) return UiStyle.Colors.timelineError
        if (part is Text) return UiStyle.Colors.timelineText
        if (part is Reasoning) return UiStyle.Colors.timelineText
        if (part is Compaction) return UiStyle.Colors.timelineStep
        if (part !is Tool) return UiStyle.Colors.timelineStep
        if (part.kind == ToolKind.READ) return UiStyle.Colors.timelineRead
        if (part.kind == ToolKind.WRITE) return UiStyle.Colors.timelineWrite
        return UiStyle.Colors.timelineTool
    }
}
