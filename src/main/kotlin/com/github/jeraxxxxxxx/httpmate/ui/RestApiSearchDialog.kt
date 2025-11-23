package com.github.jeraxxxxxxx.httpmate.ui

import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
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

class RestApiSearchDialog(private val project: Project, private val allItems: List<RestApiItem>) : DialogWrapper(project) {

    private val listModel = javax.swing.DefaultListModel<RestApiItem>()
    private val list = JBList(listModel)
    private val searchField = SearchTextField()

    init {
        init()
        title = "Search REST APIs"
        updateList(allItems)
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
                append(value.method, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append(" " + value.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                val psiFile = value.element.containingFile
                if (psiFile != null) {
                     append(" (" + psiFile.name + ")", SimpleTextAttributes.GRAY_ATTRIBUTES)
                }
                icon = value.icon
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
                }
            }
        })

        panel.add(JBScrollPane(list), BorderLayout.CENTER)

        return panel
    }

    private fun filter(text: String) {
        val query = text.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allItems
        } else {
            allItems.filter { 
                it.path.lowercase().contains(query) || 
                it.method.lowercase().contains(query) 
            }
        }
        updateList(filtered)
    }

    private fun updateList(items: List<RestApiItem>) {
        listModel.clear()
        items.forEach { listModel.addElement(it) }
        if (items.isNotEmpty()) {
            list.selectedIndex = 0
        }
    }

    override fun doOKAction() {
        val selected = list.selectedValue
        if (selected != null) {
            val element = selected.element
            if (element is com.intellij.pom.Navigatable && (element as com.intellij.pom.Navigatable).canNavigate()) {
                (element as com.intellij.pom.Navigatable).navigate(true)
            } else if (element.navigationElement is com.intellij.pom.Navigatable && (element.navigationElement as com.intellij.pom.Navigatable).canNavigate()) {
                (element.navigationElement as com.intellij.pom.Navigatable).navigate(true)
            } else if (element.isValid) {
                val file = element.containingFile?.virtualFile
                if (file != null) {
                    com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file, element.textOffset).navigate(true)
                }
            }
        }
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return searchField
    }
}
