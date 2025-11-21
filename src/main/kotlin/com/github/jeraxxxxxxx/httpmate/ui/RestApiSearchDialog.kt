package com.github.jeraxxxxxxx.httpmate.ui

import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.psi.NavigatablePsiElement
import javax.swing.JList

class RestApiSearchDialog(private val project: Project, private val items: List<RestApiItem>) {

    fun show() {
        val renderer = object : ColoredListCellRenderer<RestApiItem>() {
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

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setRenderer(renderer)
            .setTitle("Search REST APIs")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setNamerForFiltering { item: RestApiItem -> item.method + " " + item.path }
            .setItemChosenCallback { item: RestApiItem? ->
                if (item != null && item.element is NavigatablePsiElement) {
                    (item.element as NavigatablePsiElement).navigate(true)
                }
            }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }
}
