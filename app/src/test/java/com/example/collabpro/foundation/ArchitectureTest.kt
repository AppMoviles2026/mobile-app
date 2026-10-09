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
    @Test fun `connected screens cannot import demo repositories or prototype state`() {
        val paths = listOf("features/identity/presentation/HomeScreen.kt", "features/identity/presentation/profile",
            "features/campaign/presentation/manage", "features/campaign/presentation/discovery",
            "features/campaign/presentation/applications", "features/campaign/presentation/dashboard")
        for (path in paths) for (file in File(source, path).walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val text = file.readText()
            assertFalse("Prototype dependency in $file", Regex("^import .*?(Preview|AppState|SampleData)", RegexOption.MULTILINE).containsMatchIn(text))
        }
    }
    @Test fun `every original preview remains available without a view model or network`() {
        val text = File(source, "navigation/PreviewScreens.kt").readText()
        val originals = """Welcome AboutBrand AboutCreator HowBrand HowCreator Contact RolePicker RegisterBrand RegisterCreator
            Login Recover ResetPassword ResetCompleted SessionGate BrandHome CreatorHome BrandProfile CreatorProfile SocialAccounts
            CreatorProfileError LinkedAccounts SocialDenied SocialChecking CampaignSearch CampaignDetail CampaignEmpty CampaignSearchError
            ClosedCampaign ApplicationForm MyApplications ApplicationDetail ApplicationConflict CancelledApplication ApplicationsError
            BrandCampaigns CampaignForm CampaignTerms PartialCampaign OwnCampaignsError Applicants ApplicantDetail Agreement Collaborations
            CollaborationDetail EvidenceForm ReviewDeliverable Incidents IncidentForm PaymentMethods PaymentForm Plans Subscription
            Compensation Results History HistoryDetail WelcomeEnglish""".trim().split(Regex("\\s+"))
        assertEquals(57, originals.size)
        for (name in originals) assertTrue("Missing $name", text.contains("fun ${name}Preview("))
        assertFalse(text.contains("ViewModel")); assertFalse(text.contains("Retrofit"))
    }
    @Test fun `only opaque social result links enter mobile and Custom Tabs replace generic browser`() {
        val activity = File(source, "MainActivity.kt").readText()
        val browser = File(source, "features/identity/infrastructure/browser/SocialCustomTabs.kt").readText()
        assertTrue(activity.contains("SocialCustomTabs.open")); assertFalse(activity.contains("startActivity("))
        assertTrue(browser.contains("CustomTabsIntent.Builder()")); assertTrue(browser.contains("trustedBrowserUrl"))
        assertFalse(browser.contains("WebView")); assertFalse(browser.contains("Authorization:"))
        assertTrue(File(source, "navigation/CollabApp.kt").readText().contains("PROTOTIPO • datos de ejemplo"))
    }
}
