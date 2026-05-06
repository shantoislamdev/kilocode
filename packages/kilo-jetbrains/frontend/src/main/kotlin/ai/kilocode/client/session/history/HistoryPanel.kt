package ai.kilocode.client.session.history

import ai.kilocode.client.plugin.KiloBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class HistoryPanel(
    parent: Disposable,
    private val controller: HistoryController,
    private val gitUrl: () -> String? = { null },
) : BorderLayoutPanel(), Disposable {
    private val rows = CollectionListModel<HistoryItem>()
    private val search = SearchTextField(false).apply {
        textEditor.emptyText.text = KiloBundle.message("history.search.placeholder")
    }
    private val list = JBList(rows).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = HistoryListRenderer()
        emptyText.text = KiloBundle.message("history.empty")
        addListSelectionListener { sync() }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount < 2) return
                selectedValue?.let(controller::open)
            }
        })
    }
    private val local = JButton(KiloBundle.message("history.tab.local"))
    private val cloud = JButton(KiloBundle.message("history.tab.cloud"))
    private val repo = JBCheckBox(KiloBundle.message("history.cloud.repo.only"))
    private val delete = JButton(KiloBundle.message("history.delete.text"))
    private val more = JButton(KiloBundle.message("history.cloud.load.more"))
    private val status = JBLabel()

    init {
        Disposer.register(parent, this)
        border = JBUI.Borders.empty(8)
        add(header(), BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(footer(), BorderLayout.SOUTH)
        bind(parent)
        sync()
        controller.loadLocal()
    }

    val component: JComponent get() = this

    private fun header(): JComponent {
        local.addActionListener {
            controller.selectSource(HistorySource.LOCAL)
            controller.loadLocal()
        }
        cloud.addActionListener {
            controller.selectSource(HistorySource.CLOUD)
            controller.loadCloud(gitUrl = if (repo.isSelected) gitUrl() else null)
        }
        repo.addActionListener {
            if (controller.model.source == HistorySource.CLOUD) {
                controller.loadCloud(gitUrl = if (repo.isSelected) gitUrl() else null)
            }
        }
        search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                sync()
            }
        })
        return BorderLayoutPanel().apply {
            add(BorderLayoutPanel().apply {
                add(local, BorderLayout.WEST)
                add(cloud, BorderLayout.CENTER)
                add(repo, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(search, BorderLayout.CENTER)
        }
    }

    private fun footer(): JComponent {
        delete.addActionListener { list.selectedValue?.let(controller::delete) }
        more.addActionListener { controller.loadMoreCloud() }
        return BorderLayoutPanel().apply {
            add(status, BorderLayout.CENTER)
            add(BorderLayoutPanel().apply {
                layout = FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)
                add(delete)
                add(more)
            }, BorderLayout.EAST)
        }
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
                is HistoryModelEvent.DeleteStarted -> Unit
            }
        }
    }

    private fun sync() {
        val query = search.text.trim().lowercase()
        val items = controller.model.items().filter { item ->
            query.isEmpty() || item.title.lowercase().contains(query) || item.id.lowercase().contains(query)
        }
        rows.replaceAll(items)
        local.isEnabled = controller.model.source != HistorySource.LOCAL
        cloud.isEnabled = controller.model.source != HistorySource.CLOUD
        repo.isVisible = controller.model.source == HistorySource.CLOUD
        delete.isEnabled = controller.model.source == HistorySource.LOCAL && list.selectedValue != null
        more.isVisible = controller.model.source == HistorySource.CLOUD
        more.isEnabled = controller.model.cursor != null && !controller.model.cloudLoading
        status.text = statusText()
    }

    private fun statusText(): String {
        val model = controller.model
        if (model.localLoading || model.cloudLoading) return KiloBundle.message("history.loading")
        val error = if (model.source == HistorySource.LOCAL) model.localError else model.cloudError
        if (error != null) return error
        return ""
    }

    internal fun itemCount() = rows.size

    internal fun selectedSource() = controller.model.source

    internal fun select(index: Int) {
        list.selectedIndex = index
        sync()
    }

    internal fun clickDelete() {
        delete.doClick()
    }

    internal fun clickCloud() {
        cloud.doClick()
    }

    internal fun clickMore() {
        more.doClick()
    }

    internal fun setSearch(value: String) {
        search.text = value
        sync()
    }

    override fun dispose() {
        // no-op
    }
}
