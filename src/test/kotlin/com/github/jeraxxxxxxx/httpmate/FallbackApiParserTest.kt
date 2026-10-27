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

    @Test
    fun constantsAndConcatenationsAreEvaluatedAsWholeExpressions() {
        assertEquals(setOf("GET" to "/api/users", "GET" to "/api/admin"), routes("""
            class Controller {
                static final String BASE = "/api";
                static final String USERS = BASE + "/users";
                @GetMapping(path = {USERS, (BASE + "/admin")}) public void users() {}
                @GetMapping(MISSING) public void unresolved() {}
                @GetMapping(MISSING + "/partial") public void partial() {}
            }
        """))
    }

    @Test
    fun kotlinConstantsAndQualifiedConstantsRespectTheirOwners() {
        assertEquals(setOf("GET" to "/top/users", "GET" to "/first", "GET" to "/first/bare",
            "GET" to "/first/short", "GET" to "/second", "GET" to "/routes/item"), routes("""
            const val TOP = "/top"
            object Routes { const val ROOT = "/routes" }
            class First {
                companion object { const val API = "/first" }
                @GetMapping(First.Companion.API) fun first() {}
                @GetMapping(API + "/bare") fun bare() {}
                @GetMapping(First.API + "/short") fun short() {}
                @GetMapping(TOP + "/users") fun users() {}
            }
            class Second {
                const val API = "/second"
                @GetMapping(API) fun second() {}
                @GetMapping(Routes.ROOT + "/item") fun item() {}
            }
        """))
    }

    @Test
    fun constantsDoNotLeakAcrossControllersAndCyclesAreIgnored() {
        assertEquals(setOf("GET" to "/first", "GET" to "/second"), routes("""
            class First {
                static final String API = "/first";
                @GetMapping(API) public void first() {}
            }
            class Second {
                static final String API = "/second";
                static final String CYCLE = CYCLE;
                @GetMapping(API) public void second() {}
                @GetMapping(CYCLE) public void cycle() {}
            }
            class Third { @GetMapping(API) public void missing() {} }
        """))
    }

    @Test
    fun constructorAnnotationsDoNotHideKotlinClassBodies() {
        assertEquals(setOf("GET" to "/api/users", "GET" to "/next"), routes("""
            class Empty
            @RequestMapping("/api")
            class Controller @Inject @Other("ignored") private constructor(val service: Service) {
                @GetMapping("/users") fun users() {}
            }
            @RequestMapping("/next") class Next { @GetMapping fun next() {} }
        """))
    }

    @Test
    fun classHttpMethodsAreInheritedAndCombinedWithMethodDeclarations() {
        assertEquals(setOf("POST" to "/api/inherited", "POST" to "/api/both", "GET" to "/api/both"), routes("""
            @RequestMapping(value = "/api", method = RequestMethod.POST)
            class Controller {
                @RequestMapping("/inherited") public void inherited() {}
                @GetMapping("/both") public void both() {}
            }
        """))
    }

    @Test
    fun literalStructuralCharactersRemainPaths() {
        assertEquals(setOf("GET" to "/{", "GET" to "/+"), routes("""
            class Controller {
                @GetMapping("{") public void brace() {}
                @GetMapping("+") public void plus() {}
            }
        """))
    }

    private fun routes(text: String) = FallbackApiParser.parse(text).map { it.method to it.path }.toSet()
}
