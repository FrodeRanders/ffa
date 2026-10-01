package se.fk.data.modell.v1;

import se.fk.data.modell.internal.LifecycleState;

/** FFA-objekt med stabil identitet och en version som förvaltas av infrastrukturen. */
public abstract class Livscykelhanterad extends LifecycleState {
    protected Livscykelhanterad() {}

    @Override
    public String toString() {
        return "id='" + getId() + "', version=" + getVersion();
    }
}
