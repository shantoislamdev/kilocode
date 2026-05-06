package ai.kilocode.client.session.history

import ai.kilocode.client.plugin.KiloBundle
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class HistoryListRenderer : BorderLayoutPanel(), ListCellRenderer<HistoryItem> {
    private val title = JBLabel()
    private val meta = JBLabel()

    init {
        border = JBUI.Borders.empty(8, 12, 8, 12)
        add(title, BorderLayout.CENTER)
        add(meta, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(
        list: JList<out HistoryItem>,
        value: HistoryItem?,
        index: Int,
        selected: Boolean,
        focus: Boolean,
    ): Component {
        isOpaque = selected
        background = if (selected) list.selectionBackground else list.background
        title.foreground = if (selected) list.selectionForeground else UIUtil.getLabelForeground()
        meta.foreground = if (selected) list.selectionForeground else UIUtil.getContextHelpForeground()
        title.text = value?.title?.takeIf { it.isNotBlank() } ?: KiloBundle.message("history.untitled")
        meta.text = value?.updatedAt.orEmpty()
        return this
    }
}
