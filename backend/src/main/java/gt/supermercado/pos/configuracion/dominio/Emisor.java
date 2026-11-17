package gt.supermercado.pos.configuracion.dominio;

import java.time.OffsetDateTime;

/** HU-12.01: los datos fiscales de la empresa que aparecen en las facturas. Teléfono y correo son opcionales. */
public record Emisor(String nit, String razonSocial, String nombreComercial, String direccionFiscal, String telefono,
                     String correo, OffsetDateTime modificadoEn) {
}
