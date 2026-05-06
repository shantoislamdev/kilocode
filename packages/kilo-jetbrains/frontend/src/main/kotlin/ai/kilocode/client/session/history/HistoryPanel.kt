package ai.kilocode.client.session.history

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class HistoryPanel(
    parent: Disposable,
    private val controller: HistoryController,
    private val gitUrl: () -> String? = { null },
) : BorderLayoutPanel(), Disposable {
    private val local = Tab(HistorySource.LOCAL)
    private val cloud = Tab(HistorySource.CLOUD)
    private val status = JBLabel()
    private val body = BorderLayoutPanel()
    private val localRows = CollectionListModel<HistoryItem>()
    private val cloudRows = CollectionListModel<HistoryItem>()
    private val localRenderer = HistoryListRenderer(localRows, source = { controller.model.source }, deletable = true)
    private val cloudRenderer = HistoryListRenderer(cloudRows, source = { controller.model.source }, deletable = false)
    private val localSearch = search()
    private val cloudSearch = search()
    private val localList = list(localRows, HistorySource.LOCAL, localRenderer)
    private val cloudList = list(cloudRows, HistorySource.CLOUD, cloudRenderer)
    private val localPanel = panel(localSearch, localList)
    private val more = JButton(KiloBundle.message("history.cloud.load.more"))
    private val cloudPanel = panel(cloudSearch, cloudList, more)
    private var loadedCloud = false

    init {
        Disposer.register(parent, this)
        border = JBUI.Borders.empty(UiStyle.Space.LG)
        add(header(), BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        bind(parent)
        sync()
        controller.loadLocal()
    }

    val component: JComponent get() = this

    private fun header(): JComponent {
        local.addActionListener {
            controller.selectSource(HistorySource.LOCAL)
        }
        cloud.addActionListener {
            controller.selectSource(HistorySource.CLOUD)
            if (!loadedCloud) {
                loadedCloud = true
                controller.loadCloud(gitUrl = gitUrl())
            }
        }
        more.addActionListener { controller.loadMoreCloud() }
        return BorderLayoutPanel().apply {
            border = JBUI.Borders.emptyBottom(UiStyle.Space.LG)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(local)
                add(Box.createHorizontalStrut(JBUI.scale(UiStyle.Space.SM)))
                add(cloud)
            }, BorderLayout.WEST)
        }
    }

    private fun search() = SearchTextField(false).apply {
        textEditor.emptyText.text = KiloBundle.message("history.search.placeholder")
        textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                sync()
            }
        })
    }

    private fun panel(search: SearchTextField, list: JBList<HistoryItem>, footer: JComponent? = null): JComponent {
        return BorderLayoutPanel().apply {
            add(search, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
            footer?.let {
                it.border = JBUI.Borders.emptyTop(UiStyle.Space.LG)
                add(it, BorderLayout.SOUTH)
            }
        }
    }

    private fun list(rows: CollectionListModel<HistoryItem>, source: HistorySource, renderer: HistoryListRenderer) = JBList(rows).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = renderer
        emptyText.text = KiloBundle.message("history.empty")
        addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                val item = clicked(this@apply, e) ?: return
                if (source == HistorySource.LOCAL && UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED, true) && deleteClick(this@apply, e)) {
                    confirm(item)
                    e.consume()
                    return
                }
                if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED, true)) controller.open(item)
            }
        })
    }

    private fun bind(parent: Disposable) {
        controller.model.addListener(parent) { event ->
            when (event) {
                is HistoryModelEvent.LocalLoading,
                is HistoryModelEvent.LocalLoaded,
                is HistoryModelEvent.CloudLoading,
                is HistoryModelEvent.CloudLoaded,
                is HistoryModelEvent.Deleted,
                is HistoryModelEvent.Error,
                is HistoryModelEvent.SourceChanged -> sync()
                is HistoryModelEvent.DeleteStarted -> sync()
            }
        }
    }

    private fun sync() {
        syncRows(localRows, controller.model.local, localSearch.text)
        syncRows(cloudRows, controller.model.cloud, cloudSearch.text)
        local.isSelected = controller.model.source == HistorySource.LOCAL
        cloud.isSelected = controller.model.source == HistorySource.CLOUD
        val target = if (controller.model.source == HistorySource.LOCAL) localPanel else cloudPanel
        if (body.componentCount != 1 || body.getComponent(0) !== target) {
            body.removeAll()
            body.add(target, BorderLayout.CENTER)
        }
        more.isEnabled = controller.model.cursor != null && !controller.model.cloudLoading
        more.isVisible = controller.model.cursor != null || controller.model.cloudLoading
        status.text = statusText()
        revalidate()
        repaint()
    }

    private fun syncRows(model: CollectionListModel<HistoryItem>, items: List<HistoryItem>, value: String) {
        val query = value.trim().lowercase()
        val selected = if (model === localRows) localList.selectedValue?.id else cloudList.selectedValue?.id
        val next = HistoryTime.sorted(items).filter { item ->
            query.isEmpty() || item.title.lowercase().contains(query) || item.id.lowercase().contains(query) || item.directory?.lowercase()?.contains(query) == true
        }
        model.replaceAll(next)
        val idx = next.indexOfFirst { it.id == selected }
        if (idx >= 0) {
            if (model === localRows) localList.selectedIndex = idx else cloudList.selectedIndex = idx
        }
    }

    private fun statusText(): String {
        val model = controller.model
        if (model.localLoading || model.cloudLoading) return KiloBundle.message("history.loading")
        val error = if (model.source == HistorySource.LOCAL) model.localError else model.cloudError
        if (error != null) return error
        return ""
    }

    private fun deleteClick(list: JBList<HistoryItem>, e: MouseEvent): Boolean {
        val row = list.locationToIndex(e.point)
        val box = row.takeIf { it >= 0 }?.let { list.getCellBounds(it, it) } ?: return false
        if (!box.contains(e.point)) return false
        return HistoryListRenderer.isDeleteClick(list, box, e.point)
    }

    private fun clicked(list: JBList<HistoryItem>, e: MouseEvent): HistoryItem? {
        val row = list.locationToIndex(e.point)
        val box = row.takeIf { it >= 0 }?.let { list.getCellBounds(it, it) } ?: return null
        if (!box.contains(e.point)) return null
        list.selectedIndex = row
        return list.model.getElementAt(row)
    }

    private fun confirm(item: HistoryItem) {
        if (controller.model.deleting(item.id)) return
        val result = Messages.showYesNoDialog(
            this,
            KiloBundle.message("history.delete.confirm.message", item.title.takeIf { it.isNotBlank() } ?: KiloBundle.message("history.untitled")),
            KiloBundle.message("history.delete.confirm.title"),
            Messages.getWarningIcon(),
        )
        if (result != Messages.YES) return
        controller.delete(item)
    }

    internal fun itemCount() = if (controller.model.source == HistorySource.LOCAL) localRows.size else cloudRows.size

    internal fun selectedSource() = controller.model.source

    internal fun select(index: Int) {
        activeList().selectedIndex = index
        sync()
    }

    internal fun clickDelete() {
        activeList().selectedValue?.let(controller::delete)
    }

    internal fun clickCloud() {
        cloud.doClick()
    }

    internal fun clickLocal() {
        local.doClick()
    }

    internal fun clickMore() {
        more.doClick()
    }

    internal fun setSearch(value: String) {
        if (controller.model.source == HistorySource.LOCAL) localSearch.text = value else cloudSearch.text = value
        sync()
    }

    internal fun groupTitles(): List<String> {
        val model = if (controller.model.source == HistorySource.LOCAL) localRows else cloudRows
        return model.items.indices.mapNotNull { HistoryListRenderer.section(model.items, it) }
    }

    internal fun deleteVisible(index: Int, selected: Boolean = true): Boolean {
        val item = localRows.getElementAt(index)
        val view = localList.cellRenderer.getListCellRendererComponent(localList, item, index, selected, false)
        return view is HistoryListRenderer && view.deleteVisible()
    }

    internal fun cloudDeleteVisible(index: Int, selected: Boolean = true): Boolean {
        val item = cloudRows.getElementAt(index)
        val view = cloudList.cellRenderer.getListCellRendererComponent(cloudList, item, index, selected, false)
        return view is HistoryListRenderer && view.deleteVisible()
    }

    private fun activeList() = if (controller.model.source == HistorySource.LOCAL) localList else cloudList

    override fun dispose() {
        // no-op
    }

    private class Tab(private val source: HistorySource) : JToggleButton(
        when (source) {
            HistorySource.LOCAL -> KiloBundle.message("history.tab.local")
            HistorySource.CLOUD -> KiloBundle.message("history.tab.cloud")
        },
    ) {
        init {
            isFocusable = false
        }

        override fun updateUI() {
            super.updateUI()
            border = JBUI.Borders.empty(UiStyle.Space.SM, UiStyle.Space.LG)
        }
    }
}
