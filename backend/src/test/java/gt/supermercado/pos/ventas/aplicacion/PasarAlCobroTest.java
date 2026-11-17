package gt.supermercado.pos.ventas.aplicacion;

import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.caja.dominio.EstadoTurno;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.RepositorioVentas;
import gt.supermercado.pos.ventas.dominio.ValidadorClienteDeVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-06.15 crit. 3: antes de cobrar se aplican las reglas del cliente de EP-05. El módulo de clientes no existe
 * todavía (la implementación provisional acepta todo), así que la regla se prueba con un validador que rechaza.
 */
class PasarAlCobroTest {

    private static final Turno TURNO = new Turno(7, 1, "Caja 1", 20, "Ana López", EstadoTurno.ABIERTO,
            OffsetDateTime.now());
    private static final LineaVenta ARROZ = new LineaVenta(1, 5, "7401", "Arroz 1 lb", 1, new BigDecimal("5.00"),
            new BigDecimal("5.000000"), new BigDecimal("4.464286"), new BigDecimal("0.535714"));

    private final RepositorioVentas ventas = mock(RepositorioVentas.class);
    private final GestionTurnos turnos = mock(GestionTurnos.class);
    private final ValidadorClienteDeVenta clientes = mock(ValidadorClienteDeVenta.class);
    private final SesionActiva sesion = new SesionActiva(1, 20, "ana", "Ana López", Rol.CAJERO, "Cajero", false,
            Set.of("VENTA_REGISTRAR", "PAGO_REGISTRAR"), 1L, "Caja 1");
    private final GestionVentas gestion = new GestionVentas(ventas, turnos, null, null, clientes, null);

    @BeforeEach
    void turnoAbierto() {
        when(turnos.exigirTurnoAbierto(sesion)).thenReturn(TURNO);
    }

    @Test
    void siElClienteNoCumpleLasReglasNoSePasaAlCobro() {
        Venta venta = venta(List.of(ARROZ));
        when(ventas.enCursoDelTurno(TURNO.id())).thenReturn(Optional.of(venta));
        doThrow(new NegocioException("CF_SOBRE_LIMITE", "Para Q2,500.00 o más se pide NIT o CUI."))
                .when(clientes).validar(venta);

        assertThatThrownBy(() -> gestion.pasarAlCobro(sesion))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Para Q2,500.00 o más se pide NIT o CUI.");
    }

    @Test
    void siElClienteCumpleLasReglasDevuelveLaVentaParaCobrarla() {
        Venta venta = venta(List.of(ARROZ));
        when(ventas.enCursoDelTurno(TURNO.id())).thenReturn(Optional.of(venta));

        assertThat(gestion.pasarAlCobro(sesion)).isEqualTo(venta);
        verify(clientes).validar(venta);
    }

    @Test
    void unaVentaVaciaSeRechazaAntesDeValidarAlCliente() {
        when(ventas.enCursoDelTurno(TURNO.id())).thenReturn(Optional.of(venta(List.of())));

        assertThatThrownBy(() -> gestion.pasarAlCobro(sesion))
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("VENTA_VACIA");
        verify(clientes, never()).validar(any());
    }

    private static Venta venta(List<LineaVenta> lineas) {
        return new Venta(31, 1042, TURNO.id(), 1, "Caja 1", 20, EstadoVenta.EN_CURSO, new BigDecimal("0.12"),
                OffsetDateTime.now(), null, lineas);
    }
}
