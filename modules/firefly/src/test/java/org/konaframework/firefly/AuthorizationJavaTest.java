package org.konaframework.firefly;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.konaframework.auth.Authenticatable;
import org.konaframework.auth.authorization.Gate;
import org.konaframework.http.Request;
import org.konaframework.http.Router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationJavaTest {
    @BeforeEach
    void setUp() {
        AuthorizationFixturesKt.azDatabase();
        Firefly.reset();
        Firefly.gate(new Gate().policy(AzPost.class, new AzPostPolicy()));
    }

    @AfterEach
    void tearDown() {
        Firefly.reset();
    }

    /** A resource answers from the model policy unless a hook is overridden. */
    static class Posts extends Resource {
        Posts() {
            super("posts");
            model(AzPost.class);
        }

        @Override
        public boolean canDelete(Object record) {
            return false;
        }
    }

    @Test
    void javaResourcesAskThePolicyAndMayOverrideAHook() {
        Posts posts = new Posts();
        Authenticatable admin = AuthorizationFixturesKt.azUser("admin");
        Authenticatable viewer = AuthorizationFixturesKt.azUser("viewer");

        assertTrue(FireflyAuth.actingAs(admin, posts::canCreate));
        assertFalse(FireflyAuth.actingAs(viewer, posts::canCreate));
        assertTrue(FireflyAuth.actingAs(viewer, posts::canViewAny));
        assertFalse(FireflyAuth.actingAs(admin, () -> posts.canDelete(new AzPost())), "the override wins");
        assertTrue(FireflyAuth.actingAs(admin, () -> posts.can("update", new AzPost())));
        assertTrue(FireflyAuth.actingAs(viewer, () -> posts.can("viewAny")), "the record is optional");
        assertEquals(null, FireflyAuth.user());
    }

    @Test
    void javaPanelsDecideWhoMayEnter() {
        AzGuard guard = new AzGuard();
        Router router = new Router();
        new Panel().id("desk").path("admin").guard(guard)
            .canAccess(user -> user != null && !"viewer".equals(AuthorizationFixturesKt.role(user)))
            .resource(new Posts())
            .mount(router);

        var match = router.find("GET", "/admin/posts/list");
        guard.signIn("viewer");
        assertEquals(403, match.getRoute().getAction().handle(Request.get("/admin/posts/list")).getStatus());
        guard.signIn("editor");
        assertEquals(200, match.getRoute().getAction().handle(Request.get("/admin/posts/list")).getStatus());
    }

    @Test
    void javaUsersCanDecideForThemselves() {
        FireflyUser staff = new AzStaff(false);
        Panel panel = new Panel().id("desk");
        assertFalse(staff.canAccessPanel(panel));
        assertTrue(new AzStaff(true).canAccessPanel(panel));
        assertTrue(panel.permits((Authenticatable) new AzStaff(true)));
        assertFalse(panel.permits((Authenticatable) staff));
    }
}
