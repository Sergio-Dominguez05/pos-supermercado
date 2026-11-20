package gt.supermercado.pos.clientes.api;

import gt.supermercado.pos.clientes.aplicacion.GestionClientes;
import gt.supermercado.pos.clientes.aplicacion.GestionClientes.ClienteYCorreo;
import gt.supermercado.pos.clientes.aplicacion.GestionClientes.BusquedaNit;
import gt.supermercado.pos.clientes.aplicacion.GestionClientes.SolicitudCliente;
import gt.supermercado.pos.clientes.dominio.Cliente;
import gt.supermercado.pos.clientes.dominio.FiltroClientes;
import gt.supermercado.pos.clientes.dominio.PaginaClientes;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/** EP-05: buscar, registrar y asignar el cliente de la venta en curso. */
@RestController
public class ClientesController {

    private final GestionClientes gestion;

    public ClientesController(GestionClientes gestion) {
        this.gestion = gestion;
    }

    /** El cliente de la venta: {@code consumidorFinal} si no tiene uno asignado (HU-05.01). */
    /** {@code correoFactura}: HU-05.11, a dónde se enviará la factura; {@code null} si no se envía. */
    public record ClienteDeVenta(boolean consumidorFinal, Cliente cliente, String correoFactura) {

        static ClienteDeVenta de(ClienteYCorreo actual) {
            return new ClienteDeVenta(actual.cliente().isEmpty(), actual.cliente().orElse(null),
                    actual.correoFactura());
        }
    }

    public record SolicitudAsignacion(Long clienteId) {}

    public record SolicitudCorreo(String correo) {}

    /** HU-05.10: lista paginada, con búsqueda por NIT, CUI o nombre. */
    @GetMapping("/api/clientes")
    @PreAuthorize("hasAuthority('CLIENTE_GESTIONAR')")
    public PaginaClientes listar(@RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "0") int pagina,
                                 @RequestParam(defaultValue = "25") int tamano) {
        return gestion.listar(new FiltroClientes(q, pagina, tamano));
    }

    /**
     * HU-05.02 y HU-05.04: el cliente con ese NIT (con o sin guion). Si no está registrado se consulta al certificador.
     * 422 {@code NIT_INVALIDO} si el dígito verificador no es correcto (HU-05.03).
     */
    @GetMapping("/api/clientes/nit/{nit}")
    @PreAuthorize("hasAuthority('CLIENTE_GESTIONAR')")
    public BusquedaNit buscar(@PathVariable String nit, @AuthenticationPrincipal SesionActiva sesion) {
        return gestion.buscar(nit, sesion);
    }

    /** HU-05.05: registra al cliente, o devuelve el existente si el NIT ya está registrado. */
    /** HU-05.06: búsqueda por CUI (13 dígitos); si no está registrado, consulta el nombre al certificador. */
    @GetMapping("/api/clientes/cui/{cui}")
    @PreAuthorize("hasAuthority('CLIENTE_GESTIONAR')")
    public BusquedaNit buscarCui(@PathVariable String cui, @AuthenticationPrincipal SesionActiva sesion) {
        return gestion.buscarCui(cui, sesion);
    }

    @PostMapping("/api/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENTE_GESTIONAR')")
    public Cliente registrar(@RequestBody SolicitudCliente solicitud, @AuthenticationPrincipal SesionActiva sesion) {
        return gestion.registrar(solicitud, sesion);
    }

    @GetMapping("/api/ventas/actual/cliente")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public ClienteDeVenta deLaVenta(@AuthenticationPrincipal SesionActiva sesion) {
        return ClienteDeVenta.de(gestion.deLaVentaActual(sesion));
    }

    /**
     * HU-08.06 crit. 2: corrige el cliente de una venta ya cobrada cuya factura se rechazó por el receptor y la reenvía.
     * Solo el cajero de esa venta, en su turno abierto; 422 si el rechazo no se corrige en caja.
     */
    @PostMapping("/api/ventas/{ventaId}/factura/reenviar")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public FacturaDeVenta reenviarFactura(@PathVariable long ventaId, @RequestBody SolicitudAsignacion solicitud,
                                          @AuthenticationPrincipal SesionActiva sesion) {
        return gestion.reenviarFactura(ventaId, solicitud.clienteId(), sesion);
    }

    /** Asigna el cliente a la venta en curso; {@code clienteId} vacío vuelve a Consumidor Final. */
    @PutMapping("/api/ventas/actual/cliente")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public ClienteDeVenta asignar(@RequestBody SolicitudAsignacion solicitud,
                                  @AuthenticationPrincipal SesionActiva sesion) {
        return ClienteDeVenta.de(gestion.asignar(solicitud.clienteId(), sesion));
    }

    /** HU-05.11: el correo para la factura de la venta en curso; vacío no la envía. 422 {@code CORREO_INVALIDO}. */
    @PutMapping("/api/ventas/actual/cliente/correo")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public ClienteDeVenta cambiarCorreo(@RequestBody SolicitudCorreo solicitud,
                                        @AuthenticationPrincipal SesionActiva sesion) {
        return ClienteDeVenta.de(gestion.cambiarCorreoFactura(solicitud.correo(), sesion));
    }
}
