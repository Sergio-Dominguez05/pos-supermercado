package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.facturacion.dominio.DetalleFactura;
import gt.supermercado.pos.facturacion.dominio.DetalleFactura.Evento;
import gt.supermercado.pos.facturacion.dominio.DetalleFactura.TipoEvento;
import gt.supermercado.pos.facturacion.dominio.FiltrosHistorial;
import gt.supermercado.pos.facturacion.dominio.HistorialFacturas;
import gt.supermercado.pos.facturacion.dominio.RepositorioHistorialFacturas;
import gt.supermercado.pos.facturacion.dominio.RepositorioHistorialFacturas.EventoRegistrado;
import gt.supermercado.pos.facturacion.dominio.RepositorioHistorialFacturas.Opcion;
import gt.supermercado.pos.facturacion.dominio.TraductorErrores;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * HU-08.13 Consultar el historial de facturas y HU-08.14 Consultar el detalle de una factura, para el Supervisor y el
 * Administrador (permiso de anular facturas, decisión de Sergio). Solo leen: no quedan en la bitácora.
 */
@Service
public class ConsultaHistorialFacturas {

    private final RepositorioHistorialFacturas repositorio;

    public ConsultaHistorialFacturas(RepositorioHistorialFacturas repositorio) {
        this.repositorio = repositorio;
    }

    /** Crit. 2 y 3: filtros y búsqueda, de la más reciente a la más antigua, por páginas. */
    @Transactional(readOnly = true)
    public HistorialFacturas buscar(FiltrosHistorial filtros) {
        return repositorio.buscar(filtros);
    }

    /** Las cajas y los cajeros para los filtros (crit. 2). */
    public record OpcionesHistorial(List<Opcion> cajas, List<Opcion> cajeros) {}

    @Transactional(readOnly = true)
    public OpcionesHistorial opciones() {
        return new OpcionesHistorial(repositorio.cajas(), repositorio.cajeros());
    }

    /** HU-08.14 crit. 1 y 2: la factura con sus líneas, pagos e historial. */
    @Transactional(readOnly = true)
    public DetalleFactura detalle(long facturaId) {
        DetalleFactura factura = repositorio.detalle(facturaId).orElseThrow(() -> new RecursoNoEncontradoException(
                "FACTURA_NO_ENCONTRADA", "La factura no existe."));
        List<Evento> historial = new ArrayList<>();
        historial.add(new Evento(factura.emitidaEn(), TipoEvento.EMITIDA,
                "Emitida con la venta N.º " + factura.ventaNumero(), factura.cajero()));
        for (EventoRegistrado registrado : repositorio.eventos(facturaId)) {
            Evento evento = describir(registrado);
            if (evento != null) {
                historial.add(evento);
            }
        }
        return new DetalleFactura(factura.facturaId(), factura.ventaId(), factura.ventaNumero(), factura.estado(),
                factura.caja(), factura.cajero(), factura.turnoAbierto(), factura.emitidaEn(), factura.emisor(), factura.receptor(),
                factura.certificacion(), repositorio.lineas(factura.ventaId()), factura.subtotal(), factura.iva(),
                factura.total(), repositorio.pagos(factura.ventaId()), List.copyOf(historial));
    }

    /** Un hecho guardado, en palabras (crit. 2). La anulación aceptada se describe con su motivo, una sola vez. */
    static Evento describir(EventoRegistrado e) {
        return switch (e.origen()) {
            case "REIMPRESION" -> new Evento(e.momento(), TipoEvento.REIMPRESA,
                    e.caja() == null ? "Reimpresa" : "Reimpresa en " + e.caja(), e.usuario());
            case "ANULACION" -> new Evento(e.momento(), TipoEvento.ANULADA, "Anulada: " + e.mensaje(), e.usuario());
            default -> switch (e.operacion()) {
                case "CERTIFICAR" -> Boolean.TRUE.equals(e.exitoso())
                        ? new Evento(e.momento(), TipoEvento.CERTIFICADA, "Certificada ante la SAT", null)
                        : "SIN_RESPUESTA".equals(e.codigoError())
                                ? new Evento(e.momento(), TipoEvento.SIN_RESPUESTA,
                                        "Sin respuesta del certificador: queda en contingencia", null)
                                : new Evento(e.momento(), TipoEvento.RECHAZADA, "Rechazada por el certificador: "
                                        + TraductorErrores.traducir(e.codigoError(), e.mensaje()).mensaje(), null);
                case "CONSULTAR" -> new Evento(e.momento(), TipoEvento.CONSULTA, Boolean.TRUE.equals(e.exitoso())
                        ? "Consulta al certificador antes de reenviar" : "Consulta al certificador sin respuesta",
                        null);
                case "ANULAR" -> Boolean.TRUE.equals(e.exitoso()) ? null
                        : new Evento(e.momento(), TipoEvento.ANULACION_RECHAZADA,
                                "El certificador no aceptó la anulación"
                                        + (e.mensaje() == null ? "" : ": " + e.mensaje()), null);
                // HU-08.15: el mensaje es el destinatario y, si falló, el motivo.
                case "CORREO" -> Boolean.TRUE.equals(e.exitoso())
                        ? new Evento(e.momento(), TipoEvento.CORREO_ENVIADO, "Enviada por correo a " + e.mensaje(), null)
                        : new Evento(e.momento(), TipoEvento.CORREO_FALLIDO, "No se pudo enviar por correo a "
                                + e.mensaje(), null);
                default -> null;
            };
        };
    }
}
