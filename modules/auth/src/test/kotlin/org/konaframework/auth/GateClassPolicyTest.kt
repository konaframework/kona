package org.konaframework.auth

import org.konaframework.auth.authorization.Authorizable
import org.konaframework.auth.authorization.AuthorizationException
import org.konaframework.auth.authorization.Gate
import org.konaframework.auth.authorization.Policy
import org.konaframework.auth.middleware.Authorize
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GateClassPolicyTest {
    data class Currency(val code: String)

    class CurrencyPolicy : Policy {
        var beforeCalls = 0
        var authorizeClassCalls = 0

        override fun before(user: Authenticatable?, ability: String): Boolean? {
            beforeCalls++
            return if (ability == "force") true else null
        }

        fun viewAny(user: Authenticatable?): Boolean = user != null

        fun create(user: Authenticatable?): Boolean =
            user?.getAuthIdentifier() == 1

        fun view(user: Authenticatable?, model: Currency): Boolean =
            user != null && model.code == "USD"

        fun update(user: Authenticatable?, model: Currency): Boolean =
            user?.getAuthIdentifier() == 1 && model.code == "USD"

        fun createWithClass(user: Authenticatable?, modelClass: Class<*>): Boolean =
            user != null && modelClass == Currency::class.java

        override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean =
            ability == "legacy" && model is Currency

        override fun authorizeClass(user: Authenticatable?, ability: String, modelClass: Class<*>): Boolean {
            authorizeClassCalls++
            return ability == "deleteAny" && user != null && modelClass == Currency::class.java
        }
    }

    class AuthorizableUser(
        private val id: Any,
        private val password: String,
        private val gate: Gate,
    ) : Authenticatable, Authorizable {
        override fun getAuthIdentifier(): Any = id
        override fun getAuthPassword(): String = password
        override fun authorizableGate(): Gate = gate
    }

    @Test
    fun resolvesPolicyForClassArgumentsAndMethodDispatch() {
        val user = SimpleUser(1, "h")
        val guest = SimpleUser(2, "h")
        val policy = CurrencyPolicy()
        val gate = Gate().policy(Currency::class.java, policy).setUserResolver { user }

        assertNotNull(gate.getPolicyFor(Currency::class.java))
        assertNotNull(gate.getPolicyFor(Currency("USD")))
        assertTrue(gate.allows("viewAny", Currency::class.java))
        assertTrue(gate.allows("create", Currency::class.java))
        assertFalse(gate.allows(guest, "create", Currency::class.java))
        assertTrue(gate.allows("view", Currency("USD")))
        assertFalse(gate.allows("view", Currency("EUR")))
        assertTrue(gate.allows("update", Currency("USD")))
        assertTrue(gate.allows("view-any", Currency::class.java))
        assertTrue(gate.allows("createWithClass", Currency::class.java))
        assertTrue(gate.allows("deleteAny", Currency::class.java))
        assertEquals(1, policy.authorizeClassCalls)
        assertTrue(gate.allows("force", Currency::class.java))
        assertTrue(policy.beforeCalls >= 1)
        assertTrue(gate.allows("legacy", Currency("USD")))
        assertFalse(gate.allows("missingAbility", Currency::class.java))
        gate.authorize("create", Currency::class.java)
        assertThrows(AuthorizationException::class.java) {
            gate.authorize(guest, "create", Currency::class.java)
        }
    }

    @Test
    fun policyBeforeRunsForClassLevelChecks() {
        val gate = Gate().policy(
            Currency::class.java,
            object : Policy {
                override fun before(user: Authenticatable?, ability: String): Boolean? =
                    if (ability == "create") false else null

                override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = true
            },
        )
        assertFalse(gate.allows(SimpleUser(1, "x"), "create", Currency::class.java))
    }

    @Test
    fun authorizableCanCannotCanAny() {
        val gate = Gate()
            .define("ping") { u, _ -> u != null }
            .policy(
                Currency::class.java,
                object : Policy {
                    fun create(user: Authenticatable?): Boolean = user?.getAuthIdentifier() == 7
                    override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
                },
            )
        Gate.setDefault(gate)
        try {
            assertTrue(Gate.hasDefault())
            val user = AuthorizableUser(7, "p", gate)
            assertTrue(user.can("ping"))
            assertFalse(user.cannot("ping"))
            assertTrue(user.can("create", Currency::class.java))
            assertTrue(user.canAny(listOf("missing", "ping")))
            assertFalse(user.canAny(listOf("missing", "nope")))
            assertFalse(user.cannot("create", Currency::class.java))

            val defaultUser = object : Authenticatable, Authorizable {
                override fun getAuthIdentifier(): Any = 7
                override fun getAuthPassword(): String = "p"
            }
            assertTrue(defaultUser.can("ping"))
            assertTrue(Gate.getDefault().allows(defaultUser, "ping"))
        } finally {
            Gate.clearDefault()
        }
        assertFalse(Gate.hasDefault())
        assertThrows(IllegalStateException::class.java) { Gate.getDefault() }
    }

    @Test
    fun authorizeMiddlewareAcceptsModelClass() {
        val gate = Gate()
            .setUserResolver { SimpleUser(1, "h") }
            .policy(
                Currency::class.java,
                object : Policy {
                    fun create(user: Authenticatable?): Boolean = true
                    override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
                },
            )
        val request = Request.get("/currencies")
        val ok = Authorize(gate, "create", Currency::class.java)
            .handle(request) { Response.text("ok") }
        assertEquals("ok", ok.body)
        assertThrows(AuthorizationException::class.java) {
            Authorize(gate, "update", Currency::class.java)
                .handle(request) { Response.text("no") }
        }
    }

    @Test
    fun abilityMethodNameAndEmptyMethodArgs() {
        assertEquals("viewAny", Gate.abilityMethodName("view-any"))
        assertEquals("viewAny", Gate.abilityMethodName("view_any"))
        assertEquals("create", Gate.abilityMethodName("create"))

        val gate = Gate().policy(
            Currency::class.java,
            object : Policy {
                fun ping(): Boolean = true
                override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
            },
        )
        assertTrue(gate.allows(SimpleUser(1, "x"), "ping", Currency::class.java))

        val reserved = Gate().policy(
            Currency::class.java,
            object : Policy {
                override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean =
                    ability == "authorize" && model is Class<*>
            },
        )
        assertTrue(reserved.allows(SimpleUser(1, "x"), "authorize", Currency::class.java))
        assertNull(Gate.findAbilityMethod(reserved.getPolicyFor(Currency::class.java)!!, "authorize"))
    }

    @Test
    fun padsNullArgumentsForExtraParameters() {
        val gate = Gate().policy(
            Currency::class.java,
            object : Policy {
                fun create(user: Authenticatable?, unused: String?): Boolean =
                    user != null && unused == null

                override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
            },
        )
        assertTrue(gate.allows(SimpleUser(1, "x"), "create", Currency::class.java))
    }

    @Test
    fun authorizeClassFallsBackToAuthorizeAndTruncatesArgs() {
        val gate = Gate().policy(
            Currency::class.java,
            object : Policy {
                fun view(user: Authenticatable?, model: Currency): Boolean =
                    model.code == "USD"

                override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean =
                    ability == "export" && model == Currency::class.java
            },
        )
        assertTrue(gate.allows(SimpleUser(1, "x"), "export", Currency::class.java))
        assertTrue(gate.allows(SimpleUser(1, "x"), "view", Currency("USD"), "ignored-extra"))
    }

    @Test
    fun policyMethodFailureWrapsReflectiveException() {
        val gate = Gate().policy(
            Currency::class.java,
            object : Policy {
                fun create(user: Authenticatable?): Boolean =
                    throw IllegalArgumentException("boom")

                override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
            },
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            gate.allows(SimpleUser(1, "x"), "create", Currency::class.java)
        }
        assertTrue(ex.message!!.contains("create"))
    }

    @Test
    fun authClearManagerClearsDefaultGate() {
        Gate.setDefault(Gate())
        assertTrue(Gate.hasDefault())
        Auth.clearManager()
        assertFalse(Gate.hasDefault())
    }

    @Test
    fun skipsNonBooleanAbilityMethodsAndKeepsObjectClassArg() {
        val mixed = object : Policy {
            @Suppress("unused")
            fun create(user: Authenticatable?): String = "nope"

            fun export(user: Authenticatable?, model: Any): Boolean =
                model == Currency::class.java

            override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean =
                ability == "create"
        }
        val gate = Gate().policy(Currency::class.java, mixed)
        assertTrue(gate.allows(SimpleUser(1, "x"), "create", Currency::class.java))
        assertTrue(gate.allows(SimpleUser(1, "x"), "export", Currency::class.java))
        assertEquals("viewAny", Gate.abilityMethodName("view--any"))
    }

    @Test
    fun invokePolicyNullArgumentAndEmptyBuildArgs() {
        val policy = object : Policy {
            fun create(user: Authenticatable?): Boolean = user != null
            override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = true
        }
        val gate = Gate()
        assertFalse(gate.invokePolicy(policy, SimpleUser(1, "x"), "legacy", arrayOf(null)))

        val method = Gate.findAbilityMethod(policy, "create")!!
        val args = Gate.buildMethodArguments(method, SimpleUser(1, "x"), emptyArray())
        assertEquals(1, args.size)
        assertTrue(Gate.invokeAbilityMethod(policy, method, SimpleUser(1, "x"), emptyArray()))
    }
}
