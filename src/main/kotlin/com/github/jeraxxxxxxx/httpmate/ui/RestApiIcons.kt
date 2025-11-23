package com.github.jeraxxxxxxx.httpmate.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

class MethodIcon(private val method: String) : Icon {
    private val size = 10

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g as Graphics2D
        val originalAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g2.color = getColor(method)
        g2.fillOval(x, y + (iconHeight - size) / 2, size, size)

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing ?: RenderingHints.VALUE_ANTIALIAS_OFF)
    }

    override fun getIconWidth(): Int = size + 4 // Add some padding
    override fun getIconHeight(): Int = JBUI.scale(16)

    private fun getColor(method: String): JBColor {
        return when (method.uppercase()) {
            "GET" -> JBColor(0x61affe, 0x61affe) // Blue
            "POST" -> JBColor(0x49cc90, 0x49cc90) // Green
            "PUT" -> JBColor(0xfca130, 0xfca130) // Orange
            "DELETE" -> JBColor(0xf93e3e, 0xf93e3e) // Red
            "PATCH" -> JBColor(0x50e3c2, 0x50e3c2) // Teal
            "HEAD" -> JBColor(0x9012fe, 0x9012fe) // Purple
            "OPTIONS" -> JBColor(0x0d5aa7, 0x0d5aa7) // Dark Blue
            else -> JBColor.GRAY
        }
    }
}

object RestApiIcons {
    fun getIcon(method: String): Icon {
        return MethodIcon(method)
    }
}
