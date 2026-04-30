package ai.kilocode.client.session.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font

@Suppress("UnstableApiUsage")
class SessionStyleTest : BasePlatformTestCase() {

    fun `test transcript font uses editor settings`() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val font = SessionStyle.Fonts.transcriptFont()

        assertEquals(scheme.editorFontName, font.name)
        assertEquals(scheme.editorFontSize, font.size)
        assertEquals(Font.PLAIN, font.style)
    }

    fun `test bold editor font uses editor family and size`() {
        val font = SessionStyle.Fonts.boldEditorFont()

        assertEquals(SessionStyle.Fonts.editorFamily(), font.name)
        assertEquals(SessionStyle.Fonts.editorSize(), font.size)
        assertTrue(font.isBold)
    }

    fun `test small editor font uses editor family with smaller editor-derived size`() {
        val font = SessionStyle.Fonts.smallEditorFont()

        assertEquals(SessionStyle.Fonts.editorFamily(), font.name)
        assertTrue(font.size < SessionStyle.Fonts.editorSize())
    }
}
