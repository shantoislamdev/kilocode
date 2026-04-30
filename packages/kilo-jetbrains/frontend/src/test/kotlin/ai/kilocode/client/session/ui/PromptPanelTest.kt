package ai.kilocode.client.session.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase

@Suppress("UnstableApiUsage")
class PromptPanelTest : BasePlatformTestCase() {

    fun `test prompt input uses editor font settings`() {
        val panel = PromptPanel(project, {}, {})
        val font = panel.inputFont()

        assertEquals(SessionStyle.Fonts.editorFamily(), font.name)
        assertEquals(SessionStyle.Fonts.editorSize(), font.size)
    }
}
