package io.konaframework.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import io.konaframework.core.fixtures.PackagePrivate
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ContainerTest {
    class Greeter(val name: String)
    class Defaultable {
        var value = "ok"
    }

    class Engine
    class Car(val engine: Engine)

    class Picky @Inject constructor() {
        var engine: Engine? = null

        constructor(engine: Engine) : this() {
            this.engine = engine
        }
    }

    class Greedy(val engine: Engine?, val car: Car?) {
        constructor() : this(null, null)
        constructor(engine: Engine) : this(engine, null)
    }

    class TwoInject @Inject constructor() {
        @Inject
        constructor(@Suppress("UNUSED_PARAMETER") engine: Engine) : this()
    }

    class WithDefault(val engine: Engine = Engine())
    class Counter(val n: Int)
    class Hidden private constructor()
    class Exploding {
        init {
            throw IllegalStateException("boom")
        }
    }
    class NeedsContainer(val container: Container)

    interface Service
    abstract class AbstractService : Service
    class ServiceImpl : Service
    class UsesService(val service: Service)

    class CycleA(val b: CycleB)
    class CycleB(val a: CycleA)

    @Test
    fun bindResolveAndSingleton() {
        val c = Container()
        c.bind("greeter", { Greeter("a") })
        val a = c.resolve<Greeter>("greeter")
        val b = c.resolve<Greeter>("greeter")
        assertEquals("a", a.name)
        assertTrue(a !== b)

        c.singleton(Greeter::class.java) { Greeter("shared") }
        val s1 = c.resolve(Greeter::class.java)
        val s2 = c.resolve(Greeter::class.java)
        assertTrue(s1 === s2)

        c.bind(Greeter::class.java, { Greeter("typed") }, shared = false)
        assertEquals("typed", c.resolve(Greeter::class.java).name)
        c.bind(Greeter::class.java, { Greeter("defaultSharedFlag") })
        assertEquals("defaultSharedFlag", c.resolve(Greeter::class.java).name)
        c.bind("other", { Greeter("s") }, shared = true)
        assertTrue(c.resolve<Greeter>("other") === c.resolve<Greeter>("other"))
        c.singleton("named") { Greeter("n") }
        assertSame(c.make<Greeter>("named"), c.make<Greeter>("named"))
    }

    @Test
    fun instanceAliasMakeAndFlush() {
        val c = Container()
        val g = Greeter("x")
        c.instance(Greeter::class.java, g)
        c.alias("g", Greeter::class.java.name)
        c.alias("gg", "g")
        assertTrue(c.bound(Greeter::class.java))
        assertTrue(c.bound("g"))
        assertEquals(g, c.resolve<Greeter>("g"))
        assertEquals(g, c.resolve<Greeter>("gg"))
        assertEquals(Greeter::class.java.name, c.getAlias("gg"))
        assertEquals("ok", c.make(Defaultable::class.java).value)
        assertThrows(BindingException::class.java) { c.alias("self", "self") }
        assertThrows(BindingException::class.java) { c.alias(Greeter::class.java.name, "gg") }
        c.flush()
        assertFalse(c.bound(Greeter::class.java))
        assertFalse(c.bound("g"))
        assertTrue(c.bound(Container::class.java))
        assertSame(c, c.make(Container::class.java))
        assertThrows(BindingException::class.java) { c.resolve<Greeter>(Greeter::class.java.name) }
    }

    @Test
    fun autowiresConstructors() {
        val c = Container()
        val car = c.make(Car::class.java)
        assertEquals(Engine::class.java, car.engine.javaClass)
        assertEquals(c.resolve(Car::class.java).javaClass, Car::class.java)

        assertNull(c.make(Picky::class.java).engine)

        val greedy = c.make(Greedy::class.java)
        assertTrue(greedy.engine != null && greedy.car != null)

        assertEquals(Engine::class.java, c.make(WithDefault::class.java).engine.javaClass)
        assertSame(c, c.make(NeedsContainer::class.java).container)

        val engine = Engine()
        c.instance(Engine::class.java, engine)
        assertSame(engine, c.make(Car::class.java).engine)
    }

    @Test
    fun autowireFailures() {
        val c = Container()
        assertTrue(
            assertThrows(BindingException::class.java) { c.make(TwoInject::class.java) }
                .message!!.contains("multiple @Inject"),
        )
        assertTrue(
            assertThrows(BindingException::class.java) { c.make(Service::class.java) }
                .message!!.contains("not instantiable"),
        )
        assertThrows(BindingException::class.java) { c.make(AbstractService::class.java) }
        assertThrows(BindingException::class.java) { c.make(Hidden::class.java) }
        assertTrue(
            assertThrows(BindingException::class.java) { c.make(Greeter::class.java) }
                .message!!.contains("Unresolvable dependency [java.lang.String]"),
        )
        assertTrue(
            assertThrows(BindingException::class.java) { c.make(Counter::class.java) }
                .message!!.contains("Unresolvable dependency [int]"),
        )
        val exploding = assertThrows(BindingException::class.java) { c.make(Exploding::class.java) }
        assertEquals("boom", exploding.cause!!.message)
        val secret = assertThrows(BindingException::class.java) { c.make(PackagePrivate.hiddenType()) }
        assertTrue(secret.message!!.contains("not accessible"))

        c.instance(String::class.java, "bound-string")
        assertEquals("bound-string", c.make(Greeter::class.java).name)
    }

    @Test
    fun bindsInterfacesToImplementations() {
        val c = Container()
        c.bind(Service::class.java, ServiceImpl::class.java)
        assertTrue(c.make(UsesService::class.java).service is ServiceImpl)
        assertTrue(c.make(Service::class.java) !== c.make(Service::class.java))

        c.singleton(Service::class.java, ServiceImpl::class.java)
        assertSame(c.make(Service::class.java), c.make(Service::class.java))

        c.bind(Service::class.java, ServiceImpl::class.java, shared = true)
        assertSame(c.make(Service::class.java), c.make(UsesService::class.java).service)
    }

    @Test
    fun detectsCircularDependencies() {
        val c = Container()
        val autowired = assertThrows(CircularDependencyException::class.java) { c.make(CycleA::class.java) }
        assertTrue(autowired.message!!.contains("${CycleA::class.java.name} -> ${CycleB::class.java.name} -> ${CycleA::class.java.name}"))
        assertTrue(autowired is BindingException)

        c.bind("x", { it.resolve<Any>("y") })
        c.singleton("y") { it.resolve<Any>("x") }
        val bound = assertThrows(CircularDependencyException::class.java) { c.resolve<Any>("x") }
        assertTrue(bound.message!!.endsWith("x -> y -> x"))

        // Resolution stack is cleaned up after failures.
        assertEquals(Engine::class.java, c.make(Car::class.java).engine.javaClass)
    }

    @Test
    fun threadSafeSingletonsAndBindings() {
        val c = Container()
        val builds = AtomicInteger()
        c.singleton(Engine::class.java) {
            builds.incrementAndGet()
            Thread.sleep(200)
            Engine()
        }
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<Engine>())
        repeat(threads) { i ->
            pool.submit {
                start.await()
                c.bind("key$i", { i })
                results.add(c.make(Engine::class.java))
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(threads, results.size)
        assertEquals(1, builds.get())
        assertTrue(results.all { it === results[0] })
        repeat(threads) { assertEquals(it, c.resolve<Int>("key$it")) }
    }
}
