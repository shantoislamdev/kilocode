package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.ui.SessionStyle
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [ToolView].
 */
@Suppress("UnstableApiUsage")
class ToolViewTest : BasePlatformTestCase() {

    // ---- state icons ------

    fun `test PENDING state shows pending label`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.PENDING))
        assertTrue(view.labelText().contains("Pending"))
    }

    fun `test RUNNING state shows running label`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.RUNNING))
        assertTrue(view.labelText().contains("Running"))
    }

    fun `test COMPLETED state hides state label`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.COMPLETED))
        assertFalse(view.labelText().contains("Completed"))
    }

    fun `test ERROR state shows error label`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.ERROR))
        assertTrue(view.labelText().contains("Error"))
    }

    // ---- display text ------

    fun `test tool name shown when no title`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.RUNNING))
        assertTrue(view.labelText().contains("Shell"))
    }

    fun `test title shown instead of name when title is set`() {
        val t = Tool("p1", "bash").also { it.state = ToolExecState.RUNNING; it.title = "Install deps" }
        val view = ToolView(t)
        assertTrue(view.labelText().contains("Install deps"))
        assertTrue(view.labelText().contains("Shell"))
    }

    fun `test blank title falls back to tool name`() {
        val t = Tool("p1", "bash").also { it.state = ToolExecState.COMPLETED; it.title = "   " }
        val view = ToolView(t)
        assertTrue(view.labelText().contains("Shell"))
    }

    fun `test read tool shows filename`() {
        val t = tool("p1", "read", ToolExecState.COMPLETED).also { it.input = mapOf("filePath" to "README.MD") }

        val view = ToolView(t)

        assertTrue(view.labelText().contains("Read"))
        assertTrue(view.labelText().contains("README.MD"))
    }

    fun `test bash tool shows subtitle command and output`() {
        val t = tool("p1", "bash", ToolExecState.COMPLETED).also {
            it.input = mapOf("command" to "git remote -v", "description" to "View remotes")
            it.output = "origin git@example.com:repo.git"
        }

        val view = ToolView(t)

        assertTrue(view.labelText().contains("Shell"))
        assertTrue(view.labelText().contains("View remotes"))
        assertEquals("git remote -v", view.commandText())
        assertEquals("origin git@example.com:repo.git", view.outputText())
        assertEquals("git remote -v\n\norigin git@example.com:repo.git", view.copyText())
        assertFalse(view.isExpanded())
        assertFalse(view.hasToggle())
    }

    fun `test bash toggle collapses and expands`() {
        val t = tool("p1", "bash", ToolExecState.COMPLETED).also {
            it.input = mapOf("command" to "git log")
            it.output = "one\ntwo\nthree\nfour"
        }
        val view = ToolView(t)

        assertFalse(view.isExpanded())
        view.toggle()
        assertTrue(view.isExpanded())
        view.toggle()
        assertFalse(view.isExpanded())
    }

    fun `test collapsed bash shows first three body lines`() {
        val t = tool("p1", "bash", ToolExecState.COMPLETED).also {
            it.input = mapOf("command" to "git log")
            it.output = "one\ntwo\nthree\nfour"
        }
        val view = ToolView(t)

        assertFalse(view.isExpanded())
        assertEquals("$ git log\n\none", view.previewText())
        assertEquals("$ git log\n\none\ntwo\nthree\nfour", view.bodyText())
        assertTrue(view.hasToggle())
    }

    fun `test short bash has no toggle and shows preview`() {
        val t = tool("p1", "bash", ToolExecState.COMPLETED).also {
            it.input = mapOf("command" to "pwd")
            it.output = "/tmp"
        }
        val view = ToolView(t)

        assertFalse(view.isExpanded())
        assertFalse(view.hasToggle())
        assertEquals("$ pwd\n\n/tmp", view.previewText())
        view.toggle()
        assertFalse(view.isExpanded())
    }

    fun `test bash output uses editor font settings`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.COMPLETED))

        assertEditorFont(view.bodyFont())
        assertEditorFont(view.previewFont())
    }

    fun `test tool header uses editor-derived fonts`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.COMPLETED))

        assertEditorFont(view.titleFont())
        assertTrue(view.titleFont().isBold)
        assertSmallEditorFont(view.subtitleFont())
        assertSmallEditorFont(view.stateFont())
    }

    // ---- update ------

    fun `test update changes state icon`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.RUNNING))
        val updated = Tool("p1", "bash").also { it.state = ToolExecState.COMPLETED }
        view.update(updated)
        assertFalse(view.labelText().contains("Running"))
    }

    fun `test update changes title`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.RUNNING, title = "old"))
        val updated = Tool("p1", "bash").also { it.state = ToolExecState.COMPLETED; it.title = "new title" }
        view.update(updated)
        assertTrue(view.labelText().contains("new title"))
    }

    fun `test update with non-Tool content is ignored`() {
        val view = ToolView(tool("p1", "bash", ToolExecState.RUNNING))
        val before = view.labelText()
        view.update(ai.kilocode.client.session.model.Text("p1"))
        assertEquals(before, view.labelText())
    }

    // ---- contentId ------

    fun `test contentId matches Tool id`() {
        val view = ToolView(Tool("part99", "edit").also { it.state = ToolExecState.PENDING })
        assertEquals("part99", view.contentId)
    }

    // ---- helpers ------

    private fun tool(id: String, name: String, state: ToolExecState, title: String? = null): Tool =
        Tool(id, name).also { it.state = state; it.title = title }

    private fun assertEditorFont(font: java.awt.Font) {
        assertEquals(SessionStyle.Fonts.editorFamily(), font.name)
        assertEquals(SessionStyle.Fonts.editorSize(), font.size)
    }

    private fun assertSmallEditorFont(font: java.awt.Font) {
        assertEquals(SessionStyle.Fonts.editorFamily(), font.name)
        assertTrue(font.size < SessionStyle.Fonts.editorSize())
    }
}
