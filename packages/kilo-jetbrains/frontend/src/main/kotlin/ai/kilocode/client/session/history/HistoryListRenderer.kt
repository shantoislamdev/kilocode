package ai.kilocode.client.session.history

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.PickerRow
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.ui.CollectionListModel
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

private const val DELETE_CLICK_AREA_WIDTH = 32

internal class HistoryListRenderer(
    private val model: CollectionListModel<HistoryItem>,
    private val source: () -> HistorySource,
    private val deletable: Boolean,
) : JPanel(BorderLayout()), ListCellRenderer<HistoryItem> {
    companion object {
        private val icon: Icon = AllIcons.General.Remove
        private val empty: Icon = EmptyIcon.create(icon)

        fun isDeleteClick(list: JList<*>, bounds: Rectangle, point: Point): Boolean {
            val width = JBUI.scale(DELETE_CLICK_AREA_WIDTH)
            if (list.componentOrientation.isLeftToRight) {
                val right = bounds.x + bounds.width
                return point.x in (right - width)..right
            }
            return point.x in bounds.x..(bounds.x + width)
        }

        fun section(items: List<HistoryItem>, index: Int): String? {
            val item = items.getOrNull(index) ?: return null
            val current = HistoryTime.section(item)
            val previous = items.getOrNull(index - 1)?.let(HistoryTime::section)
            if (current == previous) return null
            return HistoryTime.title(current)
        }
    }

    private val sep = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
    private val top = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty()
        add(sep, BorderLayout.NORTH)
    }
    private val title = SimpleColoredComponent()
    private val time = JBLabel()
    private val del = JBLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment = SwingConstants.CENTER
    }
    private val main = JPanel(BorderLayout()).apply {
        add(title, BorderLayout.CENTER)
        add(time, BorderLayout.EAST)
    }
    private val row = JPanel(BorderLayout()).apply {
        add(main, BorderLayout.CENTER)
        add(del, BorderLayout.EAST)
    }
    private val wrap = PickerRow()

    init {
        isOpaque = true
        top.isOpaque = true
        row.border = JBUI.Borders.empty(UiStyle.Space.LG, UiStyle.Space.LG, UiStyle.Space.LG, UiStyle.Space.LG)
        UiStyle.Components.transparent(row)
        UiStyle.Components.transparent(main)
        UiStyle.Components.transparent(title)
        UiStyle.Components.transparent(time)
        UiStyle.Components.transparent(del)
        wrap.setContent(row)
        add(top, BorderLayout.NORTH)
        add(wrap, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out HistoryItem>,
        value: HistoryItem?,
        index: Int,
        selected: Boolean,
        focus: Boolean,
    ): JPanel {
        val focused = selected || list.hasFocus() || focus
        val fg = UIUtil.getListForeground(selected, focused)
        val weak = if (selected) fg else UIUtil.getContextHelpForeground()
        val section = if (source() == HistorySource.LOCAL) section(model.items, index) else null

        background = list.background
        top.background = list.background
        wrap.update(list, selected, focused)
        sep.caption = section
        sep.setHideLine(index == 0)
        top.isVisible = section != null

        title.clear()
        title.append(
            value?.title?.takeIf { it.isNotBlank() } ?: KiloBundle.message("history.untitled"),
            SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, fg),
        )
        time.text = value?.let(HistoryTime::relative).orEmpty()
        time.foreground = weak
        del.icon = if (deletable && selected) icon else empty

        top.invalidate()
        return this
    }

    fun deleteVisible(): Boolean = del.icon === icon
}
