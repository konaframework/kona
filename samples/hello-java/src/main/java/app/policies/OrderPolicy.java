package app.policies;

import app.models.Order;
import org.konaframework.auth.Authenticatable;
import org.konaframework.auth.authorization.Policy;

/**
 * Found by Firefly through the {@code app.policies.{Model}Policy} name, so nothing registers it.
 * Orders can be listed, created and edited, but never deleted from the desk.
 */
public class OrderPolicy implements Policy {
    public boolean viewAny(Authenticatable user) {
        return true;
    }

    public boolean view(Authenticatable user, Order order) {
        return true;
    }

    public boolean create(Authenticatable user) {
        return true;
    }

    public boolean update(Authenticatable user, Order order) {
        return true;
    }

    public boolean delete(Authenticatable user, Order order) {
        return false;
    }

    public boolean deleteAny(Authenticatable user) {
        return false;
    }

    @Override
    public boolean authorize(Authenticatable user, String ability, Object model) {
        return false;
    }
}
