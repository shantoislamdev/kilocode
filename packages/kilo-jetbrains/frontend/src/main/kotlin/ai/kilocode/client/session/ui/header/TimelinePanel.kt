package ai.kilocode.client.session.ui.header

import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Compaction
import ai.kilocode.client.session.model.Reasoning
import ai.kilocode.client.session.model.Text
import ai.kilocode.client.session.model.TimelineItem
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.ToolKind
import ai.kilocode.client.ui.UiStyle
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import kotlin.math.roundToInt
import javax.swing.JPanel

internal class TimelinePanel : JPanel() {
    companion object {
        const val HEIGHT = 26
        private const val WIDTH = 12
        private const val MIN = 8
        private const val PAD = 4
        private const val GAP = 1
    }

    private var items: List<TimelineItem> = emptyList()
    private var heights: List<Int> = emptyList()

    init {
        isOpaque = false
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                toolTipText = item(event)?.title
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(event: MouseEvent) {
                toolTipText = null
            }
        })
    }

    fun setItems(items: List<TimelineItem>): Boolean {
        val appended = items.size > this.items.size
        val max = items.maxOfOrNull { it.weight }?.coerceAtLeast(1) ?: 1
        this.items = items
        heights = items.map { height(it.weight, max) }
        val show = items.isNotEmpty()
        if (isVisible != show) isVisible = show
        revalidate()
        repaint()
        return appended
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        val w = JBUI.scale(WIDTH)
        val gap = JBUI.scale(GAP)
        val tall = height.takeIf { it > 0 } ?: preferredSize.height
        try {
            for (idx in items.indices) {
                val h = heights[idx]
                val x = idx * (w + gap)
                val y = (tall - h).coerceAtLeast(0)
                g2.color = color(items[idx])
                g2.fillRect(x, y, w, h)
            }
        } finally {
            g2.dispose()
        }
    }

    private fun item(event: MouseEvent): TimelineItem? {
        val w = JBUI.scale(WIDTH)
        val gap = JBUI.scale(GAP)
        val tall = height.takeIf { it > 0 } ?: preferredSize.height
        for (idx in items.indices) {
            val h = heights[idx]
            val x = idx * (w + gap)
            val y = (tall - h).coerceAtLeast(0)
            val inside = event.x >= x && event.x < x + w && event.y >= y && event.y < y + h
            if (inside) return items[idx]
        }
        return null
    }

    override fun getPreferredSize(): Dimension = JBUI.size(width(), HEIGHT)

    override fun getMinimumSize(): Dimension = JBUI.size(0, HEIGHT)

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, JBUI.scale(HEIGHT))

    fun count() = items.size

    fun parts(): List<Content> = items.map { it.part }

    fun active(index: Int) = items[index].active

    fun barHeight(index: Int) = heights[index]

    fun barWidth() = JBUI.scale(WIDTH + GAP)

    private fun height(weight: Int, max: Int): Int {
        val fill = MIN + (weight.toDouble() / max.toDouble()) * (HEIGHT - MIN - PAD)
        return JBUI.scale(fill.roundToInt())
    }

    private fun width(): Int {
        if (items.isEmpty()) return 0
        return items.size * WIDTH + (items.size - 1) * GAP
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
