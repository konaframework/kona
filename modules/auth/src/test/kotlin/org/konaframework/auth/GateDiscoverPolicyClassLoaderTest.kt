package org.konaframework.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.auth.authorization.Gate
import org.konaframework.auth.authorization.Policy
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider

/**
 * Reproduces konaframework/kona#489: under grind SoftReload, models load on a child-first
 * classloader while [Gate.discoverPolicy] previously used [Class.forName] without a loader,
 * so policies stayed on the parent and `user as? User` / `instanceof` failed.
 */
class GateDiscoverPolicyClassLoaderTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun discoverPolicyLoadsPolicyWithModelClassLoader() {
        val parentDir = temp.resolve("parent")
        val childDir = temp.resolve("child")
        Files.createDirectories(parentDir)
        Files.createDirectories(childDir)
        compileFixtures(parentDir)
        // Identical bytecode on the child so SoftReload-style prefer-child wins.
        compileFixtures(childDir)

        // Framework types stay on the test ClassLoader (like SoftReload keeps org.konaframework.*
        // on the grind parent). Only app fixtures are duplicated across parent/child.
        val framework = Gate::class.java.classLoader
        URLClassLoader(arrayOf(parentDir.toUri().toURL()), framework).use { parent ->
            val parentModel = parent.loadClass("app.models.DemoUser")
            val parentPolicy = parent.loadClass("app.policies.DemoUserPolicy")
            assertSame(parent, parentModel.classLoader)

            AppChildFirstLoader(arrayOf(childDir.toUri().toURL()), parent).use { child ->
                val childModel = child.loadClass("app.models.DemoUser")
                assertNotSame(parentModel, childModel)
                assertSame(child, childModel.classLoader)

                val gate = Gate()
                assertTrue(gate.discoverPolicy(childModel, "app.policies"))
                val policy = gate.getPolicyFor(childModel)!!
                assertSame(child, policy.javaClass.classLoader)
                assertNotSame(parentPolicy, policy.javaClass)

                val childUser = childModel.getDeclaredConstructor().newInstance() as Authenticatable
                gate.setUserResolver { childUser }
                assertTrue(gate.allows("view", childModel.getDeclaredConstructor().newInstance()))

                // Parent-loaded policy rejects the child user (classloader split).
                @Suppress("UNCHECKED_CAST")
                val stale = parentPolicy.getDeclaredConstructor().newInstance() as Policy
                assertFalse(stale.authorize(childUser, "view", childModel))
            }
        }
    }

    @Test
    fun discoverPolicyStillFindsSameLoaderPolicies() {
        val gate = Gate()
        assertTrue(gate.discoverPolicy(SampleModel::class.java, "org.konaframework.auth"))
        assertEquals(
            SampleModel::class.java.classLoader,
            gate.getPolicyFor(SampleModel::class.java)!!.javaClass.classLoader,
        )
    }

    @Test
    fun discoverPolicyFallsBackWhenModelClassLoaderIsNull() {
        // Bootstrap types report a null ClassLoader; discovery must use the context loader.
        assertEquals(null, String::class.java.classLoader)
        assertFalse(Gate().discoverPolicy(String::class.java, "org.konaframework.auth.missing"))
    }

    private fun compileFixtures(output: Path) {
        val srcRoot = temp.resolve("src-${output.fileName}")
        val models = srcRoot.resolve("app/models")
        val policies = srcRoot.resolve("app/policies")
        Files.createDirectories(models)
        Files.createDirectories(policies)
        Files.writeString(
            models.resolve("DemoUser.java"),
            """
            package app.models;
            import org.konaframework.auth.Authenticatable;
            public class DemoUser implements Authenticatable {
              public Object getAuthIdentifier() { return 1; }
              public String getAuthPassword() { return "x"; }
            }
            """.trimIndent(),
        )
        Files.writeString(
            policies.resolve("DemoUserPolicy.java"),
            """
            package app.policies;
            import org.konaframework.auth.Authenticatable;
            import org.konaframework.auth.authorization.Policy;
            import app.models.DemoUser;
            public class DemoUserPolicy implements Policy {
              public boolean authorize(Authenticatable user, String ability, Object model) {
                return user instanceof DemoUser;
              }
            }
            """.trimIndent(),
        )
        val javac = ToolProvider.getSystemJavaCompiler()
        assertTrue(javac != null, "JDK compiler required")
        val authCp = Authenticatable::class.java.protectionDomain.codeSource!!.location.path
        val ok = javac!!.run(
            null,
            null,
            null,
            "-classpath",
            authCp,
            "-d",
            output.toString(),
            models.resolve("DemoUser.java").toString(),
            policies.resolve("DemoUserPolicy.java").toString(),
        )
        assertEquals(0, ok, "javac failed for SoftReload policy fixtures")
    }

    /**
     * Minimal child-first loader for `app.*` types (mirrors SoftReloadClassLoader prefer-child).
     */
    private class AppChildFirstLoader(
        urls: Array<java.net.URL>,
        parent: ClassLoader,
    ) : URLClassLoader(urls, parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("app.")) {
                return super.loadClass(name, resolve)
            }
            synchronized(getClassLoadingLock(name)) {
                var loaded = findLoadedClass(name)
                if (loaded == null) {
                    loaded = try {
                        findClass(name)
                    } catch (_: ClassNotFoundException) {
                        null
                    }
                }
                if (loaded == null) {
                    return super.loadClass(name, resolve)
                }
                if (resolve) {
                    resolveClass(loaded)
                }
                return loaded
            }
        }
    }
}
