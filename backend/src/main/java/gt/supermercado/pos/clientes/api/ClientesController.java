package gt.supermercado.pos.clientes.api;

import gt.supermercado.pos.clientes.aplicacion.GestionClientes;
import gt.supermercado.pos.clientes.aplicacion.GestionClientes.BusquedaNit;
import gt.supermercado.pos.clientes.aplicacion.GestionClientes.SolicitudCliente;
import gt.supermercado.pos.clientes.dominio.Cliente;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/** EP-05: buscar, registrar y asignar el cliente de la venta en curso. */
@RestController
public class ClientesController {

    private final GestionClientes gestion;

    public ClientesController(GestionClientes gestion) {
        this.gestion = gestion;
    }

    /** El cliente de la venta: {@code consumidorFinal} si no tiene uno asignado (HU-05.01). */
    public record ClienteDeVenta(boolean consumidorFinal, Cliente cliente) {

        static ClienteDeVenta de(Optional<Cliente> cliente) {
            return new ClienteDeVenta(cliente.isEmpty(), cliente.orElse(null));
        }
    }

    public record SolicitudAsignacion(Long clienteId) {}

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

    /** Asigna el cliente a la venta en curso; {@code clienteId} vacío vuelve a Consumidor Final. */
    @PutMapping("/api/ventas/actual/cliente")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public ClienteDeVenta asignar(@RequestBody SolicitudAsignacion solicitud,
                                  @AuthenticationPrincipal SesionActiva sesion) {
        return ClienteDeVenta.de(gestion.asignar(solicitud.clienteId(), sesion));
    }
}
