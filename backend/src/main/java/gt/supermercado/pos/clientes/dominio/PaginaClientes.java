package gt.supermercado.pos.clientes.dominio;

import java.time.OffsetDateTime;
import java.util.List;

/** Una página de clientes registrados (HU-05.10). */
public record PaginaClientes(List<ClienteListado> clientes, int pagina, int tamano, long total) {

    public PaginaClientes {
        clientes = List.copyOf(clientes);
    }

    public record ClienteListado(long id, String tipoIdentificacion, String identificacion, String nombre,
                                 String direccion, String correo, OffsetDateTime ultimaCompra) {}
}
