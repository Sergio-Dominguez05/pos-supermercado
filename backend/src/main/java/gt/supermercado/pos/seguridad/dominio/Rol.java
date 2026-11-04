package gt.supermercado.pos.seguridad.dominio;

/** Roles fijos del sistema (tabla {@code rol}). El código coincide con {@code rol.codigo}. */
public enum Rol {
    ADMIN(1),
    SUPERVISOR(2),
    CAJERO(3);

    private final int id;

    Rol(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** Administrador y Supervisor entran con contraseña (HU-01.01); el Cajero, con PIN (HU-01.02). */
    public boolean usaContrasena() {
        return this != CAJERO;
    }
}
