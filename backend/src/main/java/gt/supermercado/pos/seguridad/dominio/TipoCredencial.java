package gt.supermercado.pos.seguridad.dominio;

/**
 * Cómo se identifica cada rol: Administrador y Supervisor con contraseña (HU-01.01), Cajero con PIN (HU-01.02).
 * Ambas se guardan con el mismo hash seguro (Argon2id).
 */
public enum TipoCredencial {
    CONTRASENA("contraseña"),
    PIN("PIN");

    private final String nombre;

    TipoCredencial(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre para mostrar al usuario y en la bitácora. */
    public String nombre() {
        return nombre;
    }

    public static TipoCredencial de(Rol rol) {
        return rol == Rol.CAJERO ? PIN : CONTRASENA;
    }
}
