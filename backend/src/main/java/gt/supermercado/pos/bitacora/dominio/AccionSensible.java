package gt.supermercado.pos.bitacora.dominio;

/**
 * Catálogo de las acciones sensibles que se registran en la bitácora (HU-11.01 crit. 1). Cada caso de uso registra
 * su acción con {@link EventoBitacora}, en el momento en que ocurre (crit. 2), tanto si sale bien como si falla
 * (crit. 3: el intento fallido va con {@code Bitacora.registrarIntentoFallido}).
 *
 * <p>Las acciones de módulos que aún no existen ya están aquí para que su historia solo tenga que registrarlas;
 * la historia que implementa cada una se indica en su comentario. El código de la acción es lo que se guarda en
 * {@code bitacora.accion} y por lo que se filtrará la consulta (HU-11.06): no se debe renombrar.
 */
public enum AccionSensible {

    // Sesión
    INICIO_SESION(Categoria.SESION, "seguridad"),
    CIERRE_SESION(Categoria.SESION, "seguridad"),
    /** Petición rechazada por falta de permiso (HU-01.11). */
    ACCESO_DENEGADO(Categoria.SESION, "seguridad"),

    // Usuarios y permisos. El cambio de rol se registra como USUARIO_EDITADO, con el rol anterior y el nuevo.
    USUARIO_CREADO(Categoria.USUARIOS_Y_PERMISOS, "seguridad"),
    USUARIO_EDITADO(Categoria.USUARIOS_Y_PERMISOS, "seguridad"),
    USUARIO_ACTIVADO(Categoria.USUARIOS_Y_PERMISOS, "seguridad"),
    USUARIO_DESACTIVADO(Categoria.USUARIOS_Y_PERMISOS, "seguridad"),
    /** Un administrador restablece la contraseña o el PIN de otro usuario (HU-01.14). */
    CREDENCIAL_RESTABLECIDA(Categoria.USUARIOS_Y_PERMISOS, "seguridad"),
    /** El usuario cambia su propia contraseña o PIN (HU-01.13). */
    CREDENCIAL_CAMBIADA(Categoria.USUARIOS_Y_PERMISOS, "seguridad"),
    /** Pendiente: HU-01.10. */
    PERMISOS_MODIFICADOS(Categoria.USUARIOS_Y_PERMISOS, "seguridad"),

    // Ventas
    /** Pendiente: HU-06.07. */
    LINEA_VENTA_ELIMINADA(Categoria.VENTAS, "ventas"),
    /** Pendiente: HU-06.14. */
    VENTA_CANCELADA(Categoria.VENTAS, "ventas"),

    // Facturación
    /** Pendiente: HU-08.10. */
    FACTURA_ANULADA(Categoria.FACTURACION, "facturacion"),
    /** Pendiente: HU-08.12. */
    FACTURA_REIMPRESA(Categoria.FACTURACION, "facturacion"),
    /** Pendiente: HU-08.06. */
    FACTURA_RECHAZADA_CERTIFICADOR(Categoria.FACTURACION, "facturacion"),

    // Pagos
    /** Pago con tarjeta eliminado antes de finalizar la venta. Pendiente: HU-07.06. */
    PAGO_TARJETA_ELIMINADO(Categoria.PAGOS, "pagos"),

    // Caja
    /** Pendiente: HU-09.01. */
    TURNO_ABIERTO(Categoria.CAJA, "caja"),
    /** Pendiente: HU-09.08. */
    TURNO_CERRADO(Categoria.CAJA, "caja"),
    /** Un supervisor cierra la caja de otro usuario. Pendiente: HU-09.12. */
    TURNO_CERRADO_POR_SUPERVISOR(Categoria.CAJA, "caja"),

    // Configuración
    CAJA_CREADA(Categoria.CONFIGURACION, "configuracion"),
    CAJA_EDITADA(Categoria.CONFIGURACION, "configuracion"),
    CAJA_ACTIVADA(Categoria.CONFIGURACION, "configuracion"),
    CAJA_DESACTIVADA(Categoria.CONFIGURACION, "configuracion"),
    EQUIPO_VINCULADO(Categoria.CONFIGURACION, "configuracion"),
    /** Cualquier parámetro del sistema. Pendiente: HU-12.01, 12.02, 12.03, 12.07, 12.08 y 12.13. */
    PARAMETRO_MODIFICADO(Categoria.CONFIGURACION, "configuracion"),

    // Catálogo
    /** Pendiente: HU-03.01. */
    PRODUCTO_CREADO(Categoria.CATALOGO, "catalogo"),
    /** Cambio de descripción. Pendiente: HU-03.02. */
    PRODUCTO_EDITADO(Categoria.CATALOGO, "catalogo"),
    /** Base del historial de precios (HU-03.11). Pendiente: HU-03.02. */
    PRECIO_CAMBIADO(Categoria.CATALOGO, "catalogo"),
    /** Pendiente: HU-03.03. */
    PRODUCTO_ACTIVADO(Categoria.CATALOGO, "catalogo"),
    /** Pendiente: HU-03.03. */
    PRODUCTO_DESACTIVADO(Categoria.CATALOGO, "catalogo"),
    /** Carga del catálogo desde un archivo. Pendiente: HU-03.05. */
    CATALOGO_CARGADO(Categoria.CATALOGO, "catalogo");

    /** Los grupos de eventos del criterio 1 de HU-11.01. */
    public enum Categoria { SESION, USUARIOS_Y_PERMISOS, VENTAS, FACTURACION, PAGOS, CAJA, CONFIGURACION, CATALOGO }

    private final Categoria categoria;
    private final String modulo;

    AccionSensible(Categoria categoria, String modulo) {
        this.categoria = categoria;
        this.modulo = modulo;
    }

    public Categoria categoria() {
        return categoria;
    }

    /** Módulo del backend donde ocurre; se guarda en {@code bitacora.modulo}. */
    public String modulo() {
        return modulo;
    }

    /** Lo que se guarda en {@code bitacora.accion}. */
    public String codigo() {
        return name();
    }
}
