package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.SessionRef
import ai.kilocode.client.session.history.HistoryTime
import ai.kilocode.client.session.history.LocalHistoryItem
import ai.kilocode.client.session.history.clicked
import ai.kilocode.client.session.history.title
import ai.kilocode.client.session.update.SessionController
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.md.MdView
import ai.kilocode.rpc.dto.SessionDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.Centerizer
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

/**
 * Centered empty-session panel.
 */
class EmptySessionPanel(
    parent: Disposable,
    private val controller: SessionController,
    recents: List<SessionDto>,
    private val history: () -> Unit = {},
) : BorderLayoutPanel(), Disposable, SessionStyleTarget {

    companion object {
        internal val LIMIT = UiStyle.Size.LIMIT
        internal val MAX_WIDTH = UiStyle.Size.WIDTH
    }

    private val model = DefaultListModel<LocalHistoryItem>()
    private var hover = -1
    private var style = SessionStyle.current()
    private val recentTitle = JBLabel(KiloBundle.message("session.empty.recent")).apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.emptyLeft(UiStyle.Space.LG)
    }

    private val list = JBList(model).apply {
        // Blend the recent-session list into the centered empty-state surface.
        isOpaque = false
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = LIMIT
        cellRenderer = SessionRenderer()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        emptyText.clear()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val item = clicked(this@apply, e) ?: return
                controller.openSession(SessionRef.Local(item.session))
            }

            override fun mouseExited(e: MouseEvent) {
                hover = -1
                repaint()
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = index(e)
                if (hover == index) return
                hover = index
                repaint()
            }
        })
    }
    private val historyButton = ShowHistoryButton().apply {
        alignmentX = CENTER_ALIGNMENT
        addActionListener { history() }
    }
    private val md = MdView.html().apply {
        // MdView uses an HTML component; transparency keeps the centered panel seamless.
        opaque = false
        foreground = UIUtil.getContextHelpForeground()
        set(KiloBundle.message("session.empty.welcome"))
    }
    private val content = createContent()

    init {
        Disposer.register(parent, this)
        // The empty state floats on the tool-window background.
        isOpaque = false
        border = UiStyle.Insets.empty()
        applyStyle(SessionStyle.current())
        setSessions(recents)
        add(Centerizer(content, Centerizer.TYPE.BOTH), BorderLayout.CENTER)
    }

    private fun setSessions(sessions: List<SessionDto>) {
        model.clear()
        sessions.take(LIMIT).map(::LocalHistoryItem).forEach(model::addElement)
        revalidate()
        repaint()
    }

    private fun createContent(): BorderLayoutPanel {
        val logo = JBLabel(
            IconLoader.getIcon("/icons/kilo-content.svg", EmptySessionPanel::class.java),
        ).apply {
            alignmentX = CENTER_ALIGNMENT
        }
        val intro = BorderLayoutPanel().apply {
            alignmentX = CENTER_ALIGNMENT
            add(md.component, BorderLayout.CENTER)
            border = JBUI.Borders.empty(0, UiStyle.Space.PAD, 0, UiStyle.Space.PAD)
        }
        val recent = BorderLayoutPanel().apply {
            alignmentX = CENTER_ALIGNMENT
            add(recentTitle, BorderLayout.NORTH)
            add(list, BorderLayout.CENTER)
            add(BorderLayoutPanel().apply {
                border = JBUI.Borders.emptyTop(UiStyle.Space.LG)
                add(historyButton, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }
        val stack = BorderLayoutPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(logo)
            add(Box.createVerticalStrut(JBUI.scale(UiStyle.Space.LOGO)))
            add(intro)
            add(Box.createVerticalStrut(JBUI.scale(UiStyle.Space.RECENT)))
            add(recent)
        }
        return object : BorderLayoutPanel() {
            override fun getPreferredSize(): Dimension {
                val size = super.getPreferredSize()
                return JBDimension(JBUI.scale(MAX_WIDTH), size.height)
            }
        }.apply {
            add(stack, BorderLayout.NORTH)
        }
    }

    internal fun recentCount() = model.size()

    internal fun selectRecent(index: Int) {
        list.selectedIndex = index
    }

    internal fun selectedRecent() = list.selectedIndex

    internal fun clickRecent(index: Int) {
        list.selectedIndex = index
        controller.openSession(SessionRef.Local(model.getElementAt(index).session))
    }

    internal fun clickShowHistory() {
        historyButton.doClick()
    }

    internal fun showHistoryText() = historyButton.text

    internal fun showHistoryBorderPainted() = historyButton.isBorderPainted

    internal fun showHistoryCursor() = historyButton.cursor.type

    internal fun recentCursor() = list.cursor.type

    internal fun recentVisible() = true

    internal fun explanationMarkdown() = md.markdown()

    internal fun contentPreferredSize() = content.preferredSize

    internal fun initialized() = true

    internal fun loadingVisible() = false

    internal fun activeView() = getComponent(0)

    internal fun text(session: SessionDto, now: Long = System.currentTimeMillis()) = HistoryTime.relative(LocalHistoryItem(session), now)

    internal fun rendererComponent(
        session: SessionDto,
        selected: Boolean = false,
        hover: Boolean = false,
    ): Component {
        val old = this.hover
        this.hover = if (hover) 0 else -1
        return list.cellRenderer.getListCellRendererComponent(list, LocalHistoryItem(session), 0, selected, false).also {
            this.hover = old
        }
    }

    private fun index(e: MouseEvent): Int {
        val idx = list.locationToIndex(e.point)
        if (idx < 0) return -1
        val box = list.getCellBounds(idx, idx) ?: return -1
        if (!box.contains(e.point)) return -1
        return idx
    }

    private inner class SessionRenderer : BorderLayoutPanel(), ListCellRenderer<LocalHistoryItem> {
        private val title = JBLabel()
        private val time = JBLabel()

        init {
            border = JBUI.Borders.empty(UiStyle.Space.LG, UiStyle.Space.LG, UiStyle.Space.LG, UiStyle.Space.LG)
            add(title, BorderLayout.CENTER)
            add(time, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out LocalHistoryItem>,
            value: LocalHistoryItem?,
            index: Int,
            selected: Boolean,
            focus: Boolean,
        ): Component {
            val active = selected || hover == index
            isOpaque = active
            background = if (active) list.selectionBackground else list.background
            title.foreground = if (active) list.selectionForeground else UIUtil.getLabelForeground()
            time.foreground = if (active) list.selectionForeground else UIUtil.getContextHelpForeground()
            title.text = value?.let(::title) ?: ""
            time.text = value?.let(HistoryTime::relative) ?: ""
            return this
        }
    }

    private inner class ShowHistoryButton : JButton(KiloBundle.message("session.showHistory"), AllIcons.Vcs.History) {
        private var over = false

        init {
            isFocusable = false
            setRequestFocusEnabled(false)
            isContentAreaFilled = false
            isBorderPainted = false
            isOpaque = false
            border = JBUI.Borders.empty(UiStyle.Space.SM, UiStyle.Space.LG)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    sync(true)
                }

                override fun mouseExited(e: MouseEvent) {
                    sync(false)
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            if (isEnabled && over) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
                    val arc = JBUI.scale(JBUI.getInt("Button.arc", 6))
                    g2.fillRoundRect(0, 0, width, height, arc, arc)
                } finally {
                    g2.dispose()
                }
            }
            super.paintComponent(g)
        }

        private fun sync(value: Boolean) {
            if (over == value) return
            over = value
            repaint()
        }
    }

    override fun dispose() {
        // no-op
    }

    override fun applyStyle(style: SessionStyle) {
        this.style = style
        md.font = style.uiFont
        recentTitle.font = style.smallUiFont
        revalidate()
        repaint()
    }
}
