package ai.kilocode.client.session.history

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ListUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class HistoryPanel(
    parent: Disposable,
    private val controller: HistoryController,
    private val gitUrl: () -> String? = { null },
) : BorderLayoutPanel(), Disposable {
    private val localSearch = search(controller.local)
    private val cloudSearch = search(controller.cloud)
    private val localList = localList()
    private val cloudList = cloudList()
    private val more = JButton(KiloBundle.message("history.cloud.load.more"))
    private val localPanel = panel(localSearch, localList)
    private val cloudPanel = panel(cloudSearch, cloudList, more)
    private val localInfo = TabInfo(localPanel).setText(KiloBundle.message("history.tab.local"))
    private val cloudInfo = TabInfo(cloudPanel).setText(KiloBundle.message("history.tab.cloud"))
    private var stale = false
    private val tabs: JBTabs = JBTabsFactory.createTabs(null, this).apply {
        presentation.setSingleRow(true)
        presentation.setTabsPosition(JBTabsPosition.top)
        presentation.showBorder = false
        addTab(localInfo).setPreferredFocusableComponent(localSearch.textEditor)
        addTab(cloudInfo).setPreferredFocusableComponent(cloudSearch.textEditor)
        addListener(object : TabsListener {
            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
                sync()
            }
        }, this@HistoryPanel)
    }

    init {
        Disposer.register(parent, this)
        border = JBUI.Borders.empty(UiStyle.Space.LG)
        more.addActionListener { controller.loadMoreCloud() }
        bind(localList, controller.local)
        bind(cloudList, controller.cloud)
        bindTheme()
        addHierarchyListener { e ->
            if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return@addHierarchyListener
            if (isShowing && stale) {
                refresh()
                return@addHierarchyListener
            }
            if (!isShowing) stale = true
        }
        add(tabs.component, BorderLayout.CENTER)
        sync()
        refresh()
    }

    val component: JComponent get() = this

    val defaultFocusedComponent: JComponent get() = activeSearch().textEditor

    fun refresh() {
        stale = false
        updateTheme()
        controller.reload(gitUrl())
    }

    private fun bindTheme() {
        val bus = ApplicationManager.getApplication().messageBus.connect(this)
        bus.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            ApplicationManager.getApplication().invokeLater {
                updateTheme()
            }
        })
    }

    private fun updateTheme() {
        SwingUtilities.updateComponentTreeUI(this)
        SwingUtilities.updateComponentTreeUI(localPanel)
        SwingUtilities.updateComponentTreeUI(cloudPanel)
        updateRenderer(localList)
        updateRenderer(cloudList)
        sync()
    }

    private fun updateRenderer(list: JBList<out HistoryItem>) {
        val view = list.cellRenderer
        if (view is JComponent) SwingUtilities.updateComponentTreeUI(view)
    }

    private fun search(model: HistoryModel<out HistoryItem>) = SearchTextField(false).apply {
        textEditor.emptyText.text = KiloBundle.message("history.search.placeholder")
        textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                model.setFilter(text)
            }
        })
        textEditor.registerKeyboardAction(
            { move(-1) },
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
            JComponent.WHEN_FOCUSED,
        )
        textEditor.registerKeyboardAction(
            { move(1) },
            KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
            JComponent.WHEN_FOCUSED,
        )
        textEditor.registerKeyboardAction(
            { activeList().selectedValue?.let(::activate) },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
    }

    private fun panel(search: SearchTextField, list: JList<out HistoryItem>, footer: JComponent? = null): JComponent {
        return BorderLayoutPanel().apply {
            add(search, BorderLayout.NORTH)
            add(JBScrollPane(list).apply {
                border = JBUI.Borders.empty()
                viewportBorder = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
            footer?.let {
                it.border = JBUI.Borders.emptyTop(UiStyle.Space.LG)
                add(it, BorderLayout.SOUTH)
            }
        }
    }

    private fun localList() = JBList(controller.local).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        isFocusable = false
        cellRenderer = LocalHistoryRenderer(controller.local)
        emptyText.text = KiloBundle.message("history.empty")
        addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (!UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED, true)) return
                val item = clicked(this@apply, e) ?: return
                if (deleteClick(this@apply, e)) {
                    confirm(item)
                    e.consume()
                    return
                }
                activate(item)
            }
        })
        registerKeyboardAction(
            { selectedValue?.let(::activate) },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
        ListUtil.installAutoSelectOnMouseMove(this)
        ScrollingUtil.installActions(this)
    }

    private fun cloudList() = JBList(controller.cloud).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        isFocusable = false
        cellRenderer = CloudHistoryRenderer(controller.cloud)
        emptyText.text = KiloBundle.message("history.empty")
        addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (!UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED, true)) return
                clicked(this@apply, e)?.let(::activate)
            }
        })
        registerKeyboardAction(
            { selectedValue?.let(::activate) },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
        ListUtil.installAutoSelectOnMouseMove(this)
        ScrollingUtil.installActions(this)
    }

    private fun <T : HistoryItem> bind(list: JBList<T>, model: HistoryModel<T>) {
        val listener = object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) = sync()

            override fun intervalRemoved(e: ListDataEvent) = sync()

            override fun contentsChanged(e: ListDataEvent) = sync()
        }
        model.addListDataListener(listener)
        Disposer.register(this) { model.removeListDataListener(listener) }
        list.setPaintBusy(model.loading)
    }

    private fun sync() {
        syncList(localList, controller.local)
        syncList(cloudList, controller.cloud)
        more.isEnabled = controller.cloud.cursor != null && !controller.cloud.loading
        more.isVisible = controller.cloud.cursor != null || controller.cloud.loading
        revalidate()
        repaint()
    }

    private fun <T : HistoryItem> syncList(list: JBList<T>, model: HistoryModel<T>) {
        list.setPaintBusy(model.loading)
        list.emptyText.text = when {
            model.loading -> KiloBundle.message("history.loading")
            model.error != null -> model.error.orEmpty()
            else -> KiloBundle.message("history.empty")
        }
    }

    private fun deleteClick(list: JBList<LocalHistoryItem>, e: MouseEvent): Boolean {
        val row = list.locationToIndex(e.point)
        val box = row.takeIf { it >= 0 }?.let { list.getCellBounds(it, it) } ?: return false
        if (!box.contains(e.point)) return false
        return HistoryRenderer.isDeleteClick(list, box, e.point)
    }

    private fun <T : HistoryItem> clicked(list: JBList<T>, e: MouseEvent): T? {
        val row = list.locationToIndex(e.point)
        val box = row.takeIf { it >= 0 }?.let { list.getCellBounds(it, it) } ?: return null
        if (!box.contains(e.point)) return null
        list.selectedIndex = row
        return list.model.getElementAt(row)
    }

    private fun activate(item: HistoryItem) {
        when (item) {
            is LocalHistoryItem -> controller.open(item)
            is CloudHistoryItem -> controller.open(item)
        }
    }

    private fun confirm(item: LocalHistoryItem) {
        if (controller.deleting(item)) return
        val result = Messages.showYesNoDialog(
            this,
            KiloBundle.message("history.delete.confirm.message", item.title.takeIf { it.isNotBlank() } ?: KiloBundle.message("history.untitled")),
            KiloBundle.message("history.delete.confirm.title"),
            Messages.getWarningIcon(),
        )
        if (result != Messages.YES) return
        controller.delete(item)
    }

    internal fun itemCount() = activeModel().size

    internal fun selectedSource() = if (tabs.selectedInfo === cloudInfo) HistorySource.CLOUD else HistorySource.LOCAL

    internal fun select(index: Int) {
        activeList().selectedIndex = index
    }

    internal fun selectedIndex() = activeList().selectedIndex

    internal fun listFocusable() = activeList().isFocusable

    internal fun clickDelete() {
        localList.selectedValue?.let(controller::delete)
    }

    internal fun clickCloud() {
        tabs.select(cloudInfo, false)
        sync()
    }

    internal fun clickLocal() {
        tabs.select(localInfo, false)
        sync()
    }

    internal fun clickMore() {
        more.doClick()
    }

    internal fun setSearch(value: String) {
        if (tabs.selectedInfo === cloudInfo) cloudSearch.text = value else localSearch.text = value
    }

    internal fun groupTitles(): List<String> {
        val items = activeModel().visibleItems
        return items.indices.mapNotNull { HistoryRenderer.section(items, it) }
    }

    internal fun deleteVisible(index: Int, selected: Boolean = true): Boolean {
        val item = controller.local.getElementAt(index)
        val view = localList.cellRenderer.getListCellRendererComponent(localList, item, index, selected, false)
        return view is HistoryRenderer<*> && view.deleteVisible()
    }

    internal fun cloudDeleteVisible(index: Int, selected: Boolean = true): Boolean {
        val item = controller.cloud.getElementAt(index)
        val view = cloudList.cellRenderer.getListCellRendererComponent(cloudList, item, index, selected, false)
        return view is HistoryRenderer<*> && view.deleteVisible()
    }

    private fun activeList(): JBList<out HistoryItem> = if (tabs.selectedInfo === cloudInfo) cloudList else localList

    private fun activeModel(): HistoryModel<out HistoryItem> = if (tabs.selectedInfo === cloudInfo) controller.cloud else controller.local

    private fun activeSearch(): SearchTextField = if (tabs.selectedInfo === cloudInfo) cloudSearch else localSearch

    private fun move(step: Int) {
        val list = activeList()
        val size = list.model.size
        if (size <= 0) return
        val cur = list.selectedIndex.takeIf { it >= 0 } ?: if (step > 0) -1 else size
        val idx = (cur + step).coerceIn(0, size - 1)
        list.selectedIndex = idx
        ScrollingUtil.ensureIndexIsVisible(list, idx, 0)
    }

    override fun dispose() {
        // no-op
    }
}
