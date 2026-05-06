package ai.kilocode.client.session.ui.header

import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.TimelineItem
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import kotlin.math.roundToInt
import javax.swing.JPanel

internal class TimelinePanel : JPanel(null) {
    companion object {
        const val HEIGHT = 26
        private const val WIDTH = 12
        private const val MIN = 8
        private const val PAD = 4
        private const val GAP = 1
    }

    private val bars = mutableListOf<TimelineBar>()

    init {
        isOpaque = false
    }

    fun setItems(items: List<TimelineItem>): Boolean {
        val appended = items.size > bars.size
        while (bars.size > items.size) {
            val bar = bars.removeAt(bars.lastIndex)
            remove(bar)
        }
        while (bars.size < items.size) {
            val bar = TimelineBar()
            bars.add(bar)
            add(bar)
        }
        val max = items.maxOfOrNull { it.weight }?.coerceAtLeast(1) ?: 1
        for ((index, item) in items.withIndex()) bars[index].setItem(item, height(item.weight, max))
        val show = items.isNotEmpty()
        if (isVisible != show) isVisible = show
        revalidate()
        repaint()
        return appended
    }

    override fun doLayout() {
        val w = JBUI.scale(WIDTH)
        val gap = JBUI.scale(GAP)
        bars.forEachIndexed { index, bar ->
            val h = bar.barHeight
            val x = index * (w + gap)
            val y = height - h
            bar.setBounds(x, y.coerceAtLeast(0), w, h)
        }
    }

    override fun getPreferredSize(): Dimension = JBUI.size(width(), HEIGHT)

    override fun getMinimumSize(): Dimension = JBUI.size(0, HEIGHT)

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, JBUI.scale(HEIGHT))

    fun count() = bars.size

    fun parts(): List<Content> = bars.map { it.part }

    fun active(index: Int) = bars[index].active

    fun barHeight(index: Int) = bars[index].barHeight

    fun barWidth() = JBUI.scale(WIDTH + GAP)

    private fun height(weight: Int, max: Int): Int {
        val fill = MIN + (weight.toDouble() / max.toDouble()) * (HEIGHT - MIN - PAD)
        return JBUI.scale(fill.roundToInt())
    }

    private fun width(): Int {
        if (bars.isEmpty()) return 0
        return bars.size * WIDTH + (bars.size - 1) * GAP
    }
}
