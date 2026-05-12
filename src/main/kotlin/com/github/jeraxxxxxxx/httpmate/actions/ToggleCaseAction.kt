package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep

/**
 * 变量命名风格切换 Action
 *
 * 支持以下命名风格的循环切换：
 * - camelCase
 * - snake_case
 * - SCREAMING_SNAKE_CASE
 * - PascalCase
 * - kebab-case
 */
class ToggleCaseAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection && e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectionModel = editor.selectionModel

        if (!selectionModel.hasSelection()) return

        val selectedText = selectionModel.selectedText ?: return
        if (selectedText.isBlank()) return

        val currentStyle = CaseConverter.detect(selectedText)
        val conversions = CaseConverter.allConversions(selectedText, currentStyle)

        if (conversions.isEmpty()) return

        showConversionPopup(project, editor, conversions, currentStyle)
    }

    private fun showConversionPopup(
        project: Project,
        editor: Editor,
        conversions: List<CaseConversion>,
        currentStyle: NamingStyle
    ) {
        val step = object : BaseListPopupStep<CaseConversion>(
            HttpMateBundle.message(
                "toggle.case.popup.title",
                currentStyle.displayName
            ), conversions
        ) {
            override fun getTextFor(value: CaseConversion): String {
                return "${value.style.displayName}:  ${value.result}"
            }

            override fun onChosen(selectedValue: CaseConversion, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    applyConversion(project, editor, selectedValue.result)
                }
                return PopupStep.FINAL_CHOICE
            }

            override fun isSpeedSearchEnabled(): Boolean = true
        }

        val popup: ListPopup = JBPopupFactory.getInstance().createListPopup(step)
        popup.showInBestPositionFor(editor)
    }

    private fun applyConversion(project: Project, editor: Editor, newText: String) {
        val selectionModel = editor.selectionModel
        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd

        WriteCommandAction.runWriteCommandAction(project, HttpMateBundle.message("toggle.case.command.name"), null, {
            editor.document.replaceString(start, end, newText)
            // Re-select the replaced text
            selectionModel.setSelection(start, start + newText.length)
        })
    }
}

/**
 * 命名风格枚举
 */
enum class NamingStyle(val displayName: String) {
    CAMEL_CASE("camelCase"),
    SNAKE_CASE("snake_case"),
    SCREAMING_SNAKE_CASE("SCREAMING_SNAKE_CASE"),
    PASCAL_CASE("PascalCase"),
    KEBAB_CASE("kebab-case"),
    UNKNOWN("unknown")
}

/**
 * 转换结果
 */
data class CaseConversion(
    val style: NamingStyle,
    val result: String
)

/**
 * 命名风格转换工具
 *
 * 负责识别当前命名风格，并在不同风格之间转换。
 */
object CaseConverter {

    /**
     * 将文本拆分为单词列表（全小写）
     */
    fun splitWords(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        // 1. snake_case / SCREAMING_SNAKE_CASE
        if (text.contains('_')) {
            return text.split('_')
                .filter { it.isNotEmpty() }
                .map { it.lowercase() }
        }

        // 2. kebab-case
        if (text.contains('-')) {
            return text.split('-')
                .filter { it.isNotEmpty() }
                .map { it.lowercase() }
        }

        // 3. camelCase / PascalCase — split on uppercase transitions
        val words = mutableListOf<String>()
        val sb = StringBuilder()

        for (i in text.indices) {
            val ch = text[i]
            if (ch.isUpperCase()) {
                // Handle consecutive uppercase (acronyms like "XMLParser" -> "XML", "Parser")
                val nextIsLower = i + 1 < text.length && text[i + 1].isLowerCase()
                val prevIsLower = i > 0 && text[i - 1].isLowerCase()

                if (sb.isNotEmpty() && (prevIsLower || nextIsLower)) {
                    words.add(sb.toString().lowercase())
                    sb.clear()
                }
            }
            sb.append(ch)
        }
        if (sb.isNotEmpty()) {
            words.add(sb.toString().lowercase())
        }

        return words
    }

    /**
     * 识别文本的命名风格
     */
    fun detect(text: String): NamingStyle {
        if (text.isBlank()) return NamingStyle.UNKNOWN

        // Contains underscore -> snake_case or SCREAMING_SNAKE_CASE
        if (text.contains('_')) {
            return if (text == text.uppercase()) {
                NamingStyle.SCREAMING_SNAKE_CASE
            } else {
                NamingStyle.SNAKE_CASE
            }
        }

        // Contains hyphen -> kebab-case
        if (text.contains('-')) {
            return NamingStyle.KEBAB_CASE
        }

        // Starts with uppercase -> PascalCase
        if (text[0].isUpperCase() && text.length > 1 && text.any { it.isLowerCase() }) {
            return NamingStyle.PASCAL_CASE
        }

        // Starts with lowercase and has uppercase -> camelCase
        if (text[0].isLowerCase() && text.any { it.isUpperCase() }) {
            return NamingStyle.CAMEL_CASE
        }

        // All uppercase single word
        if (text == text.uppercase() && text.length > 1) {
            return NamingStyle.SCREAMING_SNAKE_CASE
        }

        // Default: treat as camelCase (single lowercase word)
        return NamingStyle.CAMEL_CASE
    }

    /**
     * 转换为指定风格
     */
    fun convertTo(text: String, target: NamingStyle): String {
        val words = splitWords(text)
        if (words.isEmpty()) return text

        return when (target) {
            NamingStyle.CAMEL_CASE -> {
                words.first() + words.drop(1).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            }

            NamingStyle.SNAKE_CASE -> {
                words.joinToString("_")
            }

            NamingStyle.SCREAMING_SNAKE_CASE -> {
                words.joinToString("_") { it.uppercase() }
            }

            NamingStyle.PASCAL_CASE -> {
                words.joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            }

            NamingStyle.KEBAB_CASE -> {
                words.joinToString("-")
            }

            NamingStyle.UNKNOWN -> text
        }
    }

    /**
     * 生成除当前风格外的所有转换结果
     */
    fun allConversions(text: String, currentStyle: NamingStyle): List<CaseConversion> {
        return NamingStyle.entries
            .filter { it != NamingStyle.UNKNOWN && it != currentStyle }
            .map { style -> CaseConversion(style, convertTo(text, style)) }
            .filter { it.result != text } // Filter out no-op conversions
    }
}
