package gt.supermercado.pos.pagos.aplicacion;

import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.caja.dominio.EstadoTurno;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.pagos.aplicacion.GestionPagos.EstadoCobro;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.RepositorioPagos;
import gt.supermercado.pos.pagos.dominio.TipoTarjeta;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HU-07.07: la venta no se finaliza si los pagos no cubren el total. Finalizar llega con HU-07.08 (#63), que parte de
 * {@link GestionPagos#paraFinalizar}; aquí se prueba la regla con la venta en curso de un Q15.00.
 */
class ValidarPagoCompletoTest {

    private static final Turno TURNO = new Turno(7, 1, "Caja 1", 20, "Ana López", EstadoTurno.ABIERTO,
            OffsetDateTime.now());
    private static final LineaVenta ARROZ = new LineaVenta(1, 5, "7401", "Arroz 1 lb", 3, new BigDecimal("5.00"),
            new BigDecimal("15.000000"), new BigDecimal("13.392857"), new BigDecimal("1.607143"));
    private static final Venta VENTA = new Venta(31, 1042, TURNO.id(), 1, "Caja 1", 20, EstadoVenta.EN_CURSO,
            new BigDecimal("0.12"), OffsetDateTime.now(), null, List.of(ARROZ));

    private final RepositorioPagos pagos = mock(RepositorioPagos.class);
    private final GestionTurnos turnos = mock(GestionTurnos.class);
    private final GestionVentas ventas = mock(GestionVentas.class);
    private final SesionActiva sesion = new SesionActiva(1, 20, "ana", "Ana López", Rol.CAJERO, "Cajero", false,
            Set.of("VENTA_REGISTRAR", "PAGO_REGISTRAR"), 1L, "Caja 1");
    private final GestionPagos gestion = new GestionPagos(pagos, turnos, ventas, null, null, null, null);

    @BeforeEach
    void ventaEnCurso() {
        when(turnos.exigirTurnoAbierto(sesion)).thenReturn(TURNO);
        when(ventas.enCursoBloqueada(TURNO)).thenReturn(VENTA);
    }

    @Test
    void conPagosMenoresAlTotalNoSeFinaliza() {   // crit. 1
        when(pagos.deLaVenta(VENTA.id())).thenReturn(List.of(pago("10.00")));

        assertThatThrownBy(() -> gestion.paraFinalizar(sesion))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Falta cobrar Q5.00: la venta no se puede finalizar con el pago incompleto.")
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("PAGO_INCOMPLETO");
    }

    @Test
    void sinPagosNoSeFinaliza() {   // crit. 1
        when(pagos.deLaVenta(VENTA.id())).thenReturn(List.of());

        assertThatThrownBy(() -> gestion.paraFinalizar(sesion))
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("PAGO_INCOMPLETO");
    }

    @Test
    void conPagosIgualesAlTotalSePuedeFinalizar() {   // crit. 2
        when(pagos.deLaVenta(VENTA.id())).thenReturn(List.of(pago("15.00")));

        EstadoCobro estado = gestion.paraFinalizar(sesion);

        assertThat(estado.completo()).isTrue();
        assertThat(estado.pendiente()).isEqualByComparingTo("0");
    }

    @Test
    void elCobroIndicaSiEstaCompleto() {
        assertThat(new EstadoCobro(VENTA, List.of(), new BigDecimal("0.00"), new BigDecimal("15.00")).completo())
                .isFalse();
        assertThat(new EstadoCobro(VENTA, List.of(pago("10.00")), new BigDecimal("10.00"), new BigDecimal("5.00"))
                .completo()).isFalse();
        assertThat(new EstadoCobro(VENTA, List.of(pago("15.00")), new BigDecimal("15.00"), new BigDecimal("0.00"))
                .completo()).isTrue();
    }

    private static Pago pago(String monto) {
        return new Pago(3, VENTA.id(), TURNO.id(), 20, TipoTarjeta.DEBITO, "123456", null, new BigDecimal(monto),
                OffsetDateTime.now());
    }
}
