package gt.supermercado.pos.configuracion.dominio;

import java.time.OffsetDateTime;

/**
 * HU-12.01: los datos fiscales de la empresa que aparecen en las facturas. Teléfono y correo son opcionales.
 * HU-08.02: {@code afiliacionIva} es la que pide el DTE; puede faltar en datos guardados antes de esa historia.
 */
public record Emisor(String nit, String razonSocial, String nombreComercial, String direccionFiscal, String telefono,
                     String correo, AfiliacionIva afiliacionIva, OffsetDateTime modificadoEn) {
}
