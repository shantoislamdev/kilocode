package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Reasoning
import ai.kilocode.client.session.ui.SessionStyle
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@Suppress("UnstableApiUsage")
class ReasoningViewTest : BasePlatformTestCase() {

    fun `test completed reasoning is collapsed by default`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))

        assertFalse(view.isExpanded())
        assertEquals("Reasoning", view.headerText())
        assertEquals("one\ntwo\nthree\nfour", view.markdown())
        assertEquals("one\ntwo\nthree", view.previewMarkdown())
        assertTrue(view.hasToggle())
    }

    fun `test short completed reasoning has no toggle`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree"))

        assertFalse(view.isExpanded())
        assertFalse(view.hasToggle())
        assertEquals("one\ntwo\nthree", view.previewMarkdown())
        view.toggle()
        assertFalse(view.isExpanded())
    }

    fun `test streaming reasoning is collapsed by default`() {
        val view = ReasoningView(reasoning("p1", done = false, text = "one\ntwo\nthree\nfour"))

        assertFalse(view.isExpanded())
        assertTrue(view.hasToggle())
    }

    fun `test update to done collapses reasoning`() {
        val view = ReasoningView(reasoning("p1", done = false, text = "one\ntwo\nthree\nfour"))

        view.update(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))

        assertFalse(view.isExpanded())
        assertEquals("one\ntwo\nthree\nfour", view.markdown())
        assertEquals("one\ntwo\nthree", view.previewMarkdown())
    }

    fun `test toggle opens and closes reasoning`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))

        view.toggle()
        assertTrue(view.isExpanded())
        view.toggle()
        assertFalse(view.isExpanded())
    }

    fun `test appendDelta preserves markdown`() {
        val view = ReasoningView(reasoning("p1", done = false, text = "a"))

        view.appendDelta("b")

        assertEquals("ab", view.markdown())
    }

    fun `test reasoning markdown uses editor font settings`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))

        assertEditorSheet(view.md.overrideSheet())
        assertEditorSheet(view.previewSheet())
    }

    fun `test reasoning header uses smaller editor-derived font`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one"))
        val font = view.headerFont()

        assertEquals(SessionStyle.Fonts.editorFamily(), font.name)
        assertTrue(font.size < SessionStyle.Fonts.editorSize())
    }

    private fun assertEditorSheet(sheet: String) {
        assertTrue(sheet.contains(SessionStyle.Fonts.editorFamily()))
        assertTrue(sheet.contains("${SessionStyle.Fonts.editorSize()}pt"))
    }

    private fun reasoning(id: String, done: Boolean, text: String) = Reasoning(id).also {
        it.done = done
        it.content.append(text)
    }
}
