package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.Centerizer
import java.awt.BorderLayout
import javax.swing.JPanel

class LoadingPanel : JPanel(BorderLayout()), SessionStyleTarget {
    private val label = JBLabel(KiloBundle.message("session.empty.loading"))

    init {
        isOpaque = false
        add(Centerizer(label, Centerizer.TYPE.BOTH), BorderLayout.CENTER)
        applyStyle(SessionStyle.current())
    }

    override fun applyStyle(style: SessionStyle) {
        label.font = style.uiFont
        revalidate()
        repaint()
    }
}
