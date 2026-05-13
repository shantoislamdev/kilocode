package ai.kilocode.client.session.scroll

import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

internal object ScrollButtonIcon : Icon {
    private val size get() = JBUI.scale(40)
    private val radius get() = JBUI.scale(18)

    override fun getIconWidth() = size
    override fun getIconHeight() = size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.translate(x, y)
            val cx = size / 2
            val cy = size / 2
            val r = radius
            g2.color = JBUI.CurrentTheme.Button.defaultButtonColorStart()
            g2.fillOval(cx - r, cy - r, r * 2, r * 2)
            g2.color = JBUI.CurrentTheme.Button.defaultButtonForeground()
            val stroke = JBUI.scale(1).toFloat() * 1.5f
            g2.stroke = BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val x0 = JBUI.scale(20)
            val lineTop = JBUI.scale(12)
            val lineBot = JBUI.scale(27)
            val arrowY = JBUI.scale(21)
            val arrowLeft = JBUI.scale(14)
            val arrowRight = JBUI.scale(26)
            g2.drawLine(x0, lineTop, x0, lineBot)
            g2.drawLine(x0, lineBot, arrowLeft, arrowY)
            g2.drawLine(x0, lineBot, arrowRight, arrowY)
        } finally {
            g2.dispose()
        }
    }
}
