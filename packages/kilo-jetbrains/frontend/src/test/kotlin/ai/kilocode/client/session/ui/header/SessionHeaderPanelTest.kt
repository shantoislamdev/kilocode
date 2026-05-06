package ai.kilocode.client.session.ui.header

import ai.kilocode.client.session.model.Reasoning
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.ToolKind
import ai.kilocode.client.session.ui.SessionStyle
import ai.kilocode.client.session.update.SessionControllerTestBase
import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.MessageDto
import ai.kilocode.rpc.dto.MessageTimeDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.PartDto
import ai.kilocode.rpc.dto.PartTimeDto
import ai.kilocode.rpc.dto.ProviderDto
import ai.kilocode.rpc.dto.TodoDto
import ai.kilocode.rpc.dto.TokensDto
import java.awt.Color
import java.awt.event.MouseEvent

class SessionHeaderPanelTest : SessionControllerTestBase() {

    fun `test starts hidden for empty header`() {
        appRpc.state.value = ai.kilocode.rpc.dto.KiloAppStateDto(ai.kilocode.rpc.dto.KiloAppStatusDto.READY)
        projectRpc.state.value = workspaceReady()
        val c = controller()
        flush()
        val panel = SessionHeaderPanel(c, parent)

        assertFalse(panel.isVisible)
        assertEquals("New Session", panel.titleText())
    }

    fun `test shows populated session header`() {
        val c = promptedHeader()
        val panel = SessionHeaderPanel(c, parent)
        val style = SessionStyle.current()

        assertTrue(panel.isVisible)
        assertFalse(panel.isExpanded())
        assertEquals("Generated title", panel.titleText())
        assertEquals("$0.07", panel.costText())
        assertEquals("1%", panel.contextText())
        assertEquals("Tokens 13.7K 2.5K cache 75", panel.tokenText())
        assertEquals("Tokens used by the latest assistant response: input, output, and cached prompt tokens.", panel.tokenTip())
        assertEquals("13.7K", panel.inputTokenText())
        assertEquals("2.5K", panel.outputTokenText())
        assertEquals("cache 75", panel.cacheReadText())
        assertEquals("1/2 todos complete", panel.todoText())
        assertTrue(panel.todoVisible())
        assertEquals(style.editorBackground, panel.background)
        assertEquals(
            List(panel.foregrounds().size) { style.editorForeground },
            panel.foregrounds(),
        )
        assertNotNull(panel.expandButton().icon)
    }

    fun `test compact button follows eligibility and invokes controller`() {
        val c = promptedHeader()
        val panel = SessionHeaderPanel(c, parent)

        assertTrue(panel.compactButton().isEnabled)
        panel.compactButton().doClick()
        flush()
        assertEquals(1, rpc.compacts.size)

        emit(ChatEventDto.TurnOpen("ses_test"))
        assertFalse(panel.compactButton().isEnabled)
        panel.compactButton().doClick()
        flush()
        assertEquals(1, rpc.compacts.size)
    }

    fun `test retained labels update on later header event`() {
        val c = promptedHeader()
        val panel = SessionHeaderPanel(c, parent)
        val button = panel.compactButton()

        emit(ChatEventDto.SessionUpdated("ses_test", session("ses_test", title = "New title")))
        emit(ChatEventDto.MessageUpdated("ses_test", assistant(cost = 0.2, tokens = TokensDto(1_000, 500, 0, 0, 0))))

        assertSame(button, panel.compactButton())
        assertEquals("New title", panel.titleText())
        assertEquals("$0.20", panel.costText())
        assertEquals("Tokens 1.0K 500", panel.tokenText())
        assertEquals("1.0K", panel.inputTokenText())
        assertEquals("500", panel.outputTokenText())
    }

    fun `test apply style updates header colors`() {
        val c = promptedHeader()
        val panel = SessionHeaderPanel(c, parent)
        val style = SessionStyle.current().copy(
            editorForeground = Color(1, 2, 3),
            editorBackground = Color(4, 5, 6),
        )

        panel.applyStyle(style)
        panel.expandButton().doClick()

        assertEquals(style.editorBackground, panel.background)
        assertEquals(
            List(panel.foregrounds().size) { style.editorForeground },
            panel.foregrounds(),
        )
        assertEquals(
            List(panel.contextBarForegrounds().size) { style.editorForeground },
            panel.contextBarForegrounds(),
        )
    }

