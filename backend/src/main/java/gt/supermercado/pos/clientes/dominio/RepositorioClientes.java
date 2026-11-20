package gt.supermercado.pos.clientes.dominio;

import java.util.Optional;

/** Los clientes registrados (EP-05). */
public interface RepositorioClientes {

    PaginaClientes listar(FiltroClientes filtro);

    /** {@code nit} ya normalizado. */
    Optional<Cliente> porNit(String nit);

    Optional<Cliente> porId(long id);

    /** El cliente asignado a la venta, o vacío si es a Consumidor Final. */
    Optional<Cliente> deLaVenta(long ventaId);

    /** Lanza {@code DuplicateKeyException} si el NIT ya está registrado. */
    long registrar(String nit, String nombre, String direccion, String correo, long usuarioId);

    /** HU-05.11 crit. 1: el correo del cliente, agregado o cambiado durante la venta. */
    void cambiarCorreo(long clienteId, String correo);
}
