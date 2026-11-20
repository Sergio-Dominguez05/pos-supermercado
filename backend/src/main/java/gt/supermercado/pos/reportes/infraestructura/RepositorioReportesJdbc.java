package gt.supermercado.pos.reportes.infraestructura;

import gt.supermercado.pos.reportes.dominio.DatosEmpresa;
import gt.supermercado.pos.reportes.dominio.DetalleFacturasIva;
import gt.supermercado.pos.reportes.dominio.DetallePagosTarjeta;
import gt.supermercado.pos.reportes.dominio.DetalleVentasCajero;
import gt.supermercado.pos.reportes.dominio.ReporteAnuladas;
import gt.supermercado.pos.reportes.dominio.ReporteCierres;
import gt.supermercado.pos.reportes.dominio.ReporteIva;
import gt.supermercado.pos.reportes.dominio.ReporteTarjetas;
import gt.supermercado.pos.reportes.dominio.TipoTarjetaReporte;
import gt.supermercado.pos.reportes.dominio.FiltrosReporte;
import gt.supermercado.pos.reportes.dominio.OpcionesDeFiltro.Opcion;
import gt.supermercado.pos.reportes.dominio.OrdenProductos;
import gt.supermercado.pos.reportes.dominio.ReporteProductosMasVendidos;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPorCaja;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPorCajero;
import gt.supermercado.pos.reportes.dominio.RepositorioReportes;
import gt.supermercado.pos.reportes.dominio.VentasDelDia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Consultas de solo lectura sobre las tablas de los demás módulos (Architecture.md §4: {@code reportes} lee de
 * todos). Las fechas de los filtros son días en hora de Guatemala.
 */
@Repository
class RepositorioReportesJdbc implements RepositorioReportes {

    private final JdbcTemplate jdbc;

    RepositorioReportesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<VentasDelDia> ventasPorDia(FiltrosReporte filtros) {
        return jdbc.query("""
                SELECT (v.finalizada_en AT TIME ZONE 'America/Guatemala')::date AS fecha, count(*) AS cantidad,
                       sum(v.subtotal) AS subtotal, sum(v.iva) AS iva, sum(v.total) AS total
                FROM venta v
                LEFT JOIN factura f ON f.venta_id = v.id
                WHERE v.estado = 'FINALIZADA'
                  AND (f.estado IS NULL OR f.estado <> 'ANULADA')
                  AND v.finalizada_en >= (?::date)::timestamp AT TIME ZONE 'America/Guatemala'
                  AND v.finalizada_en < (?::date + 1)::timestamp AT TIME ZONE 'America/Guatemala'
                  AND (?::bigint IS NULL OR v.caja_id = ?::bigint)
                  AND (?::bigint IS NULL OR v.usuario_id = ?::bigint)
                GROUP BY 1
                ORDER BY 1""",
                (fila, i) -> new VentasDelDia(fila.getObject("fecha", java.time.LocalDate.class),
                        fila.getLong("cantidad"), fila.getBigDecimal("subtotal"), fila.getBigDecimal("iva"),
                        fila.getBigDecimal("total")),
                filtros.desde(), filtros.hasta(), filtros.cajaId(), filtros.cajaId(), filtros.cajeroId(),
                filtros.cajeroId());
    }

    /** La condición de que la columna caiga dentro del período, en hora de Guatemala; usa dos parámetros. */
    private static String enPeriodo(String columna) {
        return "%1$s >= (?::date)::timestamp AT TIME ZONE 'America/Guatemala' AND %1$s < (?::date + 1)::timestamp AT TIME ZONE 'America/Guatemala'"
                .formatted(columna);
    }

    /** Los filtros de caja y cajero sobre una venta; usa cuatro parámetros. */
    private static String deCajaYCajero(String venta) {
        return "(?::bigint IS NULL OR %1$s.caja_id = ?::bigint) AND (?::bigint IS NULL OR %1$s.usuario_id = ?::bigint)"
                .formatted(venta);
    }

    private static List<Object> parametros(FiltrosReporte filtros) {
        List<Object> valores = new ArrayList<>(List.of(filtros.desde(), filtros.hasta()));
        valores.addAll(Arrays.asList(filtros.cajaId(), filtros.cajaId(), filtros.cajeroId(), filtros.cajeroId()));
        return valores;
    }

