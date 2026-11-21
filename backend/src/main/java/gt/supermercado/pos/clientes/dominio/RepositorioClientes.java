package gt.supermercado.pos.clientes.dominio;

import java.util.Optional;

/** Los clientes registrados (EP-05). */
public interface RepositorioClientes {

    PaginaClientes listar(FiltroClientes filtro);

    /** {@code nit} ya normalizado. */
    Optional<Cliente> porNit(String nit);

    Optional<Cliente> porId(long id);

    /** HU-05.06: {@code cui} ya normalizado (13 dígitos). */
    Optional<Cliente> porCui(String cui);

    /** El cliente asignado a la venta, o vacío si es a Consumidor Final. */
    Optional<Cliente> deLaVenta(long ventaId);

    /**
     * {@code tipo} es {@link Cliente#NIT} o {@link Cliente#CUI}. Lanza {@code DuplicateKeyException} si la
     * identificación ya está registrada.
     */
    long registrar(String tipo, String identificacion, String nombre, String direccion, String correo, long usuarioId);

    /** HU-05.11 crit. 1: el correo del cliente, agregado o cambiado durante la venta. */
    void cambiarCorreo(long clienteId, String correo);

    /** HU-05.09: el nombre, la dirección y el correo; la identificación (NIT o CUI) no cambia nunca (crit. 2). */
    void editar(long clienteId, String nombre, String direccion, String correo);
}
