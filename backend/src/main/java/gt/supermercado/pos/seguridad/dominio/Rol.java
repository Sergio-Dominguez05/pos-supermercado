package gt.supermercado.pos.seguridad.dominio;

/** Roles fijos del sistema (tabla {@code rol}). El código coincide con {@code rol.codigo}. */
public enum Rol {
    ADMIN(1),
    SUPERVISOR(2),
    CAJERO(3),
    /**
     * HU-08.08: el usuario técnico "sistema", a cuyo nombre quedan en la bitácora las acciones de los procesos
     * automáticos. No tiene permisos, no inicia sesión y no aparece en la administración de usuarios.
     */
    SISTEMA(4);

    private final int id;

    Rol(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