    fun `test expanded body shows timeline context and token metrics`() {
        val c = promptedHeader()
        val panel = SessionHeaderPanel(c, parent)
        val style = SessionStyle.current()
        val body = panel.bodyPanel()
        val timeline = panel.timelinePanel()
        val bar = panel.contextBar()

        assertFalse(panel.isExpanded())

        panel.expandButton().doClick()

        assertTrue(panel.isExpanded())
        assertSame(body, panel.bodyPanel())
        assertSame(timeline, panel.timelinePanel())
        assertSame(bar, panel.contextBar())
        assertEquals(listOf(panel.timelineScroll(), panel.tokenPanel(), bar), panel.bodyComponents().take(3))
        assertEquals(3, panel.timelineCount())
        val parts = panel.timelineParts()
        assertTrue(parts[0] is Reasoning)
        assertEquals("bash", (parts[1] as Tool).name)
        assertEquals(ToolKind.GENERIC, (parts[1] as Tool).kind)
        assertEquals(ToolExecState.ERROR, (parts[2] as Tool).state)
        assertTrue(panel.timelineActive(0))
        assertTrue(panel.timelineActive(1))
        assertFalse(panel.timelineActive(2))
        assertTrue(panel.contextBarVisible())
        assertEquals(16_300L, panel.contextBarUsed())
        assertEquals(200_000L, panel.contextBarReserved())
        assertEquals(1_783_700L, panel.contextBarAvailable())
        assertEquals(2_000_000L, panel.contextBarLimit())
        assertEquals(
            List(panel.contextBarForegrounds().size) { style.editorForeground },
            panel.contextBarForegrounds(),
        )
        assertEquals("16.3K / 2.0M tokens used\n200.0K reserved for output\n1.8M available", panel.contextBarTip())
        assertNotSame(panel.contextBarTrackColor(), panel.contextBarReservedColor())
        assertNotSame(panel.contextBarUsedColor(), panel.contextBarReservedColor())
        assertEquals(panel.timelineScrollPreferredSize().height, panel.timelinePreferredSize().height)
        assertTrue(panel.timelinePreferredSize().height >= panel.contextBar().preferredSize.height)
        assertTrue(panel.timelineBarHeight(1) < panel.timelineScrollPreferredSize().height)
        assertTrue(panel.timelineBarHeight(0) < panel.timelineBarHeight(1))
        assertEquals(panel.timelineBarHeight(1), panel.timelineBarHeight(2))

        timeline.dispatchEvent(MouseEvent(
            timeline,
            MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(),
            0,
            panel.timelineBarWidth() + 1,
            panel.timelineScrollPreferredSize().height - 1,
            0,
            false,
        ))
        assertEquals("Run tests", panel.timelineToolTip())
        assertEquals(1, panel.timelineHover())
        timeline.dispatchEvent(MouseEvent(
            timeline,
            MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(),
            0,
            panel.timelineBarWidth() - 1,
            0,
            0,
            false,
        ))
        assertNull(panel.timelineToolTip())
        assertEquals(-1, panel.timelineHover())

        panel.expandButton().doClick()

        assertFalse(panel.isExpanded())
        assertSame(body, panel.bodyPanel())
        assertSame(timeline, panel.timelinePanel())
        assertSame(bar, panel.contextBar())
        assertEquals(3, panel.timelineCount())
    }

    fun `test expand button owns expanded state across updates`() {
        val c = promptedHeader()
        val panel = SessionHeaderPanel(c, parent)

        assertFalse(panel.isExpanded())
        assertEquals("Show session metrics", panel.expandTip())

        panel.expandButton().doClick()
        emit(ChatEventDto.SessionUpdated("ses_test", session("ses_test", title = "New title")))

        assertTrue(panel.isExpanded())
        assertEquals("Hide session metrics", panel.expandTip())

        panel.expandButton().doClick()
        emit(ChatEventDto.MessageUpdated("ses_test", assistant(cost = 0.2)))

        assertFalse(panel.isExpanded())
        assertEquals("Show session metrics", panel.expandTip())
    }

