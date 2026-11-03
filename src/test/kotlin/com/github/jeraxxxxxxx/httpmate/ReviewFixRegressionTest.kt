package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.actions.ActionContextResolver
import com.github.jeraxxxxxxx.httpmate.doc.DocGenerator
import com.github.jeraxxxxxxx.httpmate.generator.DefaultJsonGenerator
import com.github.jeraxxxxxxx.httpmate.generator.MockJsonGenerator
import com.github.jeraxxxxxxx.httpmate.services.FallbackApiParser
import com.github.jeraxxxxxxx.httpmate.services.RestApiScanner
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewFixRegressionTest : BasePlatformTestCase() {
    private fun add(path: String, source: String) = myFixture.addFileToProject(path, source.trimIndent())
    private fun methods(source: String): Array<PsiMethod> =
        (add("example/Controller.java", source) as PsiClassOwner).classes.last().methods
    private fun scan(methods: Array<PsiMethod>) = RestApiScanner(project).scanBatch(methods.map { SmartPointerManager.createPointer(it) })

    private fun spring() {
        add("org/springframework/web/bind/annotation/RequestMethod.java", """
            package org.springframework.web.bind.annotation;
            public enum RequestMethod { GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE }
        """)
        add("org/springframework/web/bind/annotation/RequestMapping.java", """
            package org.springframework.web.bind.annotation;
            public @interface RequestMapping { String[] value() default {}; String[] path() default {}; RequestMethod[] method() default {}; }
        """)
        add("org/springframework/web/bind/annotation/GetMapping.java", """
            package org.springframework.web.bind.annotation;
            @RequestMapping(method=RequestMethod.GET)
            public @interface GetMapping { String[] value() default {}; String[] path() default {}; }
        """)
        add("org/springframework/core/annotation/AliasFor.java", """
            package org.springframework.core.annotation;
            public @interface AliasFor { String value() default ""; String attribute() default ""; Class<?> annotation() default java.lang.annotation.Annotation.class; }
        """)
    }

    private fun jax() {
        add("jakarta/ws/rs/Path.java", "package jakarta.ws.rs; public @interface Path { String value(); }")
        for (verb in listOf("GET", "POST")) add("jakarta/ws/rs/$verb.java", "package jakarta.ws.rs; public @interface $verb {}")
        for (name in listOf("QueryParam", "PathParam", "DefaultValue")) {
            add("jakarta/ws/rs/$name.java", "package jakarta.ws.rs; public @interface $name { String value(); }")
        }
        add("jakarta/ws/rs/core/Context.java", "package jakarta.ws.rs.core; public @interface Context {}")
    }

    private fun validation() {
        add("jakarta/validation/constraints/NotNull.java", """
            package jakarta.validation.constraints; public @interface NotNull { Class<?>[] groups() default {}; }
        """)
        add("jakarta/validation/constraints/Size.java", """
            package jakarta.validation.constraints; public @interface Size { int min() default 0; int max() default 2147483647; Class<?>[] groups() default {}; }
        """)
        add("org/springframework/validation/annotation/Validated.java", """
            package org.springframework.validation.annotation; public @interface Validated { Class<?>[] value() default {}; }
        """)
    }

    fun testKotlinClassAndMethodContextsProduceJsonAndDocumentation() {
        val file = add("example/Example.kt", """
            package example
            data class Example(val count: Int)
            class Controller { fun find(): Example = Example(1) }
        """)
        assertEquals("kotlin", file.language.id)
        val field = file.findElementAt(file.text.indexOf("count"))!!
        val owner = ActionContextResolver.findPsiClass(field)
        assertNotNull("Kotlin classes must be converted to Java PSI", owner)
        val type = JavaPsiFacade.getElementFactory(project).createType(owner!!)
        assertTrue(DefaultJsonGenerator().generate(type, 0).contains("\"count\": 0"))
        val method = ActionContextResolver.findPsiMethod(file.findElementAt(file.text.indexOf("find")))
        assertNotNull(method)
        assertTrue(DocGenerator().generate(method!!).contains("\"count\":"))
    }

    fun testKotlinMappingAndLightPointersRemainUsable() {
        spring()
        val file = add("example/Controller.kt", """
            package example
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestMapping
            @RequestMapping("/api") class Controller {
                @GetMapping("/count") fun count(): Int = 1
            }
        """)
        val method = ActionContextResolver.findPsiMethod(file.findElementAt(file.text.indexOf("count()")))!!
        val pointer = SmartPointerManager.createPointer(method)
        assertNotNull(pointer.element)
        assertEquals(listOf("GET" to "/api/count"), scan(arrayOf(pointer.element!!)).map { it.method to it.path })
        assertTrue(DocGenerator().generate(pointer.element!!).contains("/api/count"))
    }

    fun testComposedMappingsAndAliasesAreSharedBySearchAndDocs() {
        spring()
        add("example/PublicGet.java", """
            package example;
            @org.springframework.web.bind.annotation.RequestMapping(method=org.springframework.web.bind.annotation.RequestMethod.GET)
            public @interface PublicGet {
                @org.springframework.core.annotation.AliasFor(annotation=org.springframework.web.bind.annotation.RequestMapping.class, attribute="path")
                String[] routes() default {};
            }
        """)
        add("example/ApiBase.java", """
            package example;
            @org.springframework.web.bind.annotation.RequestMapping("/api") public @interface ApiBase {}
        """)
        val methods = methods("""
            package example;
            @ApiBase class Controller { @PublicGet(routes={"/users", "/people"}) public void list() {} }
        """)
        val scanner = RestApiScanner(project)
        val items = scanner.scanBatch(scanner.collectApiCandidates())
        assertEquals(setOf("GET" to "/api/users", "GET" to "/api/people"), items.map { it.method to it.path }.toSet())
        val doc = DocGenerator().generate(methods.single())
        assertTrue(doc.contains("| 请求方式 | GET |"))
        assertTrue(doc.contains("/api/users, /api/people"))
    }

    fun testSpringInterfaceMappingUsesConcreteControllerContext() {
        spring()
        add("org/springframework/web/bind/annotation/RestController.java", """
            package org.springframework.web.bind.annotation;
            public @interface RestController {}
        """)
        val implementations = methods("""
            package example;
            import org.springframework.web.bind.annotation.*;
            @RequestMapping("/api") interface Contract {
                @GetMapping("/users") String users();
            }
            @RestController @RequestMapping("/other") class Other implements Contract {
                public String users() { return ""; }
            }
            @RestController class Controller implements Contract {
                public String users() { return ""; }
                @GetMapping("/health") public String health() { return ""; }
            }
        """)
        val scanner = RestApiScanner(project)
        assertEquals(listOf("/api/health", "/api/users", "/other/users"),
            scanner.scanBatch(scanner.collectApiCandidates()).map { it.path }.sorted())
        assertTrue(DocGenerator().generate(implementations.first { it.name == "users" }).contains("/api/users"))
    }

    fun testTransitiveSpringAliasOverridesIntermediateAttribute() {
        spring()
        val endpoint = methods("""
            package example;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.core.annotation.AliasFor;
            @RequestMapping(path="/fixed", method=RequestMethod.GET)
            @interface PublicGet {
                @AliasFor(annotation=RequestMapping.class, attribute="path") String[] routes() default {};
            }
            @PublicGet @interface V2Get {
                @AliasFor(annotation=PublicGet.class, attribute="routes") String[] value() default {};
            }
            class Controller {
                @V2Get("/users") public void users() {}
                @V2Get public void fixed() {}
            }
        """)
        assertEquals(listOf("/fixed", "/users"), scan(endpoint).map { it.path }.sorted())
        assertTrue(DocGenerator().generate(endpoint.first { it.name == "users" }).contains("/users"))
    }

    fun testJaxResourceCanBeBothRootAndLocatorTarget() {
        jax()
        val source = """
            package example;
            import jakarta.ws.rs.*;
            @Path("/widgets") class WidgetsResource {
                @Path("/{id}") public WidgetResource locate() { return new WidgetResource(); }
            }
            @Path("/widget") class WidgetResource {
                @GET public String get() { return ""; }
            }
        """.trimIndent()
        val endpoint = methods(source)
        val expected = listOf("/widget", "/widgets/{id}")
        assertEquals(expected, scan(endpoint).map { it.path }.sorted())
        assertEquals(expected, FallbackApiParser.parse(source).map { it.path }.sorted())
        assertTrue(DocGenerator().generate(endpoint.single()).contains("/widgets/{id}"))
    }

    fun testNestedComposedMappingRetainsItsFixedPath() {
        spring()
        add("example/First.java", """
            package example;
            @org.springframework.web.bind.annotation.RequestMapping("/fixed") public @interface First { String[] value() default { }; }
        """)
        add("example/Second.java", "package example; @First public @interface Second {}")
        val result = scan(methods("package example; class Controller { @Second public void get() {} }"))
        assertEquals(listOf("/fixed"), result.map { it.path })
    }

    fun testUnknownPathsAreNotRootAndEmptyMappingsStillAreRoot() {
        spring()
        val methods = methods("""
            package example;
            class Controller {
                @org.springframework.web.bind.annotation.GetMapping(UNKNOWN) public void missing() {}
                @org.springframework.web.bind.annotation.GetMapping public void root() {}
            }
        """)
        assertEquals(listOf("/"), scan(methods).map { it.path })
        assertTrue(DocGenerator().generate(methods.first()).contains("无法解析"))
    }

    fun testUnresolvedClassPathDoesNotProducePartialRoutes() {
        spring()
        val methods = methods("""
            package example;
            @org.springframework.web.bind.annotation.RequestMapping(UNKNOWN)
            class Controller { @org.springframework.web.bind.annotation.GetMapping("/users") public void get() {} }
        """)
        assertTrue(scan(methods).isEmpty())
    }

    fun testJaxLocatorsAreNotEndpointsButReachableChildrenHaveFullPaths() {
        jax()
        add("example/Root.java", """
            package example;
            @jakarta.ws.rs.Path("/root") class Root {
                @jakarta.ws.rs.Path("/child") public Child child() { return null; }
            }
            class Child { @jakarta.ws.rs.GET @jakarta.ws.rs.Path("/details") public void get() {} }
        """)
        val scanner = RestApiScanner(project)
        val result = scanner.scanBatch(scanner.collectApiCandidates())
        assertEquals(listOf("GET" to "/root/child/details"), result.map { it.method to it.path })
    }

    fun testJaxLocatorCyclesTerminateWithoutInventingRoutes() {
        jax()
        val methods = methods("""
            package example;
            class Controller {
                @jakarta.ws.rs.Path("/loop") public Controller loop() { return this; }
                @jakarta.ws.rs.GET public void get() {}
            }
        """)
        assertTrue(scan(methods).isEmpty())
    }

    fun testJaxEntityAndQueryNamesAreDocumented() {
        jax()
        val methods = methods("""
            package example;
            class Input { int count; }
            @jakarta.ws.rs.Path("/users") class Controller {
                @jakarta.ws.rs.POST public void create(@jakarta.ws.rs.QueryParam("q") java.lang.String keyword, Input body) {}
            }
        """)
        val doc = DocGenerator().generate(methods.single())
        assertTrue(doc.contains("| q | String | 否 |"))
        assertTrue(doc.substringAfter("## 请求示例").substringBefore("## 响应示例").contains("\"count\":"))
    }

    fun testJaxContextIsNotMistakenForEntityBody() {
        jax()
        val methods = methods("""
            package example;
            @jakarta.ws.rs.Path("/") class Controller {
                @jakarta.ws.rs.GET public void read(@jakarta.ws.rs.core.Context java.lang.Object request) {}
            }
        """)
        assertTrue(DocGenerator().generate(methods.single()).contains("## 请求示例\n\n无请求体"))
    }

    fun testSpringParameterAliasesUseNameAndValueNotJavaNames() {
        for (name in listOf("RequestParam", "PathVariable", "RequestHeader", "CookieValue")) {
            add("org/springframework/web/bind/annotation/$name.java", """
                package org.springframework.web.bind.annotation;
                public @interface $name { String name() default ""; String value() default ""; boolean required() default true; }
            """)
        }
        val methods = methods("""
            class Controller { public void get(
                @org.springframework.web.bind.annotation.RequestParam(name="q") java.lang.String keyword,
                @org.springframework.web.bind.annotation.PathVariable("id") long userId,
                @org.springframework.web.bind.annotation.RequestHeader("X-Trace") java.lang.String trace,
                @org.springframework.web.bind.annotation.CookieValue("session") java.lang.String cookie) {} }
        """)
        val doc = DocGenerator().generate(methods.single())
        for (name in listOf("q", "id", "X-Trace", "session")) assertTrue(doc.contains("| $name |"))
        assertFalse(doc.contains("| keyword |"))
    }

    fun testSelectedValidationGroupsAndInheritedGroups() {
        validation()
        val methods = methods("""
            interface Create {} interface Update {} interface Admin extends Update {}
            class Input {
                @jakarta.validation.constraints.NotNull(groups=Create.class) java.lang.String createOnly;
                @jakarta.validation.constraints.NotNull(groups=Update.class) java.lang.String updateOnly;
                @jakarta.validation.constraints.NotNull java.lang.String defaultOnly;
            }
            class Controller {
                public void edit(@org.springframework.validation.annotation.Validated(Admin.class) Input body) {}
            }
        """)
        val doc = DocGenerator().generate(methods.single())
        assertTrue(doc.contains("| createOnly | String | 否 |"))
        assertTrue(doc.contains("| updateOnly | String | 是 |"))
        assertTrue(doc.contains("| defaultOnly | String | 否 |"))
    }

    fun testGetterConstraintsIncludingInheritedPropertiesAndLength() {
        validation()
        val methods = methods("""
            class Parent { private java.lang.String email; public java.lang.String getEmail() { return email; } }
            class Input extends Parent {
                @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min=2,max=10)
                public java.lang.String getEmail() { return super.getEmail(); }
            }
            class Controller { public void create(Input body) {} }
        """)
        val doc = DocGenerator().generate(methods.single())
        assertTrue(doc.contains("| email | String | 是 | 2-10 |"))
    }

    fun testDefaultGroupIgnoresConstraintsFromOtherGroups() {
        validation()
        val methods = methods("""
            interface Create {}
            class Input {
                @jakarta.validation.constraints.NotNull(groups=Create.class) java.lang.String createOnly;
                @jakarta.validation.constraints.Size(min=2,max=10,groups=Create.class) java.lang.String text;
            }
            class Controller { public void create(Input body) {} }
        """)
        val doc = DocGenerator().generate(methods.single())
        assertTrue(doc.contains("| createOnly | String | 否 |"))
        assertTrue(doc.contains("| text | String | 否 |  |"))
    }

    fun testGroupSequencesAndMethodValidationAreLabeledConditional() {
        validation()
        add("jakarta/validation/GroupSequence.java", "package jakarta.validation; public @interface GroupSequence { Class<?>[] value(); }")
        val methods = methods("""
            interface Create {}
            @jakarta.validation.GroupSequence({Create.class}) interface Ordered {}
            class Input { @jakarta.validation.constraints.NotNull(groups=Create.class) java.lang.String name; }
            @org.springframework.validation.annotation.Validated(Create.class)
            class Controller {
                public void ordered(@org.springframework.validation.annotation.Validated(Ordered.class) Input body) {}
                public void methodValidation(Input body) {}
            }
        """)
        for (method in methods) assertTrue(DocGenerator().generate(method).contains("| name | String | 条件必填 |"))
    }

    fun testMarkdownCellsEscapePipesNewlinesAndMarkup() {
        add("jakarta/validation/constraints/Pattern.java", "package jakarta.validation.constraints; public @interface Pattern { String regexp(); }")
        val methods = methods("""
            class Input {
                /** A|B <tag> */ @jakarta.validation.constraints.Pattern(regexp="A|B") java.lang.String value;
                /** escaped \| pipe */ int raw;
            }
            class Controller { public Input create(Input body) { return null; } }
        """)
        val doc = DocGenerator().generate(methods.single())
        assertTrue(doc.contains("格式: A\\|B"))
        assertTrue(doc.contains("A\\|B &lt;tag&gt;"))
        assertTrue(doc.contains("escaped &#92;\\| pipe"))
    }

    fun testPrimitiveAndBoxedCharProduceSingleCharacterJsonStrings() {
        val context = add("Chars.java", "class Chars {}")
        val boxed = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.Character", context)
        for (generator in listOf(DefaultJsonGenerator(), MockJsonGenerator())) {
            for (type in listOf(PsiTypes.charType(), boxed)) {
                assertTrue(generator.generate(type, 0).matches(Regex("\"[a-z]\"")))
            }
        }
    }

    fun testFallbackRejectsUnrelatedQualifiedAndImportedMappings() {
        val sources = listOf(
            "class C { @other.GetMapping(\"/wrong\") public void get() {} }",
            "import other.GetMapping; class C { @GetMapping(\"/wrong\") public void get() {} }",
            "import other.*; class C { @GetMapping(\"/wrong\") public void get() {} }",
            "@interface GetMapping { String value(); } class C { @GetMapping(\"/wrong\") public void get() {} }"
        )
        sources.forEach { assertTrue(FallbackApiParser.parse(it).isEmpty()) }
        assertEquals(listOf("/ok"), FallbackApiParser.parse("""
            import org.springframework.web.bind.annotation.GetMapping as Read
            class C { @Read("/ok") fun get() {} }
        """.trimIndent()).map { it.path })
        assertEquals(listOf("/ok"), FallbackApiParser.parse("import org.springframework.web.bind.annotation.*; class C { @GetMapping(\"/ok\") public void get() {} }").map { it.path })
    }

    fun testFallbackResolvesKotlinTemplatesAndSkipsUnknownTemplates() {
        val dollar = '$'
        val source = """
            const val BASE = "/api"
            class C {
                companion object { const val ID = "id" }
                @GetMapping("${dollar}{BASE}/users/${dollar}ID") fun get() {}
                @GetMapping("${dollar}{UNKNOWN}/users") fun unknown() {}
            }
        """.trimIndent()
        assertEquals(listOf("/api/users/id"), FallbackApiParser.parse(source).map { it.path })
    }

    fun testFallbackPreservesEscapedDollarsAndJavaLiteralDollars() {
        val dollar = '$'
        val kotlin = "class C { @GetMapping(\"/cost/\\${dollar}price\") fun get() {} }"
        val java = "class C { @GetMapping(\"/cost/${dollar}price\") public void get() {} }"
        for (source in listOf(kotlin, java)) {
            assertEquals(listOf("/cost/${dollar}price"), FallbackApiParser.parse(source).map { it.path })
        }
    }

    fun testFallbackDoesNotTreatSubresourceLocatorAsAllMethods() {
        assertTrue(FallbackApiParser.parse("@Path(\"/root\") class C { @Path(\"/child\") public Object child() {} }").isEmpty())
        assertEquals(listOf("GET" to "/root/child/details"), FallbackApiParser.parse("""
            @Path("/root") class Root { @Path("/child") public Child child() {} }
            class Child { @GET @Path("/details") public void get() {} }
        """.trimIndent()).map { it.method to it.path })
    }
}
