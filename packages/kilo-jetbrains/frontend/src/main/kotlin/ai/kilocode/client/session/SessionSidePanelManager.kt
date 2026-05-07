package ai.kilocode.client.session

import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.session.history.HistoryController
import ai.kilocode.client.session.history.HistoryPanel
import ai.kilocode.rpc.dto.SessionDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SessionSidePanelManager(
    private val project: Project,
    private val root: Workspace,
    private val create: (Project, Workspace, SessionManager, String?, Boolean, SessionDto?) -> SessionUi = { project, workspace, manager, id, loading, session ->
        service<SessionUiFactory>().create(project, workspace, manager, id, loading, session)
    },
    private val resolve: (String) -> Workspace = { dir -> service<KiloWorkspaceService>().workspace(dir) },
    private val history: ((Disposable, (SessionDto) -> Unit, (String) -> Unit) -> JComponent)? = null,
) : SessionManager, Disposable {
    val component: JPanel = object : JPanel(BorderLayout()), DataProvider {
        override fun getData(dataId: String): Any? {
            if (SessionManager.KEY.`is`(dataId)) return this@SessionSidePanelManager
            return null
        }
    }

    private val opened = mutableMapOf<String, SessionUi>()
    private val all = mutableSetOf<SessionUi>()
    private var current: SessionUi? = null
    private var panel: JComponent? = null

    val defaultFocusedComponent: JComponent? get() = current?.defaultFocusedComponent ?: (panel as? HistoryPanel)?.defaultFocusedComponent

    override fun newSession() {
        val active = current
        if (active?.blank == true) return
        register(active)
        show(create(project, root, this, null, active == null, null))
    }

    override fun openSession(session: SessionDto) {
        register(current)
        val ui = opened.getOrPut(session.id) {
            create(project, resolve(session.directory), this, session.id, false, session).also {
                all.add(it)
            }
        }
        show(ui)
    }

    override fun showHistory() {
        register(current)
        release(current)
        val cached = panel
        val view = cached ?: createHistory().also { panel = it }
        if (cached != null && view is HistoryPanel) view.refresh()
        if (current == null && component.componentCount == 1 && component.getComponent(0) === view) {
            focusHistory(view)
            return
        }
        current = null
        component.removeAll()
        component.add(view, BorderLayout.CENTER)
        component.revalidate()
        component.repaint()
        focusHistory(view)
    }

    private fun focusHistory(view: JComponent) {
        val focus = (view as? HistoryPanel)?.defaultFocusedComponent ?: return
        ApplicationManager.getApplication().invokeLater({
            IdeFocusManager.getInstance(project).requestFocusInProject(focus, project)
        }, ModalityState.defaultModalityState())
    }

    private fun createHistory(): JComponent {
        val custom = history
        if (custom != null) return custom(this, this::openSession, this::removeSession)
        val factory = service<SessionUiFactory>()
        val cs = factory.scope()
        val controller = HistoryController(
            sessions = project.service<KiloSessionService>(),
            workspace = root,
            cs = cs,
            open = { item -> openSession(item.session) },
            deleted = this::removeSession,
        )
        Disposer.register(this) { cs.cancel() }
        return HistoryPanel(this, controller).component
    }

    private fun removeSession(id: String) {
        val ui = opened.remove(id) ?: return
        all.remove(ui)
        if (current === ui) current = null
        Disposer.dispose(ui)
    }

    private fun show(ui: SessionUi) {
        all.add(ui)
        if (current === ui) return
        release(current)
        component.removeAll()
        current = ui
        component.add(ui, BorderLayout.CENTER)
        component.revalidate()
        component.repaint()
    }

    private fun register(ui: SessionUi?) {
        val id = ui?.id ?: return
        opened.putIfAbsent(id, ui)
    }

    private fun release(ui: SessionUi?) {
        if (ui == null) return
        if (ui.id != null) {
            register(ui)
            return
        }
        all.remove(ui)
        Disposer.dispose(ui)
    }

    override fun dispose() {
        val items = all.toList()
        opened.clear()
        all.clear()
        current = null
        component.removeAll()
        items.forEach { Disposer.dispose(it) }
    }
}
