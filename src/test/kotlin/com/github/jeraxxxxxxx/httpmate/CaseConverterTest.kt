package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.actions.CaseConverter
import com.github.jeraxxxxxxx.httpmate.actions.NamingStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CaseConverter 单元测试
 */
class CaseConverterTest {

    // ========== Detection Tests ==========

    @Test
    fun `detect camelCase`() {
        assertEquals(NamingStyle.CAMEL_CASE, CaseConverter.detect("userName"))
        assertEquals(NamingStyle.CAMEL_CASE, CaseConverter.detect("myVariableName"))
        assertEquals(NamingStyle.CAMEL_CASE, CaseConverter.detect("getHttpResponse"))
    }

    @Test
    fun `detect snake_case`() {
        assertEquals(NamingStyle.SNAKE_CASE, CaseConverter.detect("user_name"))
        assertEquals(NamingStyle.SNAKE_CASE, CaseConverter.detect("my_variable_name"))
        assertEquals(NamingStyle.SNAKE_CASE, CaseConverter.detect("get_http_response"))
    }

    @Test
    fun `detect SCREAMING_SNAKE_CASE`() {
        assertEquals(NamingStyle.SCREAMING_SNAKE_CASE, CaseConverter.detect("USER_NAME"))
        assertEquals(NamingStyle.SCREAMING_SNAKE_CASE, CaseConverter.detect("MAX_RETRY_COUNT"))
    }

    @Test
    fun `detect PascalCase`() {
        assertEquals(NamingStyle.PASCAL_CASE, CaseConverter.detect("UserName"))
        assertEquals(NamingStyle.PASCAL_CASE, CaseConverter.detect("MyVariableName"))
        assertEquals(NamingStyle.PASCAL_CASE, CaseConverter.detect("HttpResponse"))
    }

    @Test
    fun `detect kebab-case`() {
        assertEquals(NamingStyle.KEBAB_CASE, CaseConverter.detect("user-name"))
        assertEquals(NamingStyle.KEBAB_CASE, CaseConverter.detect("my-variable-name"))
    }

    // ========== Word Splitting Tests ==========

    @Test
    fun `split camelCase words`() {
        assertEquals(listOf("user", "name"), CaseConverter.splitWords("userName"))
        assertEquals(listOf("my", "variable", "name"), CaseConverter.splitWords("myVariableName"))
    }

    @Test
    fun `split snake_case words`() {
        assertEquals(listOf("user", "name"), CaseConverter.splitWords("user_name"))
        assertEquals(listOf("my", "variable", "name"), CaseConverter.splitWords("my_variable_name"))
    }

    @Test
    fun `split PascalCase words`() {
        assertEquals(listOf("user", "name"), CaseConverter.splitWords("UserName"))
        assertEquals(listOf("http", "response"), CaseConverter.splitWords("HttpResponse"))
    }

    @Test
    fun `split kebab-case words`() {
        assertEquals(listOf("user", "name"), CaseConverter.splitWords("user-name"))
    }

    @Test
    fun `split SCREAMING_SNAKE_CASE words`() {
        assertEquals(listOf("user", "name"), CaseConverter.splitWords("USER_NAME"))
    }

    // ========== Conversion Tests ==========

    @Test
    fun `convert camelCase to all styles`() {
        val input = "userName"
        assertEquals("userName", CaseConverter.convertTo(input, NamingStyle.CAMEL_CASE))
        assertEquals("user_name", CaseConverter.convertTo(input, NamingStyle.SNAKE_CASE))
        assertEquals("USER_NAME", CaseConverter.convertTo(input, NamingStyle.SCREAMING_SNAKE_CASE))
        assertEquals("UserName", CaseConverter.convertTo(input, NamingStyle.PASCAL_CASE))
        assertEquals("user-name", CaseConverter.convertTo(input, NamingStyle.KEBAB_CASE))
    }

    @Test
    fun `convert snake_case to all styles`() {
        val input = "user_name"
        assertEquals("userName", CaseConverter.convertTo(input, NamingStyle.CAMEL_CASE))
        assertEquals("user_name", CaseConverter.convertTo(input, NamingStyle.SNAKE_CASE))
        assertEquals("USER_NAME", CaseConverter.convertTo(input, NamingStyle.SCREAMING_SNAKE_CASE))
        assertEquals("UserName", CaseConverter.convertTo(input, NamingStyle.PASCAL_CASE))
        assertEquals("user-name", CaseConverter.convertTo(input, NamingStyle.KEBAB_CASE))
    }

