package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Compaction
import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.ContextUsage
import ai.kilocode.client.session.model.Reasoning
import ai.kilocode.client.session.model.SessionHeaderSnapshot
import ai.kilocode.client.session.model.SessionModelEvent
import ai.kilocode.client.session.model.Text
import ai.kilocode.client.session.model.TimelineItem
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.ToolKind
import ai.kilocode.client.session.update.SessionController
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.TokensDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import kotlin.math.roundToInt
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class SessionHeaderPanel(
    private val controller: SessionController,
    parent: Disposable,
) : BorderLayoutPanel(), SessionStyleTarget {

    companion object {
        private val COMPRESS_ICON: Icon = IconLoader.getIcon("/icons/compress.svg", SessionHeaderPanel::class.java)
        private val CHEVRON_ICON: Icon = IconLoader.getIcon("/icons/chevron-down.svg", SessionHeaderPanel::class.java)
        private val CHEVRON_UP_ICON: Icon = RotatedIcon(CHEVRON_ICON)
        private val UP_ICON: Icon = IconLoader.getIcon("/icons/arrow-up.svg", SessionHeaderPanel::class.java)
        private val DOWN_ICON: Icon = IconLoader.getIcon("/icons/arrow-down-to-line.svg", SessionHeaderPanel::class.java)
    }

    private val title = JBLabel()
    private val cost = JBLabel()
    private val context = JBLabel()
    private val todos = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    private val compact = UiStyle.Buttons.HoverIcon().apply {
        icon = COMPRESS_ICON
        toolTipText = KiloBundle.message("session.header.compact.description")
        accessibleContext.accessibleName = KiloBundle.message("session.header.compact")
        addActionListener { controller.compact() }
    }
    private val expand = UiStyle.Buttons.HoverIcon().apply {
        icon = CHEVRON_ICON
        toolTipText = KiloBundle.message("session.header.expand")
        accessibleContext.accessibleName = KiloBundle.message("session.header.expand")
        addActionListener { toggle() }
    }
    private val timeline = TimelinePanel()
    private val scroll = JBScrollPane(timeline).apply {
        border = JBUI.Borders.empty()
        viewportBorder = JBUI.Borders.empty()
        isOpaque = false
        viewport.isOpaque = false
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        preferredSize = JBUI.size(0, TimelinePanel.HEIGHT)
        minimumSize = preferredSize
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
    private val bar = ContextBar()
    private val tokenTitle = JBLabel(KiloBundle.message("session.header.tokens")).apply { foreground = UiStyle.Colors.weak() }
    private val input = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
        icon = UP_ICON
        iconTextGap = UiStyle.Gap.xs()
    }
    private val output = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
        icon = DOWN_ICON
        iconTextGap = UiStyle.Gap.xs()
    }
    private val cacheRead = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
        icon = DOWN_ICON
        iconTextGap = UiStyle.Gap.xs()
    }
    private val cacheWrite = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
        icon = UP_ICON
        iconTextGap = UiStyle.Gap.xs()
    }
    private val top = BorderLayoutPanel()
    private val right = JPanel(FlowLayout(FlowLayout.RIGHT, UiStyle.Gap.inline(), 0)).apply {
        isOpaque = false
        add(cost)
        add(context)
        add(compact)
        add(expand)
    }
    private val tokens = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.inline(), 0)).apply {
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Space.SM, 0, 0, 0)
        add(tokenTitle)
        add(input)
        add(output)
        add(cacheWrite)
        add(cacheRead)
    }
    private val todoRow = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.inline(), 0)).apply {
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Space.SM, 0, 0, 0)
        add(todos)
    }
    private val body = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(UiStyle.Space.SM, 0, 0, 0)
        add(scroll)
        add(bar)
        add(tokens)
        add(todoRow)
    }
    private var style = SessionStyle.current()

    init {
        isOpaque = true
        background = UiStyle.Colors.headerBar()
        updateUI()

        top.add(title, BorderLayout.CENTER)
        top.add(right, BorderLayout.EAST)
        add(top, BorderLayout.NORTH)

        controller.model.addListener(parent) { event ->
            when (event) {
                is SessionModelEvent.HeaderUpdated -> update(event.header)

                is SessionModelEvent.MessageAdded,
                is SessionModelEvent.MessageUpdated,
                is SessionModelEvent.MessageRemoved,
                is SessionModelEvent.ContentAdded,
                is SessionModelEvent.ContentUpdated,
                is SessionModelEvent.ContentRemoved,
                is SessionModelEvent.ContentDelta,
                is SessionModelEvent.StateChanged,
                is SessionModelEvent.DiffUpdated,
                is SessionModelEvent.TodosUpdated,
                is SessionModelEvent.SessionUpdated,
                is SessionModelEvent.Compacted,
                is SessionModelEvent.HistoryLoaded,
                is SessionModelEvent.Cleared,
                is SessionModelEvent.TurnAdded,
                is SessionModelEvent.TurnUpdated,
                is SessionModelEvent.TurnRemoved -> Unit
            }
        }

        applyStyle(style)
        update(controller.model.header)
    }

    override fun updateUI() {
        super.updateUI()
        background = UiStyle.Colors.headerBar()
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 1, 0),
            JBUI.Borders.empty(UiStyle.Space.LG, UiStyle.Space.PAD, UiStyle.Space.SM, UiStyle.Space.PAD),
        )
    }

    fun update(header: SessionHeaderSnapshot) {
        val before = isVisible
        title.text = header.title
        title.toolTipText = header.title
        title.accessibleContext.accessibleName = header.title
        isVisible = header.visible
        if (!header.visible) {
            collapse()
            if (before) refresh()
            return
        }

        set(cost, money(header.cost))
        set(context, contextText(header.context))
        context.toolTipText = contextTip(header.context)
        setTokens(header.tokens)
        set(todos, todo(header.todos.completed, header.todos.total))
        todoRow.isVisible = todos.isVisible

        compact.isEnabled = header.canCompact
        val appended = timeline.setItems(header.timeline)
        if (scroll.isVisible != timeline.isVisible) scroll.isVisible = timeline.isVisible
        if (appended) SwingUtilities.invokeLater { scroll.horizontalScrollBar.value = scroll.horizontalScrollBar.maximum }
        bar.setUsage(header.context)
        refresh()
    }

    override fun applyStyle(style: SessionStyle) {
        this.style = style
        title.font = style.boldUiFont
        cost.font = style.uiFont
        context.font = style.uiFont
        todos.font = style.smallUiFont
        tokenTitle.font = style.smallUiFont
        input.font = style.smallUiFont
        output.font = style.smallUiFont
        cacheRead.font = style.smallUiFont
        cacheWrite.font = style.smallUiFont
        bar.applyStyle(style)
        refresh()
    }

    internal fun titleText(): String = title.text

    internal fun costText(): String = cost.text

    internal fun contextText(): String = context.text

    internal fun tokenText(): String = listOf(tokenTitle, input, output, cacheWrite, cacheRead)
        .filter { it.isVisible }
        .joinToString(" ") { it.text }

    internal fun inputTokenText(): String = input.text

    internal fun outputTokenText(): String = output.text

    internal fun cacheReadText(): String = cacheRead.text

    internal fun cacheWriteText(): String = cacheWrite.text

    internal fun todoText(): String = todos.text

    internal fun todoVisible() = todoRow.isVisible && todos.isVisible

    internal fun compactButton() = compact

    internal fun expandButton() = expand

    internal fun isExpanded() = body.parent === this

    internal fun bodyPanel() = body

    internal fun timelinePanel(): Component = timeline

    internal fun contextBar(): Component = bar

    internal fun contextBarVisible() = bar.isVisible

    internal fun contextBarUsed() = bar.values()?.used

    internal fun contextBarReserved() = bar.values()?.reserved

    internal fun contextBarAvailable() = bar.values()?.available

    internal fun contextBarLimit() = bar.values()?.limit

    internal fun contextBarTip() = bar.toolTipText

    internal fun contextBarTrackColor() = bar.trackColor()

    internal fun contextBarUsedColor() = bar.usedColor()

    internal fun contextBarReservedColor() = bar.reservedColor()

    internal fun timelineCount() = timeline.count()

    internal fun timelineParts() = timeline.parts()

    internal fun timelineActive(index: Int) = timeline.active(index)

    internal fun timelinePreferredSize() = timeline.preferredSize

    internal fun timelineBarHeight(index: Int) = timeline.barHeight(index)

    internal fun timelineBarWidth() = timeline.barWidth()

    internal fun timelineScrollPreferredSize() = scroll.preferredSize

    internal fun expandTip() = expand.toolTipText

    private fun setTokens(value: TokensDto?) {
        val tk = value
        val sent = tk?.input ?: 0L
        val received = (tk?.output ?: 0L) + (tk?.reasoning ?: 0L)
        val read = tk?.cacheRead ?: 0L
        val write = tk?.cacheWrite ?: 0L
        val total = sent + received + read + write

        tokenTitle.text = KiloBundle.message("session.header.tokens")
        tokenTitle.isVisible = total > 0
        set(input, if (sent > 0) num(sent) else null)
        set(output, if (received > 0) num(received) else null)
        set(cacheRead, if (read > 0) KiloBundle.message("session.header.cache.read", num(read)) else null)
        set(cacheWrite, if (write > 0) KiloBundle.message("session.header.cache.write", num(write)) else null)
        tokens.isVisible = total > 0
    }

    private fun toggle() {
        if (isExpanded()) collapse() else expand()
        refresh()
    }

    private fun expand(): Boolean {
        if (isExpanded()) return false
        add(body, BorderLayout.CENTER)
        setExpand(true)
        return true
    }

    private fun collapse(): Boolean {
        val attached = body.parent === this
        if (!attached) return false
        remove(body)
        setExpand(false)
        return attached
    }

    private fun setExpand(expanded: Boolean) {
        val key = if (expanded) "session.header.collapse" else "session.header.expand"
        expand.icon = if (expanded) CHEVRON_UP_ICON else CHEVRON_ICON
        expand.toolTipText = KiloBundle.message(key)
        expand.accessibleContext.accessibleName = KiloBundle.message(key)
    }

    private fun refresh() {
        revalidate()
        repaint()
    }
}

