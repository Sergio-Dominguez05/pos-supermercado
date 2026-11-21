package gt.supermercado.pos.bitacora.dominio;

import java.util.List;

/** HU-11.09: convierte los registros de la bitácora en un archivo de Excel o CSV. */
public interface ExportadorBitacora {

    /** Las columnas del archivo, en orden; las mismas en los dos formatos. */
    List<String> COLUMNAS = List.of("Secuencia", "Fecha y hora", "Usuario", "Rol", "Caja", "Módulo", "Acción",
            "Resultado", "Descripción", "Entidad", "Id de la entidad", "Cambios");

    byte[] exportar(List<RegistroExportado> registros, FormatoBitacora formato);
}