    fun `test context bar uses neutral grey colors`() {
        val c = promptedHeader()
        val panel = SessionHeaderPanel(c, parent)
        val color = panel.contextBarUsedColor()

        emit(ChatEventDto.MessageUpdated("ses_test", assistant(tokens = TokensDto(1_200_000, 0, 0, 0, 0))))

        assertEquals(color, panel.contextBarUsedColor())
        assertNotSame(panel.contextBarTrackColor(), panel.contextBarUsedColor())
        assertNotSame(panel.contextBarTrackColor(), panel.contextBarReservedColor())
        assertNotSame(panel.contextBarUsedColor(), panel.contextBarReservedColor())
    }

    fun `test timeline width uses uniform bars and gaps`() {
        val c = promptedHeader()
        val panel = SessionHeaderPanel(c, parent)
        val first = panel.timelinePreferredSize().width

        emit(ChatEventDto.PartUpdated("ses_test", tool("tool_3", "bash", "running", "Short")))

        val next = panel.timelinePreferredSize().width
        assertTrue(first > 0)
        assertEquals(4, panel.timelineCount())
        assertEquals(panel.timelineBarWidth(), next - first)
    }

    private fun promptedHeader(): ai.kilocode.client.session.update.SessionController {
        appRpc.state.value = ai.kilocode.rpc.dto.KiloAppStateDto(
            ai.kilocode.rpc.dto.KiloAppStatusDto.READY,
            config = ai.kilocode.rpc.dto.ConfigDto(model = "kilo/gpt-5"),
        )
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(
                            id = "gpt-5",
                            name = "GPT-5",
                            limit = ai.kilocode.rpc.dto.ModelLimitDto(context = 2_000_000, output = 200_000),
                        ),
                    ),
                ),
            ),
        )
        val c = controller()
        flush()
        edt { c.prompt("go") }
        flush()

        emit(ChatEventDto.SessionUpdated("ses_test", session("ses_test", title = "Generated title")))
        emit(ChatEventDto.MessageUpdated("ses_test", assistant()))
        emit(ChatEventDto.PartUpdated("ses_test", reasoning(done = false, text = "Thinking")))
        emit(ChatEventDto.PartUpdated("ses_test", tool("tool_1", "bash", "running", "Run tests", input = mapOf("cmd" to "test", "files" to "src"))))
        emit(ChatEventDto.PartUpdated("ses_test", tool("tool_2", "edit", "error", "Edit file", input = mapOf("cmd" to "test", "files" to "src"))))
        emit(ChatEventDto.TodoUpdated("ses_test", listOf(
            TodoDto("Write tests", "completed", "high"),
            TodoDto("Ship it", "pending", "medium"),
        )))
        return c
    }

    private fun assistant(
        cost: Double = 0.07,
        tokens: TokensDto = TokensDto(13_700, 2_000, 500, 75, 25),
    ) = MessageDto(
        id = "msg1",
        sessionID = "ses_test",
        role = "assistant",
        time = MessageTimeDto(created = 0.0),
        cost = cost,
        tokens = tokens,
    )

    private fun reasoning(done: Boolean, text: String) = PartDto(
        id = "reasoning_1",
        sessionID = "ses_test",
        messageID = "msg1",
        type = "reasoning",
        text = text,
        time = if (done) PartTimeDto(1.0, 2.0) else PartTimeDto(1.0, null),
    )

    private fun tool(
        id: String,
        name: String,
        state: String,
        title: String,
        input: Map<String, String> = mapOf("cmd" to "test"),
    ) = PartDto(
        id = id,
        sessionID = "ses_test",
        messageID = "msg1",
        type = "tool",
        tool = name,
        state = state,
        title = title,
        input = input,
        time = PartTimeDto(1.0, 3.0),
    )
}
