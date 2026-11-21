package gt.supermercado.pos.bitacora.dominio;

/**
 * HU-11.09: un registro de la bitácora tal como se exporta: lo que se ve en la lista, más la entidad afectada y los
 * cambios (valor anterior y nuevo, HU-11.03) en JSON. Los datos sensibles ya vienen como "modificado".
 */
public record RegistroExportado(RegistroConsultado registro, String entidad, Long entidadId, String cambios) {
}
