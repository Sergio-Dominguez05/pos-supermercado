package gt.supermercado.pos.facturacion;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-08.03: una venta solo se finaliza si su factura se puede armar. Las pruebas de integración que finalizan ventas
 * dejan configurados el emisor y el establecimiento de la Matriz, sin pisar lo que otra prueba ya haya guardado.
 */
public final class FacturacionDePrueba {

    private FacturacionDePrueba() {
    }

    public static void configurarEmisorYEstablecimiento(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO emisor (id, nit, razon_social, nombre_comercial, direccion_fiscal, afiliacion_iva,
                                    modificado_por)
                VALUES (1, '576937K', 'Supermercados del Valle, S.A.', 'Súper del Valle', 'Zona 1, Guatemala', 'GEN',
                        (SELECT min(id) FROM usuario))
                ON CONFLICT (id) DO UPDATE SET afiliacion_iva = 'GEN'
                WHERE emisor.afiliacion_iva IS DISTINCT FROM 'GEN'""");
        jdbc.update("""
                INSERT INTO establecimiento (sucursal_id, codigo_sat, nombre, direccion, codigo_postal, municipio,
                                             departamento, modificado_por)
                SELECT id, '1', 'Súper del Valle, zona 1', '6a. avenida 5-10, zona 1', '01001', 'Guatemala',
                       'Guatemala', (SELECT min(id) FROM usuario)
                FROM sucursal WHERE codigo = 'MATRIZ'
                ON CONFLICT (sucursal_id) DO UPDATE SET
                    codigo_postal = coalesce(establecimiento.codigo_postal, excluded.codigo_postal),
                    municipio = coalesce(establecimiento.municipio, excluded.municipio),
                    departamento = coalesce(establecimiento.departamento, excluded.departamento)""");
    }
}
