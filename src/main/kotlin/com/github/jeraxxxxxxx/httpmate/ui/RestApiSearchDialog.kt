package com.github.jeraxxxxxxx.httpmate.ui

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.constants.AppConstants
import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Computable
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.OpenSourceUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * REST API 搜索对话框
 * 提供快速搜索和导航到 API 定义的功能
 */
class RestApiSearchDialog(
    private val project: Project,
    private var allItems: List<RestApiItem>,
    private val onClose: () -> Unit = {}
) :
    DialogWrapper(project) {

    private val listModel = javax.swing.DefaultListModel<RestApiItem>()
    private val list = JBList(listModel)
    private val searchField = SearchTextField()
    private var currentQuery: String = ""
    private var loading = false
    private var stoppedMessage: String? = null
    private val filterGeneration = AtomicLong()
    @Volatile
    private var searchIndex: Pair<List<RestApiItem>, RestSearchIndex<RestApiItem>>? = null
    private class StaleSearch : RuntimeException()

    private val searchAlarm = com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD, disposable)
    private val statusLabel = javax.swing.JLabel()

    init {
        isModal = false
        init()
        title = HttpMateBundle.message("rest.search.title")
        updateList(allItems)
    }

    fun showLoading() {
        loading = true
        stoppedMessage = null
        isOKActionEnabled = false
        statusLabel.text = HttpMateBundle.message("rest.search.progress.title")
    }

    fun setItems(items: List<RestApiItem>) {
        if (isDisposed) return
        loading = false
        stoppedMessage = null
        // The scan hands over an immutable snapshot; do not copy large lists on the EDT.
        allItems = items
        searchIndex = null
        filter(searchField.text)
    }

    fun scanStopped(message: String) {
        if (isDisposed) return
        loading = false
        stoppedMessage = message
        filterGeneration.incrementAndGet()
        searchAlarm.cancelAllRequests()
        statusLabel.text = message
    }

    override fun dispose() {
        filterGeneration.incrementAndGet()
        searchIndex = null
        onClose()
        super.dispose()
    }

    override fun getDimensionServiceKey(): String {
        return "HttpMate.RestApiSearchDialog"
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)

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
                append(value.method, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append(" ")
                appendHighlighted(value.path, currentQuery, SimpleTextAttributes.REGULAR_ATTRIBUTES)

                if (value.fileName.isNotEmpty()) {
                    append(" (${value.fileName})", SimpleTextAttributes.GRAY_ATTRIBUTES)
                }
                icon = value.icon
            }

            private val HIGHLIGHT_ATTRIBUTES =
                SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, com.intellij.ui.JBColor.BLUE)

            private fun appendHighlighted(text: String, query: String, baseAttributes: SimpleTextAttributes) {
                if (query.isEmpty()) {
                    append(text, baseAttributes)
                    return
                }

                val lowerText = text.lowercase()
                val lowerQuery = query.lowercase()

                val exactIndex = lowerText.indexOf(lowerQuery)
                if (exactIndex >= 0) {
                    if (exactIndex > 0) {
                        append(text.substring(0, exactIndex), baseAttributes)
                    }
                    append(text.substring(exactIndex, exactIndex + query.length), HIGHLIGHT_ATTRIBUTES)
                    if (exactIndex + query.length < text.length) {
                        append(text.substring(exactIndex + query.length), baseAttributes)
                    }
                    return
                }

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
        val generation = filterGeneration.incrementAndGet()
        val snapshot = allItems
        val modality = com.intellij.openapi.application.ModalityState.stateForComponent(list)
        searchAlarm.cancelAllRequests()
        searchAlarm.addRequest({
            val isCurrent = { generation == filterGeneration.get() }
            val index = searchIndex?.takeIf { it.first === snapshot }?.second ?: try {
                RestSearchIndex(snapshot) {
                    if (!isCurrent()) throw StaleSearch()
                    it.path
                }.also { if (isCurrent()) searchIndex = snapshot to it }
            } catch (_: StaleSearch) { return@addRequest }
            val filtered = index.search(text, AppConstants.MAX_SEARCH_RESULTS, isCurrent) ?: return@addRequest

            ApplicationManager.getApplication().invokeLater({
                if (!isDisposed && generation == filterGeneration.get()) {
                    currentQuery = text.trim()
                    updateList(filtered.items, filtered.total)
                    list.repaint()
                }
            }, modality)
        }, AppConstants.SEARCH_DEBOUNCE_MS.toLong())
    }

    private fun updateList(items: List<RestApiItem>, totalMatches: Int = items.size) {
        listModel.clear()
        items.take(AppConstants.MAX_SEARCH_RESULTS).forEach { listModel.addElement(it) }

        if (items.isNotEmpty()) {
            list.selectedIndex = 0
        }
        isOKActionEnabled = items.isNotEmpty()

        val countText = if (totalMatches > AppConstants.MAX_SEARCH_RESULTS) {
            HttpMateBundle.message(
                "rest.search.status.limited",
                totalMatches,
                AppConstants.MAX_SEARCH_RESULTS,
                allItems.size
            )
        } else {
            HttpMateBundle.message("rest.search.status.full", totalMatches, allItems.size)
        }
        statusLabel.text = stoppedMessage ?: if (loading) HttpMateBundle.message("rest.search.progress.title") else countText
    }

    override fun doOKAction() {
        if (list.selectedValue == null) return
        val selected = list.selectedValue
        if (selected != null) {
            val element = ApplicationManager.getApplication().runReadAction(Computable {
                selected.element
            })
            if (element != null && element.isValid) {
                val navigatable = element as? com.intellij.pom.Navigatable
                    ?: element.navigationElement as? com.intellij.pom.Navigatable

                if (navigatable?.canNavigate() == true) {
                    OpenSourceUtil.navigate(true, navigatable)
                } else {
                    element.containingFile?.virtualFile?.let { file ->
                        OpenSourceUtil.navigate(
                            true,
                            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file, selected.navigationOffset)
                        )
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