private fun set(label: JBLabel, value: String?) {
    val text = value.orEmpty()
    if (label.text != text) label.text = text
    val show = text.isNotEmpty()
    if (label.isVisible != show) label.isVisible = show
}

private fun money(value: Double?): String? {
    val cost = value ?: return null
    if (cost < 0.01) return "\$%.4f".format(cost)
    if (cost < 1.0) return "\$%.2f".format(cost)
    return "\$%.2f".format(cost)
}

private fun contextText(value: ContextUsage?): String? {
    val ctx = value ?: return null
    val pct = ctx.percentage
    if (pct != null) return "$pct%"
    if (ctx.tokens > 0) return num(ctx.tokens)
    return null
}

private fun contextTip(value: ContextUsage?): String? {
    val ctx = value ?: return null
    val pct = ctx.percentage
    if (pct != null) return KiloBundle.message("session.header.context.tooltip.percent", num(ctx.tokens), pct)
    if (ctx.tokens > 0) return KiloBundle.message("session.header.context.tooltip.tokens", num(ctx.tokens))
    return null
}

private fun todo(done: Int, total: Int): String? {
    if (total <= 0) return null
    if (done >= total) return KiloBundle.message("session.header.todos.done", total)
    return KiloBundle.message("session.header.todos.progress", done, total)
}

