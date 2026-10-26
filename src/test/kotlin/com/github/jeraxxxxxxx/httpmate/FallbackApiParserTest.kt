package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.services.FallbackApiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackApiParserTest {
    @Test
    fun methodMappingWithoutClassMappingIsNotSkipped() {
        val text = """class Users {
            @RequestMapping("/users") public String users() { return ""; }
            @GetMapping("/other") public String other() { return ""; }
        }"""
        assertEquals(setOf("ALL" to "/users", "GET" to "/other"), routes(text))
    }

    @Test
    fun identicalClassAndMethodPathsAreBothRetained() {
        assertEquals(setOf("ALL" to "/api/api"), routes("""
            @RequestMapping("/api") class Controller {
                @RequestMapping("/api") public void handle() {}
            }
        """))
    }

    @Test
    fun pathsAndJaxRsVerbsBelongToTheirOwnDeclarations() {
        assertEquals(setOf("GET" to "/first", "POST" to "/second/item"), routes("""
            @Path("/first") class First {
                @GET public void first() {}
            }
            @Path("/second") class Second {
                @POST @Path("/item") public void second() {}
            }
        """))
    }

    @Test
    fun commentsAndStringsDoNotCreateMappingsOrChangeBraceDepth() {
        val text = """
            // @RequestMapping("/fake") class Fake {}
            class Controller {
                /* @GetMapping("/comment") public void fake() {} */
                String marker = "@GetMapping(\"/string\") {";
                @GetMapping("/real") public String real() { return "}"; }
            }
        """
        val endpoints = FallbackApiParser.parse(text)
        assertEquals(listOf("GET" to "/real"), endpoints.map { it.method to it.path })
        assertTrue(text.substring(endpoints.single().offset).startsWith("@GetMapping"))
    }

    @Test
    fun kotlinExpressionBodiesAndMultipleClassesAreSupported() {
        assertEquals(setOf("GET" to "/api/one", "POST" to "/two"), routes("""
            @RequestMapping("/api")
            class First {
                @GetMapping("/one")
                fun one(): String = "one"
            }
            class Second {
                @PostMapping("/two")
                fun two() = "two"
            }
        """))
    }

    @Test
    fun arraysOfPathsAndMethodsAreExpandedWithoutUsingOtherAttributes() {
        assertEquals(setOf("GET" to "/a", "POST" to "/a", "GET" to "/b", "POST" to "/b"), routes("""
            class Controller {
                @RequestMapping(path = {"/a", "/b"}, method = {RequestMethod.GET, RequestMethod.POST},
                    produces = "application/json")
                public void handle() {}
            }
        """))
    }

    @Test
    fun kotlinArraysAndQualifiedAnnotationNamesAreSupported() {
        assertEquals(setOf("GET" to "/a", "GET" to "/b"), routes("""
            class Controller {
                @org.springframework.web.bind.annotation.GetMapping(path = ["/a", "/b"])
                fun handle() {}
            }
        """))
    }

    private fun routes(text: String) = FallbackApiParser.parse(text).map { it.method to it.path }.toSet()
}
