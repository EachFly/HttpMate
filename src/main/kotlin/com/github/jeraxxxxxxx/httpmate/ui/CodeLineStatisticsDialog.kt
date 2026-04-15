package com.github.jeraxxxxxxx.httpmate.ui

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.actions.FileStats
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.text.DecimalFormat
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * 代码行统计结果对话框
 *
 * 以表格形式展示按文件类型分组的统计数据，
 * 包括总行数、代码行、注释行、空行以及各部分占比。
 */
class CodeLineStatisticsDialog(
    project: Project,
    private val directoryName: String,
    private val fileStatsList: List<FileStats>
) : DialogWrapper(project) {

    init {
        title = HttpMateBundle.message("code.stats.dialog.title", directoryName)
        init()
    }

    override fun getDimensionServiceKey(): String {
        return "HttpMate.CodeLineStatisticsDialog"
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, 12))
        mainPanel.preferredSize = Dimension(780, 520)
        mainPanel.border = EmptyBorder(8, 8, 8, 8)

        // ==== Summary Panel ====
        val summaryPanel = createSummaryPanel()
        mainPanel.add(summaryPanel, BorderLayout.NORTH)

        // ==== Detail Table ====
        val detailPanel = createDetailTable()
        mainPanel.add(detailPanel, BorderLayout.CENTER)

        return mainPanel
    }

    /**
     * 创建汇总面板，展示总体统计数据
     */
    private fun createSummaryPanel(): JPanel {
        val totalFiles = fileStatsList.size
        val totalLines = fileStatsList.sumOf { it.totalLines }
        val totalCode = fileStatsList.sumOf { it.codeLines }
        val totalComments = fileStatsList.sumOf { it.commentLines }
        val totalBlanks = fileStatsList.sumOf { it.blankLines }
        val extensionCount = fileStatsList.map { it.extension }.distinct().size
        val pctFormat = DecimalFormat("0.0")

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                HttpMateBundle.message("code.stats.summary.title")
            ),
            EmptyBorder(8, 12, 8, 12)
        )

        // Summary grid
        val gridPanel = JPanel(java.awt.GridLayout(3, 4, 20, 6))

        fun addStatCard(label: String, value: String) {
            val card = JPanel(BorderLayout(4, 0))
            card.isOpaque = false
            val lbl = JLabel(label)
            lbl.font = lbl.font.deriveFont(Font.PLAIN, 12f)
            lbl.foreground = Color.GRAY
            card.add(lbl, BorderLayout.WEST)

            val val1 = JLabel(value)
            val1.font = val1.font.deriveFont(Font.BOLD, 13f)
            card.add(val1, BorderLayout.EAST)
            gridPanel.add(card)
        }

        addStatCard(
            HttpMateBundle.message("code.stats.label.total.files"),
            totalFiles.toString()
        )
        addStatCard(
            HttpMateBundle.message("code.stats.label.file.types"),
            extensionCount.toString()
        )
        addStatCard(
            HttpMateBundle.message("code.stats.label.total.lines"),
            "%,d".format(totalLines)
        )
        addStatCard("", "") // spacer

        addStatCard(
            HttpMateBundle.message("code.stats.label.code.lines"),
            "%,d (%s%%)".format(totalCode, pctFormat.format(safePercent(totalCode, totalLines)))
        )
        addStatCard(
            HttpMateBundle.message("code.stats.label.comment.lines"),
            "%,d (%s%%)".format(totalComments, pctFormat.format(safePercent(totalComments, totalLines)))
        )
        addStatCard(
            HttpMateBundle.message("code.stats.label.blank.lines"),
            "%,d (%s%%)".format(totalBlanks, pctFormat.format(safePercent(totalBlanks, totalLines)))
        )
        addStatCard("", "") // spacer

        panel.add(gridPanel)

        // Progress bar visualization
        if (totalLines > 0) {
            panel.add(Box.createVerticalStrut(10))
            val barPanel = createStackedBar(totalCode, totalComments, totalBlanks, totalLines)
            panel.add(barPanel)
        }

        return panel
    }

    /**
     * 创建比例可视化条形图
     */
    private fun createStackedBar(code: Int, comments: Int, blanks: Int, total: Int): JPanel {
        val panel = JPanel(BorderLayout(0, 4))
        panel.isOpaque = false

        val bar = object : JPanel() {
            override fun paintComponent(g: java.awt.Graphics) {
                super.paintComponent(g)
                val g2 = g as java.awt.Graphics2D
                g2.setRenderingHint(
                    java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                )

                val w = width
                val h = height
                val codeW = (code.toDouble() / total * w).toInt()
                val commentW = (comments.toDouble() / total * w).toInt()
                val blankW = w - codeW - commentW

                // Code - Blue
                g2.color = Color(66, 133, 244)
                g2.fillRoundRect(0, 0, codeW, h, 6, 6)

                // Comment - Green
                g2.color = Color(52, 168, 83)
                g2.fillRect(codeW, 0, commentW, h)

                // Blank - Gray
                g2.color = Color(189, 189, 189)
                g2.fillRoundRect(codeW + commentW, 0, blankW, h, 6, 6)
                // Fix corners: fill rect for middle sections
                if (blankW > 0) g2.fillRect(codeW + commentW, 0, 3, h)
                if (codeW > 0) g2.fillRect(codeW - 1, 0, 2, h)
            }
        }
        bar.preferredSize = Dimension(0, 14)
        panel.add(bar, BorderLayout.CENTER)

        // Legend
        val legendPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 16, 0))
        legendPanel.isOpaque = false

        fun addLegend(label: String, color: Color) {
            val dot = object : JLabel("  ") {
                override fun paintComponent(g: java.awt.Graphics) {
                    super.paintComponent(g)
                    g.color = color
                    g.fillOval(0, 2, 10, 10)
                }
            }
            dot.preferredSize = Dimension(14, 14)
            legendPanel.add(dot)
            legendPanel.add(JLabel(label).apply {
                font = font.deriveFont(Font.PLAIN, 11f)
            })
        }

        addLegend(HttpMateBundle.message("code.stats.legend.code"), Color(66, 133, 244))
        addLegend(HttpMateBundle.message("code.stats.legend.comment"), Color(52, 168, 83))
        addLegend(HttpMateBundle.message("code.stats.legend.blank"), Color(189, 189, 189))

        panel.add(legendPanel, BorderLayout.SOUTH)
        return panel
    }

    /**
     * 创建按文件类型分组的详细统计表格
     */
    private fun createDetailTable(): JComponent {
        // Group by extension
        val grouped = fileStatsList.groupBy { it.extension }
            .map { (ext, files) ->
                ExtensionStats(
                    extension = ".$ext",
                    fileCount = files.size,
                    totalLines = files.sumOf { it.totalLines },
                    codeLines = files.sumOf { it.codeLines },
                    commentLines = files.sumOf { it.commentLines },
                    blankLines = files.sumOf { it.blankLines }
                )
            }
            .sortedByDescending { it.totalLines }

        val model = StatsTableModel(grouped)
        val table = JBTable(model)
        table.setShowGrid(true)
        table.rowHeight = 26
        table.autoCreateRowSorter = true

        // Custom renderer for percentage columns
        val pctRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean,
                hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                horizontalAlignment = SwingConstants.RIGHT
                if (value is String && value.endsWith("%")) {
                    foreground = if (isSelected) table.selectionForeground else Color(100, 100, 100)
                }
                return comp
            }
        }

        val rightRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean,
                hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                horizontalAlignment = SwingConstants.RIGHT
                return comp
            }
        }

        // Set column widths and renderers
        val columnModel = table.columnModel
        columnModel.getColumn(0).preferredWidth = 90   // Extension
        columnModel.getColumn(1).preferredWidth = 60   // File Count
        columnModel.getColumn(1).cellRenderer = rightRenderer
        columnModel.getColumn(2).preferredWidth = 80   // Total Lines
        columnModel.getColumn(2).cellRenderer = rightRenderer
        columnModel.getColumn(3).preferredWidth = 80   // Code Lines
        columnModel.getColumn(3).cellRenderer = rightRenderer
        columnModel.getColumn(4).preferredWidth = 70   // Code %
        columnModel.getColumn(4).cellRenderer = pctRenderer
        columnModel.getColumn(5).preferredWidth = 80   // Comment Lines
        columnModel.getColumn(5).cellRenderer = rightRenderer
        columnModel.getColumn(6).preferredWidth = 70   // Comment %
        columnModel.getColumn(6).cellRenderer = pctRenderer
        columnModel.getColumn(7).preferredWidth = 80   // Blank Lines
        columnModel.getColumn(7).cellRenderer = rightRenderer
        columnModel.getColumn(8).preferredWidth = 70   // Blank %
        columnModel.getColumn(8).cellRenderer = pctRenderer

        val scrollPane = JBScrollPane(table)
        scrollPane.border = BorderFactory.createTitledBorder(
            HttpMateBundle.message("code.stats.detail.title")
        )
        return scrollPane
    }

    private fun safePercent(part: Int, total: Int): Double {
        return if (total == 0) 0.0 else part.toDouble() / total * 100
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    // ==== Inner Classes ====

    private data class ExtensionStats(
        val extension: String,
        val fileCount: Int,
        val totalLines: Int,
        val codeLines: Int,
        val commentLines: Int,
        val blankLines: Int
    )

    private class StatsTableModel(private val data: List<ExtensionStats>) : AbstractTableModel() {
        private val pctFormat = DecimalFormat("0.0")

        private val columns = arrayOf(
            HttpMateBundle.message("code.stats.col.extension"),
            HttpMateBundle.message("code.stats.col.files"),
            HttpMateBundle.message("code.stats.col.total"),
            HttpMateBundle.message("code.stats.col.code"),
            HttpMateBundle.message("code.stats.col.code.pct"),
            HttpMateBundle.message("code.stats.col.comment"),
            HttpMateBundle.message("code.stats.col.comment.pct"),
            HttpMateBundle.message("code.stats.col.blank"),
            HttpMateBundle.message("code.stats.col.blank.pct")
        )

        override fun getRowCount(): Int = data.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val stat = data[rowIndex]
            val total = stat.totalLines
            return when (columnIndex) {
                0 -> stat.extension
                1 -> "%,d".format(stat.fileCount)
                2 -> "%,d".format(stat.totalLines)
                3 -> "%,d".format(stat.codeLines)
                4 -> "${pctFormat.format(safePercent(stat.codeLines, total))}%"
                5 -> "%,d".format(stat.commentLines)
                6 -> "${pctFormat.format(safePercent(stat.commentLines, total))}%"
                7 -> "%,d".format(stat.blankLines)
                8 -> "${pctFormat.format(safePercent(stat.blankLines, total))}%"
                else -> ""
            }
        }

        private fun safePercent(part: Int, total: Int): Double {
            return if (total == 0) 0.0 else part.toDouble() / total * 100
        }
    }
}