    @Override
    public List<ReporteVentasPorCajero.Fila> ventasPorCajero(FiltrosReporte filtros) {
        String sql = """
                WITH finalizadas AS (
                    SELECT v.usuario_id,
                           count(*) FILTER (WHERE f.estado IS DISTINCT FROM 'ANULADA') AS ventas,
                           coalesce(sum(v.total) FILTER (WHERE f.estado IS DISTINCT FROM 'ANULADA'), 0) AS total,
                           count(*) FILTER (WHERE f.estado = 'ANULADA') AS anuladas,
                           coalesce(sum(v.total) FILTER (WHERE f.estado = 'ANULADA'), 0) AS monto_anulado
                    FROM venta v LEFT JOIN factura f ON f.venta_id = v.id
                    WHERE v.estado = 'FINALIZADA' AND %s AND %s
                    GROUP BY v.usuario_id),
                canceladas AS (
                    SELECT v.usuario_id, count(*) AS canceladas FROM venta v
                    WHERE v.estado = 'CANCELADA' AND %s AND %s
                    GROUP BY v.usuario_id),
                eliminadas AS (
                    SELECT v.usuario_id, count(*) AS eliminadas
                    FROM venta_linea l JOIN venta v ON v.id = l.venta_id
                    WHERE l.eliminada_en IS NOT NULL AND %s AND %s
                    GROUP BY v.usuario_id)
                SELECT u.id, u.nombre_completo, u.username,
                       coalesce(fi.ventas, 0) AS ventas, coalesce(fi.total, 0) AS total,
                       coalesce(ca.canceladas, 0) AS canceladas, coalesce(el.eliminadas, 0) AS eliminadas,
                       coalesce(fi.anuladas, 0) AS anuladas, coalesce(fi.monto_anulado, 0) AS monto_anulado
                FROM usuario u
                LEFT JOIN finalizadas fi ON fi.usuario_id = u.id
                LEFT JOIN canceladas ca ON ca.usuario_id = u.id
                LEFT JOIN eliminadas el ON el.usuario_id = u.id
                WHERE fi.usuario_id IS NOT NULL OR ca.usuario_id IS NOT NULL OR el.usuario_id IS NOT NULL
                ORDER BY lower(u.nombre_completo), u.username""".formatted(
                enPeriodo("v.finalizada_en"), deCajaYCajero("v"),
                enPeriodo("v.creada_en"), deCajaYCajero("v"),
                enPeriodo("l.eliminada_en"), deCajaYCajero("v"));
        List<Object> valores = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            valores.addAll(parametros(filtros));
        }
        return jdbc.query(sql, (fila, i) -> new ReporteVentasPorCajero.Fila(fila.getLong("id"),
                fila.getString("nombre_completo") + " (" + fila.getString("username") + ")", fila.getLong("ventas"),
                fila.getBigDecimal("total").setScale(2), fila.getLong("canceladas"), fila.getLong("eliminadas"),
                fila.getLong("anuladas"), fila.getBigDecimal("monto_anulado").setScale(2)), valores.toArray());
    }

    @Override
    public List<ReporteVentasPorCaja.VentasDeCaja> ventasPorCaja(FiltrosReporte filtros) {
        String sql = """
                WITH ventas AS (
                    SELECT v.caja_id, count(*) AS ventas, sum(v.total) AS total
                    FROM venta v LEFT JOIN factura f ON f.venta_id = v.id
                    WHERE v.estado = 'FINALIZADA' AND f.estado IS DISTINCT FROM 'ANULADA' AND %s AND %s
                    GROUP BY v.caja_id),
                turnos AS (
                    SELECT t.caja_id, count(*) AS turnos FROM turno t
                    WHERE %s AND %s
                    GROUP BY t.caja_id)
                SELECT c.id, c.nombre, coalesce(ve.ventas, 0) AS ventas, coalesce(ve.total, 0) AS total,
                       coalesce(tu.turnos, 0) AS turnos
                FROM caja c
                LEFT JOIN ventas ve ON ve.caja_id = c.id
                LEFT JOIN turnos tu ON tu.caja_id = c.id
                WHERE (?::bigint IS NULL OR c.id = ?::bigint)
                  AND (c.activo OR ve.caja_id IS NOT NULL OR tu.caja_id IS NOT NULL)""".formatted(
                enPeriodo("v.finalizada_en"), deCajaYCajero("v"), enPeriodo("t.abierto_en"), deCajaYCajero("t"));
        List<Object> valores = new ArrayList<>(parametros(filtros));
        valores.addAll(parametros(filtros));
        valores.addAll(Arrays.asList(filtros.cajaId(), filtros.cajaId()));
        return jdbc.query(sql, (fila, i) -> new ReporteVentasPorCaja.VentasDeCaja(fila.getLong("id"),
                fila.getString("nombre"), fila.getLong("ventas"), fila.getBigDecimal("total").setScale(2),
                fila.getLong("turnos")), valores.toArray());
    }

    @Override
    public ReporteProductosMasVendidos.Resultado productosMasVendidos(FiltrosReporte filtros, OrdenProductos orden,
                                                                     int limite) {
        // El orden solo puede ser uno de estos dos textos fijos: nunca viene del usuario.
        String criterio = orden == OrdenProductos.MONTO ? "monto DESC, cantidad DESC" : "cantidad DESC, monto DESC";
        String sql = """
                WITH vendidos AS (
                    SELECT l.producto_id, sum(l.cantidad) AS cantidad, round(sum(l.subtotal_con_iva), 2) AS monto
                    FROM venta_linea l
                    JOIN venta v ON v.id = l.venta_id
                    LEFT JOIN factura f ON f.venta_id = v.id
                    WHERE l.eliminada_en IS NULL AND v.estado = 'FINALIZADA' AND f.estado IS DISTINCT FROM 'ANULADA'
                      AND %s AND %s
                    GROUP BY l.producto_id)
                SELECT p.id, p.codigo_barras, p.descripcion, ve.cantidad, ve.monto, count(*) OVER () AS productos
                FROM vendidos ve
                JOIN producto p ON p.id = ve.producto_id
                ORDER BY %s, lower(p.descripcion), p.id
                LIMIT ?""".formatted(enPeriodo("v.finalizada_en"), deCajaYCajero("v"), criterio);
        List<Object> valores = parametros(filtros);
        valores.add(limite);
        long[] productos = {0};
        List<ReporteProductosMasVendidos.Fila> filas = jdbc.query(sql, (fila, i) -> {
            productos[0] = fila.getLong("productos");
            return new ReporteProductosMasVendidos.Fila(i + 1, fila.getLong("id"), fila.getString("codigo_barras"),
                    fila.getString("descripcion"), fila.getLong("cantidad"), fila.getBigDecimal("monto"));
        }, valores.toArray());
        return new ReporteProductosMasVendidos.Resultado(productos[0], filas);
    }

    @Override
    public List<DetalleVentasCajero.Venta> ventasDeCajero(FiltrosReporte filtros) {
        String sql = """
                SELECT v.id, v.numero, v.estado, v.total, c.nombre AS caja, f.estado AS estado_factura,
                       CASE WHEN f.serie IS NOT NULL THEN f.serie || '-' || f.numero END AS factura,
                       CASE WHEN v.estado = 'FINALIZADA' THEN v.finalizada_en ELSE v.creada_en END AS fecha,
                       (SELECT count(*) FROM venta_linea l WHERE l.venta_id = v.id AND l.eliminada_en IS NOT NULL)
                           AS eliminadas
                FROM venta v
                JOIN caja c ON c.id = v.caja_id
                LEFT JOIN factura f ON f.venta_id = v.id
                WHERE ((v.estado = 'FINALIZADA' AND %s) OR (v.estado = 'CANCELADA' AND %s)) AND %s
                ORDER BY fecha, v.numero""".formatted(enPeriodo("v.finalizada_en"), enPeriodo("v.creada_en"),
                deCajaYCajero("v"));
        List<Object> valores = new ArrayList<>(List.of(filtros.desde(), filtros.hasta()));
        valores.addAll(parametros(filtros));
        return jdbc.query(sql, (fila, i) -> new DetalleVentasCajero.Venta(fila.getLong("id"), fila.getLong("numero"),
                fila.getObject("fecha", OffsetDateTime.class), fila.getString("caja"), fila.getString("estado"),
                fila.getBigDecimal("total"), fila.getString("estado_factura"), fila.getString("factura"),
                fila.getLong("eliminadas")), valores.toArray());
    }

    /** Los pagos vigentes de las ventas finalizadas en el período, con los filtros comunes y el de tarjeta. */
    private static final String PAGOS_DEL_PERIODO = """
            FROM pago p
            JOIN venta v ON v.id = p.venta_id
            JOIN caja c ON c.id = v.caja_id
            JOIN usuario u ON u.id = v.usuario_id
            LEFT JOIN factura f ON f.venta_id = v.id
            WHERE p.eliminado_en IS NULL AND v.estado = 'FINALIZADA' AND %s AND %s
              AND (?::text IS NULL OR p.tipo_tarjeta = ?::text)""".formatted(enPeriodo("v.finalizada_en"),
            deCajaYCajero("v"));

    private static List<Object> parametrosConTarjeta(FiltrosReporte filtros) {
        List<Object> valores = parametros(filtros);
        String tipo = filtros.tipoTarjeta() == null ? null : filtros.tipoTarjeta().name();
        valores.addAll(Arrays.asList(tipo, tipo));
        return valores;
    }

    @Override
    public List<ReporteTarjetas.PagosDelTipo> pagosPorTipo(FiltrosReporte filtros) {
        return jdbc.query("""
                SELECT p.tipo_tarjeta, f.estado IS NOT DISTINCT FROM 'ANULADA' AS anulada, count(*) AS transacciones,
                       sum(p.monto) AS monto
                %s
                GROUP BY 1, 2""".formatted(PAGOS_DEL_PERIODO),
                (fila, i) -> new ReporteTarjetas.PagosDelTipo(TipoTarjetaReporte.valueOf(fila.getString("tipo_tarjeta")),
                        fila.getBoolean("anulada"), fila.getLong("transacciones"), fila.getBigDecimal("monto")),
                parametrosConTarjeta(filtros).toArray());
    }

    @Override
    public List<DetallePagosTarjeta.Pago> pagosDeTarjeta(FiltrosReporte filtros) {
        return jdbc.query("""
                SELECT p.id, v.finalizada_en, v.numero, c.nombre AS caja,
                       u.nombre_completo || ' (' || u.username || ')' AS cajero, p.autorizacion, p.ultimos4, p.monto,
                       f.estado IS NOT DISTINCT FROM 'ANULADA' AS anulada
                %s
                ORDER BY v.finalizada_en, p.id""".formatted(PAGOS_DEL_PERIODO),
                (fila, i) -> new DetallePagosTarjeta.Pago(fila.getLong("id"),
                        fila.getObject("finalizada_en", OffsetDateTime.class), fila.getLong("numero"),
                        fila.getString("caja"), fila.getString("cajero"), fila.getString("autorizacion"),
                        fila.getString("ultimos4"), fila.getBigDecimal("monto"), fila.getBoolean("anulada")),
                parametrosConTarjeta(filtros).toArray());
    }

    /** Las facturas emitidas en el período, con los filtros comunes sobre su venta; cada consulta agrega el estado. */
    private static final String FACTURAS_DEL_PERIODO = """
            FROM factura f
            JOIN venta v ON v.id = f.venta_id
            JOIN caja c ON c.id = v.caja_id
            WHERE %s AND %s""".formatted(enPeriodo("f.emitida_en"), deCajaYCajero("v"));

    @Override
    public List<ReporteIva.Dia> ivaPorDia(FiltrosReporte filtros) {
        return jdbc.query("""
                SELECT (f.emitida_en AT TIME ZONE 'America/Guatemala')::date AS fecha, count(*) AS facturas,
                       sum(v.subtotal) AS base, sum(v.iva) AS iva, sum(v.total) AS total
                %s AND f.estado = 'CERTIFICADA'
                GROUP BY 1
                ORDER BY 1""".formatted(FACTURAS_DEL_PERIODO),
                (fila, i) -> new ReporteIva.Dia(fila.getObject("fecha", LocalDate.class), fila.getLong("facturas"),
                        fila.getBigDecimal("base"), fila.getBigDecimal("iva"), fila.getBigDecimal("total")),
                parametros(filtros).toArray());
    }

    @Override
    public ReporteIva.SinCertificar sinCertificar(FiltrosReporte filtros) {
        return jdbc.queryForObject("""
                SELECT count(*) AS facturas, coalesce(sum(v.total), 0) AS total
                %s AND f.estado IN ('PENDIENTE', 'CONTINGENCIA', 'RECHAZADA')""".formatted(FACTURAS_DEL_PERIODO),
                (fila, i) -> new ReporteIva.SinCertificar(fila.getLong("facturas"),
                        fila.getBigDecimal("total").setScale(2)),
                parametros(filtros).toArray());
    }

    @Override
    public List<DetalleFacturasIva.Factura> facturasIva(FiltrosReporte filtros) {
        return jdbc.query("""
                SELECT f.id, f.emitida_en, f.serie, f.numero, f.uuid_autorizacion::text AS autorizacion,
                       f.receptor_id, f.receptor_nombre, c.nombre AS caja, v.subtotal, v.iva, v.total
                %s AND f.estado = 'CERTIFICADA'
                ORDER BY f.emitida_en, f.id""".formatted(FACTURAS_DEL_PERIODO),
                (fila, i) -> new DetalleFacturasIva.Factura(fila.getLong("id"),
                        fila.getObject("emitida_en", OffsetDateTime.class), fila.getString("serie"),
                        fila.getString("numero"), fila.getString("autorizacion"), fila.getString("receptor_id"),
                        fila.getString("receptor_nombre"), fila.getString("caja"), fila.getBigDecimal("subtotal"),
                        fila.getBigDecimal("iva"), fila.getBigDecimal("total")),
                parametros(filtros).toArray());
    }

    private static final String NOMBRE = "%1$s.nombre_completo || ' (' || %1$s.username || ')'";

    @Override
    public List<ReporteCierres.Cierre> cierres(FiltrosReporte filtros, boolean soloConDiferencias) {
        List<Object> valores = parametros(filtros);
        valores.add(soloConDiferencias);
        return jdbc.query("""
                SELECT t.id, t.abierto_en, t.cerrado_en, c.nombre AS caja, %s AS cajero, %s AS cerrado_por,
                       t.total_registrado, t.total_lote, t.diferencia, t.justificacion
                FROM turno t
                JOIN caja c ON c.id = t.caja_id
                JOIN usuario u ON u.id = t.usuario_id
                LEFT JOIN usuario cp ON cp.id = t.cerrado_por
                WHERE t.estado = 'CERRADO' AND %s AND %s AND (NOT ? OR t.diferencia <> 0)
                ORDER BY t.cerrado_en, t.id""".formatted(NOMBRE.formatted("u"), NOMBRE.formatted("cp"),
                        enPeriodo("t.cerrado_en"), deCajaYCajero("t")),
                (fila, i) -> new ReporteCierres.Cierre(fila.getLong("id"),
                        fila.getObject("abierto_en", OffsetDateTime.class),
                        fila.getObject("cerrado_en", OffsetDateTime.class), fila.getString("caja"),
                        fila.getString("cajero"), fila.getString("cerrado_por"), fila.getBigDecimal("total_registrado"),
                        fila.getBigDecimal("total_lote"), fila.getBigDecimal("diferencia"),
                        fila.getString("justificacion")),
                valores.toArray());
    }

    @Override
    public List<ReporteAnuladas.Anulada> anuladas(FiltrosReporte filtros, Long anuladaPorId) {
        List<Object> valores = parametros(filtros);
        valores.addAll(Arrays.asList(anuladaPorId, anuladaPorId));
        return jdbc.query("""
                SELECT f.id, f.emitida_en, a.anulada_en, f.serie, f.numero, v.numero AS venta_numero,
                       c.nombre AS caja, v.total, a.motivo, %s AS cajero, %s AS anulada_por
                FROM anulacion a
                JOIN factura f ON f.id = a.factura_id
                JOIN venta v ON v.id = f.venta_id
                JOIN caja c ON c.id = v.caja_id
                JOIN usuario u ON u.id = v.usuario_id
                JOIN usuario ua ON ua.id = a.usuario_id
                WHERE %s AND %s AND (?::bigint IS NULL OR a.usuario_id = ?::bigint)
                ORDER BY a.anulada_en, f.id""".formatted(NOMBRE.formatted("u"), NOMBRE.formatted("ua"),
                        enPeriodo("a.anulada_en"), deCajaYCajero("v")),
                (fila, i) -> new ReporteAnuladas.Anulada(fila.getLong("id"),
                        fila.getObject("emitida_en", OffsetDateTime.class),
                        fila.getObject("anulada_en", OffsetDateTime.class), fila.getString("serie"),
                        fila.getString("numero"), fila.getLong("venta_numero"), fila.getString("caja"),
                        fila.getBigDecimal("total"), fila.getString("motivo"), fila.getString("cajero"),
                        fila.getString("anulada_por")),
                valores.toArray());
    }

    @Override
    public List<Opcion> anuladores() {
        return jdbc.query("""
                SELECT u.id, u.nombre_completo, u.username FROM usuario u
                WHERE EXISTS (SELECT 1 FROM rol_permiso rp WHERE rp.rol_id = u.rol_id AND rp.permiso = 'FACTURA_ANULAR')
                   OR EXISTS (SELECT 1 FROM anulacion a WHERE a.usuario_id = u.id)
                ORDER BY lower(u.nombre_completo), u.username""",
                (fila, i) -> new Opcion(String.valueOf(fila.getLong("id")),
                        fila.getString("nombre_completo") + " (" + fila.getString("username") + ")"));
    }

    @Override
    public DatosEmpresa empresa() {
        // V1 tiene una sola sucursal (Matriz); su establecimiento es el de menor id.
        return jdbc.query("""
                SELECT e.nombre_comercial, e.razon_social, e.nit, s.nombre AS establecimiento, s.direccion
                FROM (SELECT 1) uno
                LEFT JOIN emisor e ON e.id = 1
                LEFT JOIN (SELECT nombre, direccion FROM establecimiento ORDER BY sucursal_id LIMIT 1) s ON true""",
                (fila, i) -> new DatosEmpresa(fila.getString("nombre_comercial"), fila.getString("razon_social"),
                        fila.getString("nit"), fila.getString("establecimiento"), fila.getString("direccion")))
                .stream().findFirst().orElse(DatosEmpresa.SIN_CONFIGURAR);
    }

    @Override
    public List<Opcion> cajas() {
        return jdbc.query("""
                SELECT id, nombre, activo FROM caja ORDER BY lower(nombre)""",
                (fila, i) -> new Opcion(String.valueOf(fila.getLong("id")),
                        fila.getString("nombre") + (fila.getBoolean("activo") ? "" : " (inactiva)")));
    }

    @Override
    public List<Opcion> cajeros() {
        return jdbc.query("""
                SELECT u.id, u.nombre_completo, u.username FROM usuario u
                WHERE EXISTS (SELECT 1 FROM rol_permiso rp WHERE rp.rol_id = u.rol_id AND rp.permiso = 'VENTA_REGISTRAR')
                   OR EXISTS (SELECT 1 FROM venta v WHERE v.usuario_id = u.id)
                ORDER BY lower(u.nombre_completo), u.username""",
                (fila, i) -> new Opcion(String.valueOf(fila.getLong("id")),
                        fila.getString("nombre_completo") + " (" + fila.getString("username") + ")"));
    }

    @Override
    public Optional<String> nombreCaja(long cajaId) {
        return jdbc.query("SELECT nombre FROM caja WHERE id = ?", (fila, i) -> fila.getString("nombre"), cajaId)
                .stream().findFirst();
    }

    @Override
    public Optional<String> nombreCajero(long usuarioId) {
        return jdbc.query("SELECT nombre_completo, username FROM usuario WHERE id = ?",
                (fila, i) -> fila.getString("nombre_completo") + " (" + fila.getString("username") + ")", usuarioId)
                .stream().findFirst();
    }
}
