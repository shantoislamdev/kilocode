package ai.kilocode.client.session.ui.header

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.SessionHeaderSnapshot
import ai.kilocode.client.session.model.SessionModelEvent
import ai.kilocode.client.session.ui.SessionStyle
import ai.kilocode.client.session.ui.SessionStyleTarget
import ai.kilocode.client.session.update.SessionController
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.TokensDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
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
    private val todos = JBLabel()
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
    private val tokenTitle = JBLabel(KiloBundle.message("session.header.tokens"))
    private val input = JBLabel().apply {
        icon = UP_ICON
        iconTextGap = UiStyle.Gap.xs()
    }
    private val output = JBLabel().apply {
        icon = DOWN_ICON
        iconTextGap = UiStyle.Gap.xs()
    }
    private val cacheRead = JBLabel().apply {
        icon = DOWN_ICON
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
    private val tokens = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Space.SM, 0, 0, 0)
        add(tokenTitle)
        add(Box.createHorizontalStrut(UiStyle.Gap.inline()))
        add(input)
        add(Box.createHorizontalStrut(UiStyle.Gap.small()))
        add(output)
        add(Box.createHorizontalStrut(UiStyle.Gap.small()))
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
        add(tokens)
        add(bar)
        add(todoRow)
    }
    private var style = SessionStyle.current()

    init {
        isOpaque = true
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
        background = style.editorBackground
        foreground = style.editorForeground
        top.background = style.editorBackground
        right.background = style.editorBackground
        tokens.background = style.editorBackground
        todoRow.background = style.editorBackground
        body.background = style.editorBackground
        scroll.background = style.editorBackground
        scroll.viewport.background = style.editorBackground
        title.font = style.boldUiFont
        title.foreground = style.editorForeground
        cost.font = style.uiFont
        cost.foreground = style.editorForeground
        context.font = style.uiFont
        context.foreground = style.editorForeground
        todos.font = style.smallUiFont
        todos.foreground = style.editorForeground
        tokenTitle.font = style.smallUiFont
        tokenTitle.foreground = style.editorForeground
        input.font = style.smallUiFont
        input.foreground = style.editorForeground
        output.font = style.smallUiFont
        output.foreground = style.editorForeground
        cacheRead.font = style.smallUiFont
        cacheRead.foreground = style.editorForeground
        bar.applyStyle(style)
        refresh()
    }

    internal fun titleText(): String = title.text

    internal fun costText(): String = cost.text

    internal fun contextText(): String = context.text

    internal fun foregrounds() = listOf(title, cost, context, todos, tokenTitle, input, output, cacheRead)
        .map { it.foreground }

    internal fun tokenText(): String = listOf(tokenTitle, input, output, cacheRead)
        .filter { it.isVisible }
        .joinToString(" ") { it.text }

    internal fun tokenTip(): String = tokens.toolTipText

    internal fun inputTokenText(): String = input.text

    internal fun outputTokenText(): String = output.text

    internal fun cacheReadText(): String = cacheRead.text

    internal fun todoText(): String = todos.text

    internal fun todoVisible() = todoRow.isVisible && todos.isVisible

    internal fun compactButton() = compact

    internal fun expandButton() = expand

    internal fun isExpanded() = body.parent === this

    internal fun bodyPanel() = body

    internal fun bodyComponents() = body.components.toList()

    internal fun timelineScroll() = scroll

    internal fun tokenPanel() = tokens

    internal fun timelinePanel(): Component = timeline

    internal fun timelineToolTip() = timeline.toolTipText

    internal fun timelineHover() = timeline.hovered()

    internal fun contextBar(): Component = bar

    internal fun contextBarVisible() = bar.isVisible

    internal fun contextBarUsed() = bar.used()

    internal fun contextBarReserved() = bar.reserved()

    internal fun contextBarAvailable() = bar.available()

    internal fun contextBarLimit() = bar.limit()

    internal fun contextBarForegrounds() = bar.foregrounds()

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
        val total = sent + received + read

        tokenTitle.text = KiloBundle.message("session.header.tokens")
        tokens.toolTipText = KiloBundle.message("session.header.tokens.description")
        tokenTitle.isVisible = total > 0
        set(input, if (sent > 0) num(sent) else null)
        set(output, if (received > 0) num(received) else null)
        set(cacheRead, if (read > 0) KiloBundle.message("session.header.cache", num(read)) else null)
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
