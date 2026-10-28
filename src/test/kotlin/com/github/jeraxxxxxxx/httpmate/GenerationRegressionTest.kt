package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.constants.AppConstants
import com.github.jeraxxxxxxx.httpmate.doc.DocGenerator
import com.github.jeraxxxxxxx.httpmate.generator.DefaultJsonGenerator
import com.github.jeraxxxxxxx.httpmate.generator.JsonFieldResolver
import com.github.jeraxxxxxxx.httpmate.generator.JsonTypeResolver
import com.github.jeraxxxxxxx.httpmate.generator.MockJsonGenerator
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GenerationRegressionTest : BasePlatformTestCase() {
    private fun addCollections() {
        val declarations = mapOf(
            "Collection" to "public interface Collection<E> {}",
            "List" to "public interface List<E> extends Collection<E> {}",
            "Set" to "public interface Set<E> extends Collection<E> {}",
            "Map" to "public interface Map<K,V> {}",
            "ArrayList" to "public class ArrayList<E> implements List<E> { private int size; }",
            "HashSet" to "public class HashSet<E> implements Set<E> { private int size; }",
            "HashMap" to "public class HashMap<K,V> implements Map<K,V> { private int size; }",
            "ListIterator" to "public interface ListIterator<E> {}"
        )
        declarations.forEach { (name, declaration) ->
            myFixture.addFileToProject("java/util/$name.java", "package java.util; $declaration")
        }
    }

    fun testCollectionsAndConcreteSubclassesUseArrayShapesAndResolvedElements() {
        addCollections()
        val file = myFixture.addFileToProject("example/Containers.java", """
            package example;
            class User { int id; }
            class UserList extends java.util.ArrayList<User> {}
            class Containers {
                java.util.Collection<User> collection;
                java.util.List<User> list;
                java.util.Set<User> set;
                java.util.ArrayList<User> arrayList;
                java.util.HashSet<User> hashSet;
                UserList userList;
                java.util.Collection<? extends User> wildcard;
                User[] array;
                java.util.ListIterator<User> iterator;
            }
        """.trimIndent())
        val owner = (file as PsiClassOwner).classes.last()
        owner.fields.filter { it.name != "iterator" }.forEach { field ->
            assertEquals(field.name, "[]", DefaultJsonGenerator().generate(field.type, 0))
            val mock = MockJsonGenerator().generate(field.type, 0)
            assertTrue("${field.name}: $mock", mock.startsWith("["))
            assertTrue("Missing User element for ${field.name}: $mock", mock.contains("\"id\":"))
            assertFalse(mock.contains("\"size\":"))
        }
        assertNull(JsonTypeResolver.container(owner.findFieldByName("iterator", false)!!.type))
    }

    fun testMapImplementationsResolveReorderedGenericArguments() {
        addCollections()
        val file = myFixture.addFileToProject("example/Maps.java", """
            package example;
            class User { int id; }
            class Reordered<A,B> extends java.util.HashMap<B,A> {}
            class Maps {
                java.util.HashMap<java.lang.String,User> map;
                Reordered<User,java.lang.String> reordered;
            }
        """.trimIndent())
        (file as PsiClassOwner).classes.last().fields.forEach { field ->
            val args = JsonTypeResolver.container(field.type)!!.arguments
            assertEquals("java.lang.String", args[0]!!.canonicalText)
            assertEquals("example.User", args[1]!!.canonicalText)
            val mock = MockJsonGenerator().generate(field.type, 0)
            assertTrue(mock.contains("\"key_1\":"))
            assertTrue(mock.contains("\"id\":"))
            assertFalse(mock.contains("\"size\":"))
        }
    }

    fun testInheritedFieldsPreferTheNearestDeclarationWithoutLosingGenerics() {
        val file = myFixture.addFileToProject("example/Child.java", """
            package example;
            class Parent<T> { T id; T inherited; }
            class Middle<T> extends Parent<T> { long id; }
            class Child extends Middle<java.lang.String> { int id; }
        """.trimIndent())
        val child = (file as PsiClassOwner).classes.last()
        val type = JavaPsiFacade.getElementFactory(project).createType(child)
        val fields = JsonFieldResolver.resolve(type)
        assertEquals(listOf("id", "inherited"), fields.map { it.name })
        assertEquals(PsiTypes.intType(), fields.first().type)
        assertEquals("Child", fields.first().declaration.containingClass!!.name)
        assertEquals("java.lang.String", fields.last().type.canonicalText)
        for (generator in listOf(DefaultJsonGenerator(), MockJsonGenerator())) {
            val json = generator.generate(type, 0)
            assertEquals(1, Regex("\"id\"\\s*:").findAll(json).count())
            assertEquals(1, Regex("\"inherited\"\\s*:").findAll(json).count())
        }
    }

    fun testDepthLimitPreservesScalarsEnumsAndContainerShapes() {
        addCollections()
        val file = myFixture.addFileToProject("example/Values.java", """
            package example;
            enum Choice { ONLY }
            class Values { java.lang.String text; Choice choice; java.util.List<Values> list; java.util.Map<java.lang.String,Values> map; }
        """.trimIndent())
        val owner = (file as PsiClassOwner).classes.last()
        val depth = AppConstants.MAX_JSON_RECURSION_DEPTH + 1
        assertEquals("0", DefaultJsonGenerator().generate(PsiTypes.intType(), depth))
        assertTrue(MockJsonGenerator().generate(PsiTypes.intType(), depth).matches(Regex("\\d+")))
        assertEquals("false", DefaultJsonGenerator().generate(PsiTypes.booleanType(), depth))
        assertTrue(MockJsonGenerator().generate(PsiTypes.booleanType(), depth) in listOf("true", "false"))
        for (generator in listOf(DefaultJsonGenerator(), MockJsonGenerator())) {
            assertTrue(generator.generate(owner.findFieldByName("text", false)!!.type, depth).startsWith('"'))
            assertEquals("\"ONLY\"", generator.generate(owner.findFieldByName("choice", false)!!.type, depth))
            assertEquals("[]", generator.generate(PsiTypes.intType().createArrayType(), depth))
            assertEquals("[]", generator.generate(owner.findFieldByName("list", false)!!.type, depth))
            assertEquals("{}", generator.generate(owner.findFieldByName("map", false)!!.type, depth))
        }
    }

    fun testScalarAtTheEndOfADeepObjectGraphIsNotTurnedIntoAnObject() {
        val source = (0..AppConstants.MAX_JSON_RECURSION_DEPTH).joinToString("\n") { depth ->
            val field = if (depth < AppConstants.MAX_JSON_RECURSION_DEPTH) "N${depth + 1} next;" else "int value;"
            "class N$depth { $field }"
        }
        val file = myFixture.addFileToProject("Nodes.java", source)
        val type = JavaPsiFacade.getElementFactory(project).createType((file as PsiClassOwner).classes.first())
        assertTrue(DefaultJsonGenerator().generate(type, 0).contains("\"value\": 0"))
        assertTrue(MockJsonGenerator().generate(type, 0).contains(Regex("\"value\": \\d+")))
    }

    fun testRecordComponentsRemainAvailableToBothGenerators() {
        val file = myFixture.addFileToProject("example/UserRecord.java", """
            package example;
            public record UserRecord(int id, java.lang.String name) {}
        """.trimIndent())
        val type = JavaPsiFacade.getElementFactory(project).createType((file as PsiClassOwner).classes.single())
        for (generator in listOf(DefaultJsonGenerator(), MockJsonGenerator())) {
            val json = generator.generate(type, 0)
            assertEquals(1, Regex("\"id\"\\s*:").findAll(json).count())
            assertEquals(1, Regex("\"name\"\\s*:").findAll(json).count())
        }
    }

    private fun addHttpEntities() {
        myFixture.addFileToProject("org/springframework/http/HttpEntity.java", """
            package org.springframework.http;
            public class HttpEntity<T> { T body; int internalHeaders; }
        """.trimIndent())
        myFixture.addFileToProject("org/springframework/http/ResponseEntity.java", """
            package org.springframework.http;
            public class ResponseEntity<T> extends HttpEntity<T> { int internalStatus; }
        """.trimIndent())
    }

    fun testResponseEntityDocumentationDescribesOnlyTheHttpBody() {
        addCollections()
        addHttpEntities()
        val file = myFixture.addFileToProject("example/Controller.java", """
            package example;
            import org.springframework.http.ResponseEntity;
            class User { /** user identifier */ int id; }
            class UserResponse extends ResponseEntity<User> {}
            class Envelope<T> { T payload; }
            class Controller {
                public ResponseEntity<User> single() { return null; }
                public ResponseEntity<java.util.List<User>> list() { return null; }
                public UserResponse inherited() { return null; }
                public Envelope<User> businessWrapper() { return null; }
            }
        """.trimIndent())
        val owner = (file as PsiClassOwner).classes.last()
        for (name in listOf("single", "list", "inherited")) {
            val markdown = DocGenerator().generate(owner.findMethodsByName(name, false).single())
            val response = markdown.substringAfter("## 响应示例")
            assertTrue(response.contains("\"id\":"))
            assertFalse(response.contains("\"body\":"))
            assertFalse(response.contains("internalStatus"))
            assertFalse(response.contains("internalHeaders"))
            val table = markdown.substringAfter("## 响应参数").substringBefore("## 请求示例")
            assertTrue(table.contains("| id | int |"))
            assertFalse(table.contains("internalStatus"))
            if (name == "list") assertTrue(response.contains("```json\n["))
        }
        val business = DocGenerator().generate(owner.findMethodsByName("businessWrapper", false).single())
        assertTrue(business.substringAfter("## 响应示例").contains("\"payload\":"))
    }

    fun testVoidAndRawHttpEntitiesDoNotExposeWrapperInternals() {
        addHttpEntities()
        val file = myFixture.addFileToProject("example/EmptyController.java", """
            package example;
            import org.springframework.http.ResponseEntity;
            import org.springframework.http.HttpEntity;
            class EmptyController {
                public ResponseEntity<java.lang.Void> empty() { return null; }
                public HttpEntity<java.lang.Void> emptyEntity() { return null; }
                public ResponseEntity raw() { return null; }
            }
        """.trimIndent())
        val owner = (file as PsiClassOwner).classes.last()
        for (name in listOf("empty", "emptyEntity")) {
            val markdown = DocGenerator().generate(owner.findMethodsByName(name, false).single())
            assertTrue(markdown.contains("无响应参数"))
            assertTrue(markdown.contains("无响应体"))
        }
        val raw = DocGenerator().generate(owner.findMethodsByName("raw", false).single())
        assertFalse(raw.contains("internalStatus"))
        assertFalse(raw.contains("internalHeaders"))
        assertFalse(raw.contains("| body |"))
    }

    fun testDtoRequiredFlagsUseExplicitValidationConstraintsIncludingInheritedFields() {
        for (namespace in listOf("javax.validation.constraints", "jakarta.validation.constraints")) {
            for (annotation in listOf("NotNull", "NotBlank", "NotEmpty")) {
                myFixture.addFileToProject("${namespace.replace('.', '/')}/$annotation.java",
                    "package $namespace; public @interface $annotation {}")
            }
        }
        myFixture.addFileToProject("javax/validation/constraints/Size.java",
            "package javax.validation.constraints; public @interface Size { int min() default 0; int max() default 2147483647; }")
        val file = myFixture.addFileToProject("example/ValidationController.java", """
            package example;
            class Parent { @javax.validation.constraints.NotNull java.lang.String inherited; }
            class Input extends Parent {
                @javax.validation.constraints.NotNull java.lang.String notNull;
                @javax.validation.constraints.NotBlank java.lang.String notBlank;
                @jakarta.validation.constraints.NotEmpty java.lang.String notEmpty;
                @javax.validation.constraints.Size(min=1) java.lang.String sizeOnly;
                java.lang.String optional;
            }
            class ValidationController { public void handle(Input body) {} }
        """.trimIndent())
        val method = (file as PsiClassOwner).classes.last().methods.single()
        val markdown = DocGenerator().generate(method)
        for (name in listOf("notNull", "notBlank", "notEmpty", "inherited")) {
            assertTrue("Expected required field $name\n$markdown", markdown.contains("| $name | String | 是 |"))
        }
        for (name in listOf("sizeOnly", "optional")) {
            assertTrue("Expected optional field $name\n$markdown", markdown.contains("| $name | String | 否 |"))
        }
    }
}
