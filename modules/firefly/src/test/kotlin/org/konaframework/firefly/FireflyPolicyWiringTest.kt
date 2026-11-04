package org.konaframework.firefly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.auth.Auth
import org.konaframework.auth.AuthServiceProvider
import org.konaframework.auth.authorization.Gate
import org.konaframework.core.Application
import org.konaframework.core.Config
import org.konaframework.core.Kona
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Router
import java.nio.file.Files
import java.util.function.Supplier

class FireflyPolicyWiringTest {
    @BeforeEach
    fun clean() {
        Firefly.reset()
        Gate.clearDefault()
    }

    @AfterEach
    fun restore() {
        Firefly.reset()
        Kona.reset()
        Gate.clearDefault()
        Auth.clearManager()
    }

    @Test
    fun configReadsPolicyPackagesAndTheStrictSwitch() {
        val config = Config()
        assertEquals(emptyList<String>(), FireflyConfig.policies(config))
        assertNull(FireflyConfig.authorizeWithoutPolicy(config))
        config.set("firefly.policies", listOf("a.policies", " "))
        config.set("firefly.authorize_without_policy", false)
        assertEquals(listOf("a.policies"), FireflyConfig.policies(config))
        assertEquals(false, FireflyConfig.authorizeWithoutPolicy(config))
        config.set("firefly.policies", "x.one, y.two")
        assertEquals(listOf("x.one", "y.two"), FireflyConfig.policies(config))
    }

    @Test
    fun theProviderTakesTheContainerGateAndTheConfiguredSwitches() {
        val app = build { config ->
            config.set("firefly.policies", listOf("nowhere.policies", "app.policies"))
            config.set("firefly.authorize_without_policy", false)
        }
        val container = app.resolve(Gate::class.java)
        assertSame(container, Firefly.activeGate())
        assertFalse(Firefly.authorizesWithoutPolicy())
        assertNotNull(Firefly.policyFor(AzDiscovered::class.java), "found in the second configured package")
        assertTrue(container.getPolicyFor(AzDiscovered::class.java) is app.policies.AzDiscoveredPolicy)
    }

    @Test
    fun anExplicitGateIsNotReplacedByTheContainerGate() {
        val mine = Gate()
        Firefly.gate(mine)
        build { }
        assertSame(mine, Firefly.activeGate())
        assertTrue(Firefly.authorizesWithoutPolicy(), "unset config keeps the default")
    }

    @Test
    fun withoutAContainerGateTheDefaultOneIsUsed() {
        assertNull(Firefly.activeGate())
        val fallback = Gate()
        Gate.setDefault(fallback)
        assertSame(fallback, Firefly.activeGate())
        Firefly.gate(null)
        assertSame(fallback, Firefly.activeGate())
    }

    @Test
    fun policiesAreDiscoveredByConventionOnce() {
        val gate = Gate()
        Firefly.gate(gate)
        assertNotNull(Firefly.policyFor(AzDiscovered::class.java))
        assertNotNull(Firefly.policyFor(AzDiscovered::class.java), "now registered on the gate")
        assertNull(Firefly.policyFor(AzPlain::class.java), "no AzPlainPolicy anywhere")
        assertNull(Firefly.policyFor(AzPlain::class.java), "and the miss is remembered")

        val other = Gate()
        Firefly.gate(other)
        Firefly.policyPackages("elsewhere", " ")
        assertNull(Firefly.policyFor(AzDiscovered::class.java), "only the configured packages are searched")
        Firefly.policyPackages("app.policies")
        assertNotNull(Firefly.policyFor(AzDiscovered::class.java))

        Firefly.gate(null)
        assertNull(Firefly.policyFor(AzDiscovered::class.java), "no gate, no policy")
    }

    @Test
    fun mountingAPanelRegistersThePolicyOfEachResourceModel() {
        val gate = Gate()
        Firefly.gate(gate)
        val resource = object : Resource("discovered") {
            init {
                model(AzDiscovered::class.java)
            }
        }
        val bare = object : Resource("bare") {}
        Panel().id("desk").path("admin").authenticate(false).resource(resource).resource(bare).mount(Router())
        assertNotNull(gate.getPolicyFor(AzDiscovered::class.java))
        assertFalse(resource.can("create"), "the discovered policy forbids create")
        assertTrue(resource.can("viewAny").not(), "and viewAny needs a signed-in user")
        FireflyAuth.actingAs(azUser("viewer"), Supplier { assertTrue(resource.canViewAny()) })
    }

    @Test
    fun aPanelSwitchOverridesTheGlobalOneForItsResources() {
        Firefly.gate(Gate())
        val strictResource = object : Resource("plain") {
            init {
                model(AzPlain::class.java)
            }
        }
        val relaxed = object : Resource("plain2") {
            init {
                model(AzPlain::class.java)
                authorizeWithoutPolicy(true)
            }
        }
        Panel().id("desk").path("admin").authenticate(false).authorizeWithoutPolicy(false)
            .resource(strictResource).resource(relaxed).mount(Router())
        assertFalse(strictResource.canViewAny())
        assertTrue(relaxed.canViewAny())
        Firefly.authorizeWithoutPolicy(false)
        val global = object : Resource("plain3") {
            init {
                model(AzPlain::class.java)
            }
        }
        assertFalse(global.canViewAny())
    }

    private fun build(configure: (Config) -> Unit): Application {
        azDatabase()
        val base = Files.createTempDirectory("firefly-wiring")
        return Application.builder(base)
            .withProvider(AuthServiceProvider())
            .withProvider(HttpServiceProvider())
            .withProvider(FireflyServiceProvider())
            .configure { configure(it) }
            .build()
    }
}
