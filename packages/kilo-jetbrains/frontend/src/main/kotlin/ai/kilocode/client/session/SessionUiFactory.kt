package ai.kilocode.client.session

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.app.Workspace
import ai.kilocode.rpc.dto.SessionDto
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

@Service(Service.Level.APP)
class SessionUiFactory(
    private val cs: CoroutineScope,
) {
    fun create(
        project: Project,
        workspace: Workspace,
        manager: SessionManager,
        id: String? = null,
        session: SessionDto? = null,
        target: SessionRef? = SessionRef.resolve(id, session),
    ): SessionUi = SessionUi(
        project = project,
        workspace = workspace,
        sessions = project.service<KiloSessionService>(),
        app = service<KiloAppService>(),
        cs = scope(),
        id = id,
        session = session,
        target = target,
        open = { ref -> manager.openSession(ref) },
    )

    fun scope(): CoroutineScope {
        val parent = cs.coroutineContext[Job]
        return CoroutineScope(cs.coroutineContext + SupervisorJob(parent))
    }
}
