package ai.kilocode.client.session.history

import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.CloudSessionDto
import ai.kilocode.rpc.dto.SessionDto
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class HistoryController(
    private val sessions: KiloSessionService,
    private val workspace: Workspace,
    private val cs: CoroutineScope,
    private val open: (HistoryItem) -> Unit = {},
    private val deleted: (String) -> Unit = {},
) {
    companion object {
        const val CLOUD_LIMIT = 50
    }

    val model = HistoryModel()

    private var gitUrl: String? = null

    fun loadLocal() {
        edt { model.startLocal() }
        cs.launch {
            try {
                val result = sessions.list(workspace.directory)
                val items = HistoryTime.sorted(result.sessions.map(::localItem))
                edt { model.setLocal(items) }
            } catch (e: Exception) {
                edt { model.error(HistorySource.LOCAL, e.message ?: KiloBundle.message("history.error.local")) }
            }
        }
    }

    fun loadCloud(reset: Boolean = true, gitUrl: String? = null) {
        val cursor = if (reset) null else model.cursor
        val url = if (reset) gitUrl else this.gitUrl
        if (reset) this.gitUrl = gitUrl
        edt { model.startCloud(reset) }
        cs.launch {
            try {
                val result = sessions.cloudSessions(workspace.directory, cursor, CLOUD_LIMIT, url)
                val items = HistoryTime.sorted(result.sessions.map(::cloudItem))
                edt { model.setCloud(items, result.nextCursor, append = !reset) }
            } catch (e: Exception) {
                edt { model.error(HistorySource.CLOUD, e.message ?: KiloBundle.message("history.error.cloud")) }
            }
        }
    }

    fun loadMoreCloud() {
        if (model.cursor == null || model.cloudLoading) return
        loadCloud(reset = false)
    }

    fun selectSource(source: HistorySource) {
        edt { model.select(source) }
    }

    fun delete(item: HistoryItem) {
        if (item.source == HistorySource.CLOUD) {
            edt { model.error(HistorySource.CLOUD, KiloBundle.message("history.error.cloud.delete")) }
            return
        }
        edt { model.startDelete(item.id) }
        cs.launch {
            try {
                sessions.deleteSession(item.id, item.directory ?: workspace.directory)
                edt {
                    model.deleted(item.id)
                    deleted(item.id)
                }
            } catch (e: Exception) {
                edt { model.error(HistorySource.LOCAL, e.message ?: KiloBundle.message("history.error.local.delete")) }
            }
        }
    }

    fun open(item: HistoryItem) {
        if (item.source != HistorySource.LOCAL) return
        edt { open(item) }
    }
}

private fun edt(block: () -> Unit) {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
        block()
        return
    }
    app.invokeLater(block)
}

private fun localItem(session: SessionDto) = HistoryItem(
    id = session.id,
    source = HistorySource.LOCAL,
    title = session.title,
    createdAt = session.time.created.toString(),
    updatedAt = session.time.updated.toString(),
    directory = session.directory,
    local = session,
)

private fun cloudItem(session: CloudSessionDto) = HistoryItem(
    id = session.id,
    source = HistorySource.CLOUD,
    title = session.title.orEmpty(),
    createdAt = session.createdAt,
    updatedAt = session.updatedAt,
)
