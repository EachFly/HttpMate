package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.actions.ActionContextResolver
import com.github.jeraxxxxxxx.httpmate.actions.GeneratedFileNames
import com.github.jeraxxxxxxx.httpmate.doc.DocGenerator
import com.github.jeraxxxxxxx.httpmate.generator.MockJsonGenerator
import com.github.jeraxxxxxxx.httpmate.services.HttpMateProjectService
import com.github.jeraxxxxxxx.httpmate.services.RestApiScanner
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import java.util.Locale

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

    fun testRestApiScannerFindsSpringAndJaxRsEndpoints() {
        addRestAnnotationStubs()

        myFixture.addFileToProject(
            "src/com/example/SampleController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestParam;
            import javax.ws.rs.GET;
            import javax.ws.rs.Path;

            @RequestMapping("/api/users")
            public class SampleController {
              @GetMapping("/search")
              public UserResponse search(@RequestParam(required = false) String keyword) {
                return null;
              }

              @GET
              @Path("/profile")
              public UserResponse profile() {
                return null;
              }
            }

            class UserResponse {
              private String name;
            }
            """.trimIndent()
        )

        val items = ReadAction.compute<List<Pair<String, String>>, RuntimeException> {
            val scanner = RestApiScanner(project)
            val candidates = scanner.collectApiCandidates()
            scanner.scanBatch(candidates).map { it.method to it.path }.sortedBy { it.second }
        }

        assertEquals(
            listOf(
                "GET" to "/api/users/profile",
                "GET" to "/api/users/search"
            ),
            items
        )
    }

    fun testRestApiScannerExpandsMultiplePathsMethodsAndConstants() {
        addRestAnnotationStubs()

        val psiFile = myFixture.addFileToProject(
            "src/com/example/MultiController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestMethod;

            final class Routes {
              static final String API = "/constant";
            }

            @RequestMapping(path = {Routes.API, "/secondary"})
            public class MultiController {
              @RequestMapping(
                  path = {"/items", "/things"},
                  method = {RequestMethod.GET, RequestMethod.POST}
              )
              public String handle() {
                return "ok";
              }
            }
            """.trimIndent()
        )

        val result = ReadAction.compute<Pair<Set<Pair<String, String>>, String>, RuntimeException> {
            val scanner = RestApiScanner(project)
            val items = scanner.scanBatch(scanner.collectApiCandidates())
                .map { it.method to it.path }
                .toSet()
            val method = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
                .first { it.name == "handle" }
            items to DocGenerator().generate(method)
        }

        val expected = setOf(
            "GET" to "/constant/items",
            "POST" to "/constant/items",
            "GET" to "/constant/things",
            "POST" to "/constant/things",
            "GET" to "/secondary/items",
            "POST" to "/secondary/items",
            "GET" to "/secondary/things",
            "POST" to "/secondary/things"
        )
        assertEquals(expected, result.first)
        assertTrue(result.second.contains("GET, POST"))
        expected.map { it.second }.distinct().forEach { path ->
            assertTrue("Missing path $path", result.second.contains(path))
        }
    }

    fun testGeneratedFileNamesAvoidClassAndOverloadCollisions() {
        val firstFile = myFixture.addFileToProject(
            "src/first/UserController.java",
            "package first; public class UserController { public void load() {} public void load(String id) {} }"
        )
        val secondFile = myFixture.addFileToProject(
            "src/second/UserController.java",
            "package second; public class UserController { public void load() {} }"
        )

        val names = ReadAction.compute<List<String>, RuntimeException> {
            val firstClass = (firstFile as PsiClassOwner).classes.single()
            val secondClass = (secondFile as PsiClassOwner).classes.single()
            val overloads = firstClass.findMethodsByName("load", false).sortedBy { it.parameterList.parametersCount }
            listOf(
                GeneratedFileNames.forClass(firstClass),
                GeneratedFileNames.forClass(secondClass),
                GeneratedFileNames.forMethod(overloads[0]),
                GeneratedFileNames.forMethod(overloads[1])
            )
        }

        assertEquals(names.size, names.toSet().size)
    }

    fun testMockJsonUsesLocaleIndependentDecimalSeparator() {
        val previousLocale = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)
            val value = ReadAction.compute<String, RuntimeException> {
                MockJsonGenerator().generate(PsiTypes.doubleType(), 0)
            }
            assertTrue(value.matches(Regex("\\d+\\.\\d{2}")))
            assertFalse(value.contains(','))
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previousLocale)
        }
    }

    fun testDocGeneratorProducesReadableMarkdown() {
        addRestAnnotationStubs()

        val psiFile = myFixture.addFileToProject(
            "src/com/example/DocController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestParam;

            @RequestMapping("/api/users")
            public class DocController {
              /**
               * Load a single user
               * @param userId user identifier
               */
              @GetMapping("/detail")
              public UserResponse detail(@RequestParam(required = false) String userId, @RequestBody UserRequest body) {
                return null;
              }
            }

            class UserRequest {
              private String nickname;
            }

            class UserResponse {
              private String name;
            }
            """.trimIndent()
        )

        val content = ReadAction.compute<String, RuntimeException> {
            val method = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java).first { it.name == "detail" }
            DocGenerator().generate(method)
        }

        assertTrue(content.contains("## 接口信息"))
        assertTrue(content.contains("| 接口名称 | detail |"))
        assertTrue(content.contains("| 请求方式 | GET |"))
        assertTrue(content.contains("| 接口路径 | `/api/users/detail` |"))
        assertTrue(content.contains("| userId | String | 否 |"))
        assertTrue(content.contains("## 请求示例"))
        assertTrue(content.contains("## 响应示例"))
    }

    fun testActionContextResolverFindsRecordClassFromFile() {
        val psiFile = myFixture.addFileToProject(
            "src/com/example/UserRecord.java",
            """
            package com.example;

            public record UserRecord(String name, int age) {}
            """.trimIndent()
        )

        val resolvedClass = ActionContextResolver.findPsiClass(psiFile)
            ?: (psiFile as? com.intellij.psi.PsiClassOwner)?.classes?.singleOrNull()

        assertNotNull(resolvedClass)
        assertEquals("UserRecord", resolvedClass?.name)
        assertTrue(resolvedClass?.isRecord == true)
    }

    fun testActionContextResolverPrefersMethodWhenClickingInsideMethod() {
        addRestAnnotationStubs()

        val psiFile = myFixture.addFileToProject(
            "src/com/example/MethodContextController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/api")
            public class MethodContextController {
              @GetMapping("/detail")
              public String detail() {
                return "ok";
              }
            }
            """.trimIndent()
        )

        val method = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java).first { it.name == "detail" }
        val methodIdentifier = method.nameIdentifier

        assertEquals("detail", ActionContextResolver.findPsiMethod(methodIdentifier)?.name)
        assertEquals("MethodContextController", ActionContextResolver.findPsiClass(methodIdentifier)?.name)
    }

    private fun addRestAnnotationStubs() {
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/RequestMapping.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface RequestMapping {
              String[] value() default {};
              String[] path() default {};
              RequestMethod[] method() default {};
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/GetMapping.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface GetMapping {
              String[] value() default {};
              String[] path() default {};
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/PostMapping.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface PostMapping {
              String[] value() default {};
              String[] path() default {};
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/PutMapping.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface PutMapping {
              String[] value() default {};
              String[] path() default {};
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/DeleteMapping.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface DeleteMapping {
              String[] value() default {};
              String[] path() default {};
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/PatchMapping.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface PatchMapping {
              String[] value() default {};
              String[] path() default {};
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/RequestParam.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface RequestParam {
              boolean required() default true;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/RequestBody.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface RequestBody {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/RequestMethod.java",
            """
            package org.springframework.web.bind.annotation;
            public enum RequestMethod { GET, POST, PUT, DELETE, PATCH }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/javax/ws/rs/Path.java",
            """
            package javax.ws.rs;
            public @interface Path {
              String value() default "";
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/javax/ws/rs/GET.java",
            """
            package javax.ws.rs;
            public @interface GET {}
            """.trimIndent()
        )
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
