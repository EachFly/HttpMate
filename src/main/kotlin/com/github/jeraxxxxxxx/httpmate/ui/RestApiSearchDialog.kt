package com.github.jeraxxxxxxx.httpmate.ui

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.constants.AppConstants
import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * REST API 搜索对话框
 * 提供快速搜索和导航到 API 定义的功能
 */
class RestApiSearchDialog(private val project: Project, private val allItems: List<RestApiItem>) : DialogWrapper(project) {

    private val listModel = javax.swing.DefaultListModel<RestApiItem>()
    private val list = JBList(listModel)
    private val searchField = SearchTextField()
    private var currentQuery: String = ""

    private val searchAlarm = com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD, disposable)
    private val statusLabel = javax.swing.JLabel()

    init {
        init()
        title = HttpMateBundle.message("rest.search.title")
        updateList(allItems)
    }

    override fun getDimensionServiceKey(): String? {
        return "HttpMate.RestApiSearchDialog"
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.setPreferredSize(Dimension(600, 400))

        // Search Field
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                filter(searchField.text)
            }
        })
        
        // Handle Enter key in search field to select first item
        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    if (listModel.size > 0) {
                        list.selectedIndex = 0
                        doOKAction()
                    }
                } else if (e.keyCode == KeyEvent.VK_DOWN) {
                    list.requestFocus()
                    if (listModel.size > 0) list.selectedIndex = 0
                } else if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    doCancelAction()
                }
            }
        })

        panel.add(searchField, BorderLayout.NORTH)

        // List
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : ColoredListCellRenderer<RestApiItem>() {
            override fun customizeCellRenderer(
                list: JList<out RestApiItem>,
                value: RestApiItem,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                appendHighlighted(value.method, currentQuery, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append(" ")
                appendHighlighted(value.path, currentQuery, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                
                if (value.fileName.isNotEmpty()) {
                    append(" (${value.fileName})", SimpleTextAttributes.GRAY_ATTRIBUTES)
                }
                icon = value.icon
            }

            private val HIGHLIGHT_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, com.intellij.ui.JBColor.BLUE)

            private fun appendHighlighted(text: String, query: String, baseAttributes: SimpleTextAttributes) {
                if (query.isEmpty()) {
                    append(text, baseAttributes)
                    return
                }

                val lowerText = text.lowercase()
                val lowerQuery = query.lowercase()
                
                var queryIndex = 0
                val sb = StringBuilder()
                var lastWasMatch = false
                
                for (i in text.indices) {
                    val isMatch = queryIndex < lowerQuery.length && lowerText[i] == lowerQuery[queryIndex]
                    if (isMatch) {
                        if (!lastWasMatch && sb.isNotEmpty()) {
                            append(sb.toString(), baseAttributes)
                            sb.clear()
                        }
                        sb.append(text[i])
                        lastWasMatch = true
                        queryIndex++
                    } else {
                        if (lastWasMatch && sb.isNotEmpty()) {
                            append(sb.toString(), HIGHLIGHT_ATTRIBUTES)
                            sb.clear()
                        }
                        sb.append(text[i])
                        lastWasMatch = false
                    }
                }
                
                if (sb.isNotEmpty()) {
                    append(sb.toString(), if (lastWasMatch) HIGHLIGHT_ATTRIBUTES else baseAttributes)
                }
            }
        }
        
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    doOKAction()
                }
            }
        })
        
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    doOKAction()
                } else if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
                    searchField.requestFocus()
                    val text = searchField.text
                    if (text.isNotEmpty()) {
                        searchField.text = text.substring(0, text.length - 1)
                    }
                    e.consume()
                }
            }
            
            override fun keyTyped(e: KeyEvent) {
                if (!Character.isISOControl(e.keyChar) && e.keyChar != KeyEvent.CHAR_UNDEFINED) {
                    searchField.requestFocus()
                    searchField.text += e.keyChar
                    e.consume()
                }
            }
        })

        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        
        // Status Label
        statusLabel.border = javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5)
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun filter(text: String) {
        searchAlarm.cancelAllRequests()
        searchAlarm.addRequest({
            val query = text.trim().lowercase()
            val filtered = if (query.isEmpty()) {
                allItems
            } else {
                allItems.filter {
                    isSubsequence(query, it.path.lowercase()) ||
                    isSubsequence(query, it.method.lowercase())
                }
            }

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater({
                if (!isDisposed) {
                    currentQuery = text.trim()
                    updateList(filtered)
                    list.repaint()
                }
            }, com.intellij.openapi.application.ModalityState.stateForComponent(list))
        }, AppConstants.SEARCH_DEBOUNCE_MS.toLong())
    }

    private fun isSubsequence(query: String, target: String): Boolean {
        if (query.isEmpty()) return true
        var i = 0
        var j = 0
        while (i < query.length && j < target.length) {
            if (query[i] == target[j]) {
                i++
            }
            j++
        }
        return i == query.length
    }

    private fun updateList(items: List<RestApiItem>) {
        listModel.clear()
        items.take(AppConstants.MAX_SEARCH_RESULTS).forEach { listModel.addElement(it) }
        
        if (items.isNotEmpty()) {
            list.selectedIndex = 0
        }
        
        val countText = if (items.size > AppConstants.MAX_SEARCH_RESULTS) {
            HttpMateBundle.message("rest.search.status.limited", items.size, AppConstants.MAX_SEARCH_RESULTS, allItems.size)
        } else {
            HttpMateBundle.message("rest.search.status.full", items.size, allItems.size)
        }
        statusLabel.text = countText
    }

    override fun doOKAction() {
        val selected = list.selectedValue
        if (selected != null) {
            val element = ReadAction.compute<com.intellij.psi.PsiElement?, RuntimeException> {
                selected.element
            }
            if (element != null && element.isValid) {
                val navigatable = element as? com.intellij.pom.Navigatable
                    ?: element.navigationElement as? com.intellij.pom.Navigatable

                if (navigatable?.canNavigate() == true) {
                    navigatable.navigate(true)
                } else {
                    element.containingFile?.virtualFile?.let { file ->
                        com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file, selected.navigationOffset).navigate(true)
                    }
                }
            }
        }
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return searchField
    }
}
