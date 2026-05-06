package ai.kilocode.client.session.history

import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.testing.FakeSessionRpcApi
import ai.kilocode.client.testing.FakeWorkspaceRpcApi
import ai.kilocode.rpc.dto.CloudSessionDto
import ai.kilocode.rpc.dto.KiloWorkspaceStateDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import ai.kilocode.rpc.dto.SessionDto
import ai.kilocode.rpc.dto.SessionTimeDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

@Suppress("UnstableApiUsage")
class HistoryControllerTest : BasePlatformTestCase() {
    private lateinit var scope: CoroutineScope
    private lateinit var parent: Disposable
    private lateinit var rpc: FakeSessionRpcApi
    private lateinit var sessions: KiloSessionService
    private lateinit var workspace: Workspace

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())
        parent = Disposer.newDisposable("history")
        rpc = FakeSessionRpcApi()
        sessions = KiloSessionService(project, scope, rpc)
        val workspaces = KiloWorkspaceService(scope, FakeWorkspaceRpcApi().also {
            it.state.value = KiloWorkspaceStateDto(status = KiloWorkspaceStatusDto.READY)
        })
        workspace = workspaces.workspace("/test")
    }

    override fun tearDown() {
        try {
            Disposer.dispose(parent)
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    fun `test local load maps sessions and events`() {
        rpc.listed += session("ses_1", "Local One")
        val controller = controller()
        val events = collect(controller)

        controller.loadLocal()
        flush()

        assertEquals(listOf("/test"), rpc.lists)
        assertEquals(1, controller.model.local.size)
        assertEquals("ses_1", controller.model.local[0].id)
        assertEquals("Local One", controller.model.local[0].title)
        assertEquals("LocalLoading\nLocalLoaded count=1", events.joinToString("\n"))
    }

    fun `test cloud load maps sessions and supports load more`() {
        rpc.cloud += cloud("cloud_1", "Cloud One")
        rpc.cloudCursor = "next_1"
        val controller = controller()

        controller.loadCloud(gitUrl = "git@example.com:repo.git")
        flush()

        assertEquals(1, controller.model.cloud.size)
        assertEquals("cloud_1", controller.model.cloud[0].id)
        assertEquals("next_1", controller.model.cursor)
        assertEquals(FakeSessionRpcApi.CloudCall("/test", null, 50, "git@example.com:repo.git"), rpc.cloudCalls[0])

        rpc.cloud.clear()
        rpc.cloud += cloud("cloud_2", "Cloud Two")
        rpc.cloudCursor = null
        controller.loadMoreCloud()
        flush()

        assertEquals(listOf("cloud_1", "cloud_2"), controller.model.cloud.map { it.id })
        assertEquals(FakeSessionRpcApi.CloudCall("/test", "next_1", 50, "git@example.com:repo.git"), rpc.cloudCalls[1])
    }

    fun `test local delete calls rpc and removes item`() {
        rpc.listed += session("ses_1", "Local One")
        val controller = controller()
        controller.loadLocal()
        flush()

        controller.delete(controller.model.local[0])
        flush()

        assertEquals(listOf("ses_1" to "/test"), rpc.deletes)
        assertTrue(controller.model.local.isEmpty())
    }

    fun `test cloud delete emits unsupported error`() {
        val controller = controller()
        val item = HistoryItem("cloud_1", HistorySource.CLOUD, "Cloud", "a", "b")

        controller.delete(item)
        flush()

        assertEquals(emptyList<Pair<String, String>>(), rpc.deletes)
        assertEquals("Cloud sessions cannot be deleted yet", controller.model.cloudError)
    }

    fun `test panel filters and switches source`() {
        rpc.listed += session("ses_1", "Alpha")
        rpc.listed += session("ses_2", "Beta")
        rpc.cloud += cloud("cloud_1", "Cloud")
        val controller = controller()
        val panel = HistoryPanel(parent, controller)
        flush()

        assertEquals(2, panel.itemCount())
        panel.setSearch("alp")
        assertEquals(1, panel.itemCount())

        panel.clickCloud()
        flush()

        assertEquals(HistorySource.CLOUD, panel.selectedSource())
        panel.setSearch("")
        assertEquals(1, panel.itemCount())
    }

    fun `test panel preserves independent search per source`() {
        rpc.listed += session("ses_1", "Alpha")
        rpc.listed += session("ses_2", "Beta")
        rpc.cloud += cloud("cloud_1", "Cloud Alpha")
        rpc.cloud += cloud("cloud_2", "Cloud Beta")
        val panel = HistoryPanel(parent, controller())
        flush()

        panel.setSearch("alp")
        assertEquals(1, panel.itemCount())

        panel.clickCloud()
        flush()
        assertEquals(2, panel.itemCount())
        panel.setSearch("beta")
        assertEquals(1, panel.itemCount())

        panel.clickLocal()
        assertEquals(1, panel.itemCount())
    }

    fun `test panel groups sessions by date`() {
        val now = Instant.now()
        rpc.listed += session("ses_today", "Today", now.toEpochMilli().toDouble())
        rpc.listed += session("ses_yesterday", "Yesterday", now.minus(1, ChronoUnit.DAYS).toEpochMilli().toDouble())
        rpc.listed += session("ses_week", "Week", now.minus(3, ChronoUnit.DAYS).toEpochMilli().toDouble())
        rpc.listed += session("ses_month", "Month", now.minus(10, ChronoUnit.DAYS).toEpochMilli().toDouble())
        rpc.listed += session("ses_older", "Older", now.minus(60, ChronoUnit.DAYS).toEpochMilli().toDouble())
        val panel = HistoryPanel(parent, controller())
        flush()

        assertTrue(panel.groupTitles().containsAll(listOf("Today", "Yesterday", "This Week", "Older")))
    }

    fun `test local renderer exposes delete and cloud renderer hides it`() {
        rpc.listed += session("ses_1", "Local")
        rpc.cloud += cloud("cloud_1", "Cloud")
        val panel = HistoryPanel(parent, controller())
        flush()

        assertTrue(panel.deleteVisible(0))

        panel.clickCloud()
        flush()
        assertFalse(panel.cloudDeleteVisible(0))
    }

    private fun controller() = HistoryController(sessions, workspace, scope)

    private fun collect(controller: HistoryController): MutableList<HistoryModelEvent> {
        val events = mutableListOf<HistoryModelEvent>()
        controller.model.addListener(parent) { event ->
            assertTrue(ApplicationManager.getApplication().isDispatchThread)
            events.add(event)
        }
        return events
    }

    private fun flush() = runBlocking {
        repeat(5) {
            delay(100)
            ApplicationManager.getApplication().invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
        }
    }

    private fun session(id: String, title: String, updated: Double = 2.0) = SessionDto(
        id = id,
        projectID = "prj",
        directory = "/test",
        title = title,
        version = "1",
        time = SessionTimeDto(created = 1.0, updated = updated),
    )

    private fun cloud(id: String, title: String) = CloudSessionDto(
        id = id,
        title = title,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-02T00:00:00Z",
        version = 1.0,
    )
}
