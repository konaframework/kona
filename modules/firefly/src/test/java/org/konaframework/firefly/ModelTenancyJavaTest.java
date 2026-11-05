package org.konaframework.firefly;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.konaframework.auth.Authenticatable;
import org.konaframework.http.Request;
import org.konaframework.http.Router;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Java surface for model-backed Firefly tenancy ([Panel.tenant] with a model class). */
class ModelTenancyJavaTest {
    private MtGuard guard;

    @BeforeEach
    void setUp() {
        ModelTenancyTestKt.mtDatabase();
        MtCompany acme = new MtCompany();
        acme.fill(java.util.Map.of("name", "Acme", "slug", "acme"));
        acme.save();
        MtCompany beta = new MtCompany();
        beta.fill(java.util.Map.of("name", "Beta", "slug", "beta"));
        beta.save();
        MtOrder order = new MtOrder();
        java.util.Map<String, Object> attrs = new java.util.LinkedHashMap<>();
        attrs.put("name", "Acme order");
        attrs.put("company_id", acme.getKey());
        order.fill(attrs);
        order.save();
        guard = new MtGuard(List.of(acme, beta));
        Firefly.reset();
    }

    @AfterEach
    void tearDown() {
        Firefly.reset();
    }

    static class Orders extends Resource {
        Orders() {
            super("orders");
            model(MtOrder.class);
        }

        @Override
        public org.konaframework.firefly.schemas.Schema form(org.konaframework.firefly.schemas.Schema schema) {
            return schema.components(org.konaframework.firefly.forms.TextInput.make("name").required());
        }

        @Override
        public org.konaframework.firefly.tables.Table<?, ?> table(org.konaframework.firefly.tables.Table<?, ?> table) {
            return table.columns(org.konaframework.firefly.tables.TextColumn.make("name"));
        }
    }

    @Test
    void javaPanelsMountModelTenantsAndEnforceMembership() {
        MtCompany acme = new MtCompany().newQuery().where("slug", "acme").first();
        MtCompany beta = new MtCompany().newQuery().where("slug", "beta").first();
        Router router = new Router();
        new Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany.class, "company", "slug")
            .resource(new Orders())
            .mount(router);

        guard.signIn(List.of(acme), acme);
        var denied = router.find("GET", "/admin/beta/orders/list").getRoute().getAction()
            .handle(Request.get("/admin/beta/orders/list"));
        assertEquals(403, denied.getStatus());

        guard.signIn(List.of(acme, beta), acme);
        var ok = router.find("GET", "/admin/acme/orders/list").getRoute().getAction()
            .handle(Request.get("/admin/acme/orders/list"));
        assertEquals(200, ok.getStatus());
        assertTrue(ok.getBody().contains("Acme order"));
        assertFalse(ok.getBody().contains("Beta order"));
        assertTrue(ok.getBody().contains("ff-tenant-menu"));
    }

    @Test
    void javaUsersImplementHasTenants() {
        MtCompany acme = new MtCompany().newQuery().where("slug", "acme").first();
        HasTenants user = new MtUser(new java.util.ArrayList<>(List.of(acme)), acme);
        Panel panel = new Panel().id("desk");
        assertEquals(1, user.getTenants(panel).size());
        assertTrue(user.canAccessTenant(acme));
        assertEquals(acme, ((HasDefaultTenant) user).getDefaultTenant(panel));
        Authenticatable auth = (Authenticatable) user;
        assertEquals("user", auth.getAuthIdentifier());
        Collection<?> tenants = user.getTenants(panel);
        assertFalse(tenants.isEmpty());
    }
}