private fun num(value: Long): String {
    val abs = kotlin.math.abs(value)
    if (abs < 1_000) return value.toString()
    if (abs < 1_000_000) return "%.1fK".format(value / 1_000.0)
    return "%.1fM".format(value / 1_000_000.0)
}

private class ContextBar : JPanel(BorderLayout(UiStyle.Gap.inline(), 0)) {
    private val used = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    private val limit = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    private val meter = Meter()

    init {
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Space.SM, 0, 0, 0)
        add(used, BorderLayout.WEST)
        add(meter, BorderLayout.CENTER)
        add(limit, BorderLayout.EAST)
    }

    fun setUsage(value: ContextUsage?) {
        val data = data(value)
        meter.data = data
        isVisible = data != null
        used.text = data?.used?.let(::num).orEmpty()
        limit.text = data?.limit?.let(::num).orEmpty()
        toolTipText = data?.tip()
        meter.toolTipText = toolTipText
        revalidate()
        repaint()
    }

    fun applyStyle(style: SessionStyle) {
        used.font = style.smallUiFont
        limit.font = style.smallUiFont
    }

    fun values(): ContextData? = meter.data

    fun trackColor(): Color = meter.trackColor()

    fun usedColor(): Color = meter.data?.let(meter::usedColor) ?: meter.usedColor()

    fun reservedColor(): Color = meter.reservedColor()

    private fun data(value: ContextUsage?): ContextData? {
        val ctx = value ?: return null
        val max = ctx.limit?.takeIf { it > 0 } ?: return null
        if (ctx.tokens <= 0) return null
        val used = ctx.tokens.coerceAtMost(max)
        val output = ctx.output?.takeIf { it > 0 } ?: 0L
        val reserved = output.coerceAtMost(max - used)
        val available = (max - used - reserved).coerceAtLeast(0)
        return ContextData(used, reserved, available, max, output)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

private data class ContextData(
    val used: Long,
    val reserved: Long,
    val available: Long,
    val limit: Long,
    val output: Long,
) {
    fun tip(): String {
        val lines = mutableListOf(KiloBundle.message("session.header.context.used", num(used), num(limit)))
        if (output > 0) lines.add(KiloBundle.message("session.header.context.reserved", num(output)))
        if (available > 0) lines.add(KiloBundle.message("session.header.context.available", num(available)))
        return lines.joinToString("\n")
    }
}

private class Meter : JComponent() {
    companion object {
        private val HOT = JBColor.namedColor("Kilo.ContextProgress.hotBase", Color(128, 0, 0))
    }

    var data: ContextData? = null

    init {
        isOpaque = false
        preferredSize = JBUI.size(80, 4)
        minimumSize = JBUI.size(24, 4)
    }

    override fun paintComponent(g: Graphics) {
        val data = data ?: return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val h = JBUI.scale(4).coerceAtMost(height).coerceAtLeast(1)
            val y = (height - h) / 2
            val arc = JBUI.scale(4)
            g2.color = trackColor()
            g2.fillRoundRect(0, y, width, h, arc, arc)
            val used = segment(data.used, data.limit)
            val reserved = segment(data.reserved, data.limit)
            fill(g2, 0, y, used, h, usedColor(data), arc)
            fill(g2, used, y, reserved, h, reservedColor(), arc)
        } finally {
            g2.dispose()
        }
    }

    private fun segment(value: Long, limit: Long): Int {
        if (value <= 0 || limit <= 0 || width <= 0) return 0
        return ((value.toDouble() / limit.toDouble()) * width).toInt().coerceIn(0, width)
    }

    private fun fill(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, color: Color, arc: Int) {
        if (w <= 0) return
        g.color = color
        g.fillRoundRect(x, y, w, h, arc, arc)
    }

    fun trackColor(): Color = UIUtil.getBoundsColor()

    fun usedColor(): Color = UiStyle.Colors.fg()

    fun usedColor(data: ContextData): Color {
        if (data.used.toDouble() / data.limit.toDouble() >= 0.5) return UiStyle.Colors.blend(HOT, UiStyle.Colors.error(), 0.6f)
        return usedColor()
    }

    fun reservedColor(): Color = UiStyle.Colors.weak()
}

private class RotatedIcon(private val base: Icon) : Icon {
    override fun getIconWidth(): Int = base.iconWidth

    override fun getIconHeight(): Int = base.iconHeight

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            val tx = AffineTransform()
            tx.translate((x + iconWidth / 2.0), (y + iconHeight / 2.0))
            tx.rotate(Math.PI)
            tx.translate((-iconWidth / 2.0), (-iconHeight / 2.0))
            g2.transform(tx)
            base.paintIcon(c, g2, 0, 0)
        } finally {
            g2.dispose()
        }
    }
}

private class TimelinePanel : JPanel(null) {
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

    fun parts() = bars.map { it.part }

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

private class TimelineBar : JPanel() {
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
