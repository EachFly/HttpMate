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

        // ==== Detail Tables ====
        val detailPanel = createDetailTables()
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
     * 创建详细统计表格组件，包含按类型和按文件两个标签页
     */
    private fun createDetailTables(): JComponent {
        val tabbedPane = com.intellij.ui.components.JBTabbedPane()

        // ==== Custom Renderers ====
        val pctRenderer = object : DefaultTableCellRenderer() {
            private val format = DecimalFormat("0.0'%'")
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean,
                hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val formatted = if (value is Double) format.format(value) else value
                val comp = super.getTableCellRendererComponent(table, formatted, isSelected, hasFocus, row, column)
                horizontalAlignment = SwingConstants.RIGHT
                foreground = if (isSelected) table.selectionForeground else Color(100, 100, 100)
                return comp
            }
        }

        val rightRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean,
                hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val formatted = if (value is Number) "%,d".format(value.toLong()) else value
                val comp = super.getTableCellRendererComponent(table, formatted, isSelected, hasFocus, row, column)
                horizontalAlignment = SwingConstants.RIGHT
                return comp
            }
        }

        // ==== Tab 1: By Extension ====
        val grouped = fileStatsList.groupBy { it.extension }
            .map { (ext, files) ->
                ExtensionStats(
                    extension = if (ext.isNotEmpty()) ".$ext" else "unknown",
                    fileCount = files.size,
                    totalLines = files.sumOf { it.totalLines },
                    codeLines = files.sumOf { it.codeLines },
                    commentLines = files.sumOf { it.commentLines },
                    blankLines = files.sumOf { it.blankLines }
                )
            }
            .sortedByDescending { it.totalLines }

        val extModel = StatsTableModel(grouped)
        val extTable = JBTable(extModel)
        extTable.setShowGrid(true)
        extTable.rowHeight = 26
        extTable.autoCreateRowSorter = true

        val extColModel = extTable.columnModel
        extColModel.getColumn(0).preferredWidth = 90
        extColModel.getColumn(1).preferredWidth = 60
        extColModel.getColumn(1).cellRenderer = rightRenderer
        extColModel.getColumn(2).preferredWidth = 80
        extColModel.getColumn(2).cellRenderer = rightRenderer
        extColModel.getColumn(3).preferredWidth = 80
        extColModel.getColumn(3).cellRenderer = rightRenderer
        extColModel.getColumn(4).preferredWidth = 70
        extColModel.getColumn(4).cellRenderer = pctRenderer
        extColModel.getColumn(5).preferredWidth = 80
        extColModel.getColumn(5).cellRenderer = rightRenderer
        extColModel.getColumn(6).preferredWidth = 70
        extColModel.getColumn(6).cellRenderer = pctRenderer
        extColModel.getColumn(7).preferredWidth = 80
        extColModel.getColumn(7).cellRenderer = rightRenderer
        extColModel.getColumn(8).preferredWidth = 70
        extColModel.getColumn(8).cellRenderer = pctRenderer

        val extScrollPane = JBScrollPane(extTable)
        extScrollPane.border = BorderFactory.createEmptyBorder()
        
        tabbedPane.addTab(
            try { HttpMateBundle.message("code.stats.tab.by.extension") } catch (_: Exception) { "By File Type" },
            extScrollPane
        )

        // ==== Tab 2: By File ====
        val sortedFiles = fileStatsList.sortedByDescending { it.totalLines }
        val overallTotalLines = fileStatsList.sumOf { it.totalLines }
        val fileModel = FileStatsTableModel(sortedFiles, overallTotalLines)
        val fileTable = JBTable(fileModel)
        fileTable.setShowGrid(true)
        fileTable.rowHeight = 26
        fileTable.autoCreateRowSorter = true

        val fileColModel = fileTable.columnModel
        fileColModel.getColumn(0).preferredWidth = 120 // File Name
        fileColModel.getColumn(1).preferredWidth = 80  // Total Lines
        fileColModel.getColumn(1).cellRenderer = rightRenderer
        fileColModel.getColumn(2).preferredWidth = 70  // % of total
        fileColModel.getColumn(2).cellRenderer = pctRenderer
        fileColModel.getColumn(3).preferredWidth = 80  // Code Lines
        fileColModel.getColumn(3).cellRenderer = rightRenderer
        fileColModel.getColumn(4).preferredWidth = 70  // Code %
        fileColModel.getColumn(4).cellRenderer = pctRenderer
        fileColModel.getColumn(5).preferredWidth = 80  // Comment Lines
        fileColModel.getColumn(5).cellRenderer = rightRenderer
        fileColModel.getColumn(6).preferredWidth = 70  // Comment %
        fileColModel.getColumn(6).cellRenderer = pctRenderer
        fileColModel.getColumn(7).preferredWidth = 80  // Blank Lines
        fileColModel.getColumn(7).cellRenderer = rightRenderer
        fileColModel.getColumn(8).preferredWidth = 70  // Blank %
        fileColModel.getColumn(8).cellRenderer = pctRenderer

        val fileScrollPane = JBScrollPane(fileTable)
        fileScrollPane.border = BorderFactory.createEmptyBorder()
        
        tabbedPane.addTab(
            try { HttpMateBundle.message("code.stats.tab.by.file") } catch (_: Exception) { "By File" },
            fileScrollPane
        )
        
        val wrapper = JPanel(BorderLayout())
        wrapper.border = BorderFactory.createTitledBorder("Details")
        wrapper.add(tabbedPane, BorderLayout.CENTER)
        return wrapper
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

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> String::class.java
                1, 2, 3, 5, 7 -> Int::class.javaObjectType
                4, 6, 8 -> Double::class.javaObjectType
                else -> Any::class.java
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val stat = data[rowIndex]
            val total = stat.totalLines
            return when (columnIndex) {
                0 -> stat.extension
                1 -> stat.fileCount
                2 -> stat.totalLines
                3 -> stat.codeLines
                4 -> safePercent(stat.codeLines, total)
                5 -> stat.commentLines
                6 -> safePercent(stat.commentLines, total)
                7 -> stat.blankLines
                8 -> safePercent(stat.blankLines, total)
                else -> ""
            }
        }

        private fun safePercent(part: Int, total: Int): Double {
            return if (total == 0) 0.0 else part.toDouble() / total * 100
        }
    }

    private class FileStatsTableModel(
        private val data: List<FileStats>,
        private val overallTotalLines: Int
    ) : AbstractTableModel() {
        private val columns = arrayOf(
            try { HttpMateBundle.message("code.stats.col.filename") } catch (_: Exception) { "File Name" },
            try { HttpMateBundle.message("code.stats.col.total") } catch (_: Exception) { "Total Lines" },
            try { HttpMateBundle.message("code.stats.col.pct.of.total") } catch (_: Exception) { "% of Total" },
            try { HttpMateBundle.message("code.stats.col.code") } catch (_: Exception) { "Code Lines" },
            try { HttpMateBundle.message("code.stats.col.code.pct") } catch (_: Exception) { "Code %" },
            try { HttpMateBundle.message("code.stats.col.comment") } catch (_: Exception) { "Comment Lines" },
            try { HttpMateBundle.message("code.stats.col.comment.pct") } catch (_: Exception) { "Comment %" },
            try { HttpMateBundle.message("code.stats.col.blank") } catch (_: Exception) { "Blank Lines" },
            try { HttpMateBundle.message("code.stats.col.blank.pct") } catch (_: Exception) { "Blank %" }
        )

        override fun getRowCount(): Int = data.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> String::class.java
                1, 3, 5, 7 -> Int::class.javaObjectType
                2, 4, 6, 8 -> Double::class.javaObjectType
                else -> Any::class.java
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val stat = data[rowIndex]
            val total = stat.totalLines
            return when (columnIndex) {
                0 -> stat.fileName
                1 -> stat.totalLines
                2 -> safePercent(stat.totalLines, overallTotalLines)
                3 -> stat.codeLines
                4 -> safePercent(stat.codeLines, total)
                5 -> stat.commentLines
                6 -> safePercent(stat.commentLines, total)
                7 -> stat.blankLines
                8 -> safePercent(stat.blankLines, total)
                else -> ""
            }
        }

        private fun safePercent(part: Int, total: Int): Double {
            return if (total == 0) 0.0 else part.toDouble() / total * 100
        }
    }
}
