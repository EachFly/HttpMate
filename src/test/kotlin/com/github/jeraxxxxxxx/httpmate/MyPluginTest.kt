package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.actions.ActionContextResolver
import com.github.jeraxxxxxxx.httpmate.actions.GeneratedFileNames
import com.github.jeraxxxxxxx.httpmate.actions.ProjectBackgroundWork
import com.github.jeraxxxxxxx.httpmate.doc.DocGenerator
import com.github.jeraxxxxxxx.httpmate.generator.MockJsonGenerator
import com.github.jeraxxxxxxx.httpmate.generator.DefaultJsonGenerator
import com.github.jeraxxxxxxx.httpmate.generator.JsonFieldResolver
import com.github.jeraxxxxxxx.httpmate.services.HttpMateProjectService
import com.github.jeraxxxxxxx.httpmate.services.RestApiScanner
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import java.util.Locale
import java.util.concurrent.Callable
import java.io.File
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes

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

    fun testFallbackScanCreatesValidNavigationPointersWithoutAnOuterReadLock() {
        myFixture.addFileToProject("src/com/example/FallbackController.java", """
            package com.example;
            class FallbackController {
                @RequestMapping("/users") public void users() {}
            }
            @RequestMapping("/admin") class AdminController {
                @GetMapping("/users") public void users() {}
            }
        """.trimIndent())

        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
            RestApiScanner(project).scanFallback()
        })
        val items = PlatformTestUtil.waitForFuture(future, 20_000)
        assertEquals(setOf("ALL" to "/users", "GET" to "/admin/users"), items.map { it.method to it.path }.toSet())
        ReadAction.run<RuntimeException> {
            items.forEach { item ->
                val element = item.element
                assertNotNull(element)
                assertTrue(element!!.isValid)
                assertTrue(element.containingFile.text.substring(item.navigationOffset).startsWith("@"))
            }
        }
    }

    fun testJsonGenerationSubstitutesGenericAndInheritedFieldTypes() {
        myFixture.addFileToProject("src/java/util/List.java", """
            package java.util;
            public interface List<E> {}
        """.trimIndent())
        val file = myFixture.addFileToProject("src/com/example/GenericTypes.java", """
            package com.example;
            class User { String name; }
            class Envelope<T> { T data; }
            class Parent<T> { T inherited; }
            class Child<U> extends Parent<java.util.List<U>> { U direct; }
            class GenericTypes {
                Envelope<User> response;
                Child<User> child;
            }
        """.trimIndent())

        ReadAction.run<RuntimeException> {
            val owner = (file as PsiClassOwner).classes.first { it.name == "GenericTypes" }
            val responseType = owner.findFieldByName("response", false)!!.type
            val childType = owner.findFieldByName("child", false)!!.type
            val fields = JsonFieldResolver.resolve(childType).associate { it.name to it.type.canonicalText }
            assertEquals("com.example.User", fields["direct"])
            assertEquals("java.util.List<com.example.User>", fields["inherited"])
            for (generator in listOf(DefaultJsonGenerator(), MockJsonGenerator())) {
                val json = generator.generate(responseType, 0)
                assertTrue(json.contains("\"data\": {"))
                assertTrue(json.contains("\"name\":"))
            }
            val mock = MockJsonGenerator().generate(childType, 0)
            assertTrue(mock.contains("\"inherited\": ["))
            assertTrue(mock.substringAfter("\"inherited\":").contains("\"name\":"))
        }
    }

    fun testDocumentationRequiredFlagsRespectBindingsAndDefaults() {
        addRestAnnotationStubs()
        myFixture.addFileToProject("src/org/springframework/lang/Nullable.java", """
            package org.springframework.lang;
            public @interface Nullable {}
        """.trimIndent())
        val file = myFixture.addFileToProject("src/com/example/OptionalController.java", """
            package com.example;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RequestParam;
            import org.springframework.web.bind.annotation.RequestHeader;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.ValueConstants;
            class OptionalController {
                static final boolean OPTIONAL = false;
                static final String FALLBACK = "fallback";
                @PostMapping("/optional")
                public void handle(
                    @RequestBody(required = OPTIONAL) String body,
                    @RequestParam(defaultValue = FALLBACK) String query,
                    @RequestParam(defaultValue = "") String emptyDefault,
                    @RequestParam(defaultValue = ValueConstants.DEFAULT_NONE) String required,
                    @RequestHeader(defaultValue = "en") String language,
                    @PathVariable(required = false) String id,
                    @RequestParam java.util.Optional<String> wrapped,
                    @org.springframework.lang.Nullable @RequestBody String nullableBody
                ) {}
            }
        """.trimIndent())
        val markdown = ReadAction.compute<String, RuntimeException> {
            val method = PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).first { it.name == "handle" }
            DocGenerator().generate(method)
        }
        for (name in listOf("body", "query", "emptyDefault", "language", "id", "nullableBody")) {
            assertTrue("Expected optional parameter: $name\n$markdown", markdown.contains("| $name | String | 否 |"))
        }
        assertTrue(markdown.contains("| required | String | 是 |"))
        assertTrue(markdown.contains("| wrapped | Optional<String> | 否 |"))
    }

    fun testIdenticalEndpointsAndClassNamesInDifferentSourceRootsAreNotCollapsed() {
        addRestAnnotationStubs()
        val source = """
            package example;
            import org.springframework.web.bind.annotation.GetMapping;
            public class Controller { @GetMapping("/same") public void handle() {} }
        """.trimIndent()
        val files = listOf("module-a", "module-b").map {
            myFixture.addFileToProject("$it/src/example/Controller.java", source)
        }
        ReadAction.run<RuntimeException> {
            val classes = files.map { (it as PsiClassOwner).classes.single() }
            val methods = classes.map { it.findMethodsByName("handle", false).single() }
            assertEquals(methods[0].textOffset, methods[1].textOffset)
            val pointers = methods.map { SmartPointerManager.createPointer(it) }
            val items = RestApiScanner(project).scanBatch(pointers + pointers)
            assertEquals(2, items.size)
            assertEquals(2, items.map { it.elementPointer.virtualFile?.url }.toSet().size)
            assertEquals(2, classes.map(GeneratedFileNames::forClass).toSet().size)
            assertEquals(2, methods.map(GeneratedFileNames::forMethod).toSet().size)
        }
    }

    fun testSpringClassMethodsAreSharedByScannerAndDocumentation() {
        addRestAnnotationStubs()
        val file = myFixture.addFileToProject("src/example/MethodsController.java", """
            package example;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestMethod;
            import org.springframework.web.bind.annotation.GetMapping;
            @RequestMapping(value="/api", method=RequestMethod.POST)
            class MethodsController {
                @RequestMapping("/inherited") public void inherited() {}
                @GetMapping("/both") public void both() {}
            }
        """.trimIndent())
        ReadAction.run<RuntimeException> {
            val methods = (file as PsiClassOwner).classes.single().methods
            val items = RestApiScanner(project).scanBatch(methods.map { SmartPointerManager.createPointer(it) })
            assertEquals(setOf("POST" to "/api/inherited", "POST" to "/api/both", "GET" to "/api/both"),
                items.map { it.method to it.path }.toSet())
            assertTrue(DocGenerator().generate(methods.first { it.name == "inherited" }).contains("| 请求方式 | POST |"))
            assertTrue(DocGenerator().generate(methods.first { it.name == "both" }).contains("| 请求方式 | POST, GET |"))
        }
    }

    fun testDocumentationResolvesGenericAndInheritedFieldsForEachInstantiation() {
        val file = myFixture.addFileToProject("src/example/GenericController.java", """
            package example;
            class User { String name; }
            class Other { int count; }
            class Parent<T> { /** inherited description */ T inherited; }
            class Envelope<T> extends Parent<String> { T data; }
            class GenericController {
                public Envelope<User> handle(Envelope<User> users, Envelope<Other> others) { return null; }
            }
        """.trimIndent())
        ReadAction.run<RuntimeException> {
            val method = (file as PsiClassOwner).classes.last().methods.single()
            val markdown = DocGenerator().generate(method)
            assertTrue(markdown.contains("**Envelope<User>**"))
            assertTrue(markdown.contains("**Envelope<Other>**"))
            assertTrue(markdown.contains("| data | User |"))
            assertTrue(markdown.contains("| data | Other |"))
            assertTrue(markdown.contains("| inherited | String |"))
            assertTrue(markdown.contains("inherited description"))
            assertFalse(markdown.contains("| data | T |"))
            assertTrue(markdown.contains("\"name\":"))
            val responseTable = markdown.substringAfter("## 响应参数").substringBefore("## 请求示例")
            assertTrue(responseTable.contains("| data | User |"))
            assertTrue(responseTable.contains("| inherited | String | inherited description |"))
        }
    }

    fun testCanceledGenerationDoesNotReplaceExistingFileOrShowQueuedResults() {
        val directory = FileUtil.createTempDirectory("httpmate-output-test", null, true)
        try {
            val target = File(directory, "generated/result.json")
            val indicator = EmptyProgressIndicator()
            ProjectBackgroundWork.writeText(project, indicator, target, "original")
            var displayed = false
            ProjectBackgroundWork.later(project, indicator) { displayed = true }
            indicator.cancel()
            try {
                ProjectBackgroundWork.writeText(project, indicator, target, "replacement")
                fail("Canceled generation must not write output")
            } catch (_: ProcessCanceledException) {
                assertEquals("original", target.readText())
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertFalse(displayed)
            assertEquals(listOf("result.json"), target.parentFile.listFiles()!!.map { it.name })
        } finally {
            FileUtil.delete(directory)
        }
    }

    fun testFallbackScanResolvesImportedJavaConstantsAndConcatenations() {
        myFixture.addFileToProject("example/Routes.java", """
            package example;
            public class Routes {
                public static final String API = "/api";
                public static final String USERS = "/users";
            }
        """.trimIndent())
        myFixture.addFileToProject("src/example/ConstantController.java", """
            package example;
            import example.Routes;
            import static example.Routes.API;
            @RequestMapping(API)
            class ConstantController {
                @GetMapping(Routes.USERS + "/active") public void handle() {}
                @GetMapping(UNKNOWN + "/wrong") public void unknown() {}
            }
        """.trimIndent())
        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            RestApiScanner(project).scanFallback().map { it.method to it.path }.toSet()
        })
        assertEquals(setOf("GET" to "/api/users/active"), PlatformTestUtil.waitForFuture(future, 20_000))
    }

    fun testBothJsonGeneratorsUseTypeSpecificTemporalFormats() {
        val context = myFixture.addFileToProject("TemporalTypes.java", "class TemporalTypes {}")
        val factory = JavaPsiFacade.getElementFactory(project)
        for (generator in listOf(DefaultJsonGenerator(), MockJsonGenerator())) {
            fun value(name: String) = generator.generate(factory.createTypeFromText(name, context), 0).trim('"')
            java.time.LocalDate.parse(value("java.time.LocalDate"))
            java.time.LocalTime.parse(value("java.time.LocalTime"))
            java.time.LocalDateTime.parse(value("java.time.LocalDateTime"))
            java.time.ZonedDateTime.parse(value("java.time.ZonedDateTime"))
            java.time.OffsetDateTime.parse(value("java.time.OffsetDateTime"))
            java.time.Instant.parse(value("java.time.Instant"))
        }
    }

    fun testBinaryDependencyAnnotationsDoNotExpandEndpointSearchScope() {
        val directory = FileUtil.createTempDirectory("httpmate-binary-dependency", null, true)
        Disposer.register(testRootDisposable, Disposable { FileUtil.delete(directory) })
        fun writeClass(name: String, writer: ClassWriter) {
            writer.visitEnd()
            File(directory, "classes/$name.class").apply {
                parentFile.mkdirs()
                writeBytes(writer.toByteArray())
            }
        }
        val annotationPath = "org/springframework/web/bind/annotation/GetMapping"
        val annotationWriter = ClassWriter(0)
        annotationWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT or
            Opcodes.ACC_INTERFACE or Opcodes.ACC_ANNOTATION, annotationPath, null,
            "java/lang/Object", arrayOf("java/lang/annotation/Annotation"))
        annotationWriter.visitAnnotation("Ljava/lang/annotation/Retention;", true).apply {
            visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME")
            visitEnd()
        }
        for (name in listOf("value", "path")) {
            annotationWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, name,
                "()[Ljava/lang/String;", null, null).apply {
                visitAnnotationDefault().apply {
                    visitArray(null).visitEnd()
                    visitEnd()
                }
                visitEnd()
            }
        }
        writeClass(annotationPath, annotationWriter)
        val controllerWriter = ClassWriter(0)
        controllerWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT,
            "dependency/LibraryController", null, "java/lang/Object", null)
        controllerWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "handle", "()V", null, null).apply {
            visitAnnotation("L$annotationPath;", true).apply {
                visitArray("value").apply {
                    visit(null, "/library-only")
                    visitEnd()
                }
                visitEnd()
            }
            visitEnd()
        }
        writeClass("dependency/LibraryController", controllerWriter)

        // No source files exist: the dependency consists exclusively of compiled classes.
        PsiTestUtil.addLibrary(testRootDisposable, module, "binary-rest-dependency", directory.path, "classes")
        myFixture.addFileToProject("src/example/ProjectController.java", """
            package example;
            import org.springframework.web.bind.annotation.GetMapping;
            public class ProjectController {
                @GetMapping("/project-only") public void handle() {}
            }
        """.trimIndent())

        ReadAction.run<RuntimeException> {
            val facade = JavaPsiFacade.getInstance(project)
            val annotationName = "org.springframework.web.bind.annotation.GetMapping"
            val annotationClass = facade.findClass(annotationName, GlobalSearchScope.allScope(project))
            assertTrue("Annotation must resolve directly from compiled classes", annotationClass is PsiCompiledElement)
            assertNull(facade.findClass(annotationName, GlobalSearchScope.projectScope(project)))
            val libraryClass = facade.findClass("dependency.LibraryController", GlobalSearchScope.allScope(project))
            assertTrue(libraryClass!!.findMethodsByName("handle", false).single().hasAnnotation(annotationName))

            val scanner = RestApiScanner(project)
            val candidates = scanner.collectApiCandidates()
            val items = scanner.scanBatch(candidates)
            assertEquals(listOf("GET" to "/project-only"), items.map { it.method to it.path })
            assertTrue(candidates.all { GlobalSearchScope.projectScope(project).contains(it.virtualFile!!) })
        }
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
              String defaultValue() default ValueConstants.DEFAULT_NONE;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/RequestBody.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface RequestBody { boolean required() default true; }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/org/springframework/web/bind/annotation/RequestMethod.java",
            """
            package org.springframework.web.bind.annotation;
            public enum RequestMethod { GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE }
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
        myFixture.addFileToProject("src/org/springframework/web/bind/annotation/ValueConstants.java", """
            package org.springframework.web.bind.annotation;
            public interface ValueConstants {
                String DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n";
            }
        """.trimIndent())
        myFixture.addFileToProject("src/org/springframework/web/bind/annotation/RequestHeader.java", """
            package org.springframework.web.bind.annotation;
            public @interface RequestHeader {
                boolean required() default true;
                String defaultValue() default ValueConstants.DEFAULT_NONE;
            }
        """.trimIndent())
        myFixture.addFileToProject("src/org/springframework/web/bind/annotation/PathVariable.java", """
            package org.springframework.web.bind.annotation;
            public @interface PathVariable { boolean required() default true; }
        """.trimIndent())
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
