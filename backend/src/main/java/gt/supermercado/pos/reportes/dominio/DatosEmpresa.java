package gt.supermercado.pos.reportes.dominio;

import java.util.ArrayList;
import java.util.List;

/**
 * HU-10.13 crit. 2: los datos de la empresa que encabezan el PDF (y el Excel): el emisor (HU-12.01) y el
 * establecimiento (HU-12.02). Cualquiera puede faltar si aún no se configuró.
 */
public record DatosEmpresa(String nombreComercial, String razonSocial, String nit, String establecimiento,
                           String direccion) {

    public static final DatosEmpresa SIN_CONFIGURAR = new DatosEmpresa(null, null, null, null, null);

    /** Las líneas del encabezado, sin las que faltan; un aviso si no hay ningún dato. */
    public List<String> lineas() {
        List<String> lineas = new ArrayList<>();
        if (nombreComercial != null) {
            lineas.add(nombreComercial);
        }
        if (razonSocial != null) {
            lineas.add(razonSocial + (nit == null ? "" : " · NIT " + nit));
        }
        if (establecimiento != null) {
            lineas.add(establecimiento + (direccion == null ? "" : " · " + direccion));
        }
        if (lineas.isEmpty()) {
            lineas.add("Datos del emisor sin configurar");
        }
        return lineas;
    }
}
