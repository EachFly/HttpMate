package com.github.jeraxxxxxxx.httpmate

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.github.jeraxxxxxxx.httpmate.services.HttpMateProjectService

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    fun testProjectService() {
        val projectService = project.service<HttpMateProjectService>()

        // Test service initialization
        assertNotNull(projectService)
        
        // Test default configuration
        assertEquals("http-mate/docs", projectService.docOutputDir)
        assertEquals(0, projectService.totalDocsGenerated)
        
        // Test configuration modification
        projectService.docOutputDir = "custom/docs"
        assertEquals("custom/docs", projectService.docOutputDir)
        
        // Test generation recording
        projectService.recordGeneration("test.md")
        assertEquals(1, projectService.totalDocsGenerated)
        assertTrue(projectService.getGeneratedFiles().contains("test.md"))
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
