package com.example.collabpro.foundation

import com.example.collabpro.features.campaign.infrastructure.remote.*
import com.example.collabpro.features.identity.infrastructure.remote.IdentityApi
import org.junit.Assert.*
import org.junit.Test
import retrofit2.http.*
import java.io.File

class ArchitectureTest {
    private val source = File("src/main/java/com/example/collabpro")

    @Test fun `real domain and use cases do not depend on Android or infrastructure`() {
        val sources = source.walkTopDown().filter { it.isFile && it.extension == "kt" &&
            (it.invariantSeparatorsPath.contains("/domain/") || it.invariantSeparatorsPath.contains("/application/")) }.toList()
        assertTrue(sources.isNotEmpty())
        val prohibited = Regex("^import (android\\.|androidx\\.|retrofit2\\.|okhttp3\\.|com\\.google\\.gson\\.|dagger\\.|.*\\.infrastructure\\.|.*\\.presentation\\.)", RegexOption.MULTILINE)
        for (file in sources) assertFalse("Invalid dependency in $file", prohibited.containsMatchIn(file.readText()))
    }
    @Test fun `DI never binds preview repositories to real ports`() {
        val modules = source.walkTopDown().filter { it.isFile && it.name.endsWith("Module.kt") }.toList()
        assertTrue(modules.size >= 3)
        for (file in modules) assertFalse("Preview binding in $file", file.readText().contains("Preview"))
    }
    @Test fun `retrofit contains exactly the twenty five mobile initiated operations`() {
        val httpAnnotations = setOf(GET::class.java, POST::class.java, PUT::class.java, DELETE::class.java)
        val methods = listOf(IdentityApi::class.java, CampaignApi::class.java, ApplicationApi::class.java)
            .flatMap { it.declaredMethods.toList() }
            .filter { method -> method.annotations.any { it.annotationClass.java in httpAnnotations } }
        assertEquals(25, methods.size)
        assertFalse(methods.any { it.name == "callback" })
    }
    @Test fun `future bounded contexts remain prototype only`() {
        for (context in listOf("collaboration", "billing", "performance")) {
            val files = File(source, "features/$context").walkTopDown().filter { it.isFile && it.extension == "kt" }
            assertFalse("Future context must not call unsupported APIs: $context", files.any {
                it.readText().contains("import retrofit2") || it.readText().contains("@HiltViewModel")
            })
        }
    }
}
