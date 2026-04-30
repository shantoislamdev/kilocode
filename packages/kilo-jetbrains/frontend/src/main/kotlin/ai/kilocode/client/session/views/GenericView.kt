package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Generic
import ai.kilocode.client.session.ui.SessionStyle
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout

/**
 * Fallback renderer for part types that have no dedicated view.
 *
 * Rather than silently dropping unknown content (which could lead to
 * confusing empty gaps), this shows a dim label with the raw type name.
 * This makes it easy to spot new part types that need a proper renderer.
 */
class GenericView(content: Generic) : PartView() {

    override val contentId: String = content.id

    private val label = JBLabel("[${content.type}]").apply {
        foreground = SessionStyle.Colors.weak()
        font = SessionStyle.Fonts.smallUiFont()
        border = com.intellij.util.ui.JBUI.Borders.empty(SessionStyle.Space.XS, 0)
    }

    init {
        layout = BorderLayout()
        isOpaque = false
        add(label, BorderLayout.CENTER)
    }

    override fun update(content: Content) {}  // generic content has no updatable state

    /** Exposed for tests. */
    fun labelText(): String = label.text

    override fun dumpLabel() = "GenericView#$contentId(${label.text})"
}
