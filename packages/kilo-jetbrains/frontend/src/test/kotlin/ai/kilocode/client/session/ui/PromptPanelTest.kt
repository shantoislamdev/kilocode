package ai.kilocode.client.session.ui

import com.intellij.icons.AllIcons
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.EmptyIcon
import javax.swing.SwingUtilities

@Suppress("UnstableApiUsage")
class PromptPanelTest : BasePlatformTestCase() {

    fun `test prompt input uses editor font settings`() {
        val style = SessionStyle.current()
        val panel = PromptPanel(project, {}, {})
        val font = panel.inputFont()

        assertEquals(style.editorFamily, font.name)
        assertEquals(style.editorSize, font.size)
    }

    fun `test applyStyle updates prompt input and height`() {
        val panel = PromptPanel(project, {}, {})
        val style = SessionStyle.create(family = "Courier New", size = 26)

        panel.applyStyle(style)

        assertEquals("Courier New", panel.inputFont().name)
        assertEquals(26, panel.inputFont().size)
        assertTrue(panel.preferredSize.height >= 26)
    }

    fun `test reasoning picker hides when variants are empty`() {
        val panel = PromptPanel(project, {}, {})

        panel.reasoning.setItems(emptyList())

        assertFalse(panel.reasoning.isVisible)
    }

    fun `test reasoning picker shows selected variant`() {
        val panel = PromptPanel(project, {}, {})

        panel.reasoning.setItems(listOf(ReasoningPicker.Item("low", "Low"), ReasoningPicker.Item("high", "High")), "high")

        assertTrue(panel.reasoning.isVisible)
        assertEquals("high", panel.reasoning.selectedForTest()?.id)
    }

    fun `test reasoning picker aligns unchecked rows`() {
        val picker = ReasoningPicker()
        val low = ReasoningPicker.Item("low", "Low")
        val high = ReasoningPicker.Item("high", "High")

        picker.setItems(listOf(low, high), "high")

        val icon = picker.iconForTest(low)
        assertTrue(icon is EmptyIcon)
        assertSame(AllIcons.Actions.Checked, picker.iconForTest(high))
        assertEquals(AllIcons.Actions.Checked.iconWidth, icon.iconWidth)
        assertEquals(AllIcons.Actions.Checked.iconHeight, icon.iconHeight)
    }

    fun `test reset visibility can be toggled`() {
        val panel = PromptPanel(project, {}, {})

        panel.setResetVisible(true)

        assertTrue(panel.resetVisibleForTest())
    }

    fun `test pickers belong to rounded shell`() {
        val panel = PromptPanel(project, {}, {})
        val shell = panel.shellForTest()

        assertTrue(SwingUtilities.isDescendingFrom(panel.mode, shell))
        assertTrue(SwingUtilities.isDescendingFrom(panel.model, shell))
        assertTrue(SwingUtilities.isDescendingFrom(panel.reasoning, shell))
        assertSame(shell, panel.mode.parent.parent)
    }
}