    @Test
    fun `convert SCREAMING_SNAKE_CASE to all styles`() {
        val input = "USER_NAME"
        assertEquals("userName", CaseConverter.convertTo(input, NamingStyle.CAMEL_CASE))
        assertEquals("user_name", CaseConverter.convertTo(input, NamingStyle.SNAKE_CASE))
        assertEquals("USER_NAME", CaseConverter.convertTo(input, NamingStyle.SCREAMING_SNAKE_CASE))
        assertEquals("UserName", CaseConverter.convertTo(input, NamingStyle.PASCAL_CASE))
        assertEquals("user-name", CaseConverter.convertTo(input, NamingStyle.KEBAB_CASE))
    }

    @Test
    fun `convert PascalCase to all styles`() {
        val input = "UserName"
        assertEquals("userName", CaseConverter.convertTo(input, NamingStyle.CAMEL_CASE))
        assertEquals("user_name", CaseConverter.convertTo(input, NamingStyle.SNAKE_CASE))
        assertEquals("USER_NAME", CaseConverter.convertTo(input, NamingStyle.SCREAMING_SNAKE_CASE))
        assertEquals("UserName", CaseConverter.convertTo(input, NamingStyle.PASCAL_CASE))
        assertEquals("user-name", CaseConverter.convertTo(input, NamingStyle.KEBAB_CASE))
    }

    @Test
    fun `convert kebab-case to all styles`() {
        val input = "user-name"
        assertEquals("userName", CaseConverter.convertTo(input, NamingStyle.CAMEL_CASE))
        assertEquals("user_name", CaseConverter.convertTo(input, NamingStyle.SNAKE_CASE))
        assertEquals("USER_NAME", CaseConverter.convertTo(input, NamingStyle.SCREAMING_SNAKE_CASE))
        assertEquals("UserName", CaseConverter.convertTo(input, NamingStyle.PASCAL_CASE))
        assertEquals("user-name", CaseConverter.convertTo(input, NamingStyle.KEBAB_CASE))
    }

    @Test
    fun `multi-word conversion`() {
        val input = "myLongVariableName"
        assertEquals("my_long_variable_name", CaseConverter.convertTo(input, NamingStyle.SNAKE_CASE))
        assertEquals("MY_LONG_VARIABLE_NAME", CaseConverter.convertTo(input, NamingStyle.SCREAMING_SNAKE_CASE))
        assertEquals("MyLongVariableName", CaseConverter.convertTo(input, NamingStyle.PASCAL_CASE))
        assertEquals("my-long-variable-name", CaseConverter.convertTo(input, NamingStyle.KEBAB_CASE))
    }

    // ========== allConversions Tests ==========

    @Test
    fun `allConversions excludes current style`() {
        val conversions = CaseConverter.allConversions("userName", NamingStyle.CAMEL_CASE)
        assertEquals(4, conversions.size)
        assert(conversions.none { it.style == NamingStyle.CAMEL_CASE })
    }

    @Test
    fun `allConversions produces correct results`() {
        val conversions = CaseConverter.allConversions("userName", NamingStyle.CAMEL_CASE)
        val resultMap = conversions.associate { it.style to it.result }

        assertEquals("user_name", resultMap[NamingStyle.SNAKE_CASE])
        assertEquals("USER_NAME", resultMap[NamingStyle.SCREAMING_SNAKE_CASE])
        assertEquals("UserName", resultMap[NamingStyle.PASCAL_CASE])
        assertEquals("user-name", resultMap[NamingStyle.KEBAB_CASE])
    }

    // ========== Edge Cases ==========

    @Test
    fun `single word stays the same in camelCase`() {
        assertEquals("name", CaseConverter.convertTo("name", NamingStyle.CAMEL_CASE))
    }

    @Test
    fun `single word uppercase in SCREAMING`() {
        assertEquals("NAME", CaseConverter.convertTo("name", NamingStyle.SCREAMING_SNAKE_CASE))
    }

    @Test
    fun `empty string returns empty`() {
        assertEquals(0, CaseConverter.splitWords("").size)
    }

    @Test
    fun `acronym handling - XMLParser`() {
        val words = CaseConverter.splitWords("XMLParser")
        assertEquals(listOf("xml", "parser"), words)
        assertEquals("xml_parser", CaseConverter.convertTo("XMLParser", NamingStyle.SNAKE_CASE))
        assertEquals("xmlParser", CaseConverter.convertTo("XMLParser", NamingStyle.CAMEL_CASE))
    }

    @Test
    fun `acronym handling - getHTTPResponse`() {
        val words = CaseConverter.splitWords("getHTTPResponse")
        assertEquals(listOf("get", "http", "response"), words)
        assertEquals("get_http_response", CaseConverter.convertTo("getHTTPResponse", NamingStyle.SNAKE_CASE))
    }
}
