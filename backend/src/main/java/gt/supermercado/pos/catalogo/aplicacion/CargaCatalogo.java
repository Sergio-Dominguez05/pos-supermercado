package gt.supermercado.pos.catalogo.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo;
import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo.Fila;
import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo.Formato;
import gt.supermercado.pos.catalogo.dominio.Producto;
import gt.supermercado.pos.catalogo.dominio.ReglasProducto;
import gt.supermercado.pos.catalogo.dominio.RepositorioCodigosNoEncontrados;
import gt.supermercado.pos.catalogo.dominio.RepositorioProductos;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU-03.05 Cargar el catálogo desde un archivo.
 * <ul>
 *   <li>Crit. 1: la plantilla trae las columnas código, descripción y precio ({@link ArchivoCatalogo}).</li>
 *   <li>Crit. 2: {@link #revisar} valida el archivo sin guardar nada y separa los productos nuevos, los que se
 *       actualizarán (con su valor anterior y el nuevo) y las filas con errores.</li>
 *   <li>Crit. 3: un código que ya existe actualiza su descripción y su precio (el estado no cambia).</li>
 *   <li>Crit. 4: no se cargan las filas con errores (código vacío o inválido, descripción vacía o larga, precio
 *       inválido, código repetido dentro del archivo); cada una dice su motivo.</li>
 *   <li>Crit. 5: {@link #aplicar} solo carga cuando el usuario lo confirma, con el mismo archivo que revisó (lo
 *       comprueba con su huella SHA-256).</li>
 *   <li>Crit. 6: la carga queda en la bitácora como {@code CATALOGO_CARGADO} con la cantidad de productos creados y
 *       actualizados. Además, cada producto creado o cambiado deja su registro de siempre ({@code PRODUCTO_CREADO},
 *       {@code PRODUCTO_EDITADO} y {@code PRECIO_CAMBIADO}), del que salen el historial de precios (HU-03.11) y el
 *       valor anterior y nuevo de cada cambio (HU-11.03).</li>
 * </ul>
 */
@Service
public class CargaCatalogo {

    private final RepositorioProductos productos;
    private final RepositorioCodigosNoEncontrados codigosNoEncontrados;
    private final ArchivoCatalogo.Lector lector;
    private final ArchivoCatalogo.Escritor escritor;
    private final Bitacora bitacora;

    public CargaCatalogo(RepositorioProductos productos, RepositorioCodigosNoEncontrados codigosNoEncontrados,
                         ArchivoCatalogo.Lector lector, ArchivoCatalogo.Escritor escritor, Bitacora bitacora) {
        this.productos = productos;
        this.codigosNoEncontrados = codigosNoEncontrados;
        this.lector = lector;
        this.escritor = escritor;
        this.bitacora = bitacora;
    }

    /** Un producto que se creará. */
    public record Nuevo(int fila, String codigo, String descripcion, BigDecimal precio) {}

    /** Un producto que ya existe y cambiará; {@code null} en lo que no cambia. */
    public record Actualizado(int fila, long productoId, String codigo, String descripcionAntes,
                              String descripcionDespues, BigDecimal precioAntes, BigDecimal precioDespues) {}

    /** Crit. 4: una fila que no se cargará y por qué. */
    public record Error(int fila, String codigo, String motivo) {}

    /**
     * Crit. 2: lo que haría la carga. {@code huella} identifica el archivo revisado; para aplicar hay que enviar el
     * mismo (crit. 5).
     */
    public record Revision(String huella, int filas, List<Nuevo> nuevos, List<Actualizado> actualizados,
                           int sinCambios, List<Error> errores) {}

    /** Crit. 6: lo que se cargó. */
    public record Resultado(int creados, int actualizados, int sinCambios, int errores) {}

    public byte[] plantilla(Formato formato) {
        return escritor.plantilla(formato);
    }

    /** Crit. 2: valida el archivo y dice qué se crearía, qué se actualizaría y qué tiene errores. No guarda nada. */
    @Transactional(readOnly = true)
    public Revision revisar(byte[] contenido) {
        List<Fila> filas = lector.leer(contenido);
        Map<String, List<Fila>> porCodigo = filas.stream()
                .collect(Collectors.groupingBy(fila -> ReglasProducto.normalizarCodigo(sinApostrofo(fila.codigo())),
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, Producto> existentes = productos.todos().stream()
                .collect(Collectors.toMap(Producto::codigoBarras, Function.identity()));
        List<Nuevo> nuevos = new ArrayList<>();
        List<Actualizado> actualizados = new ArrayList<>();
        List<Error> errores = new ArrayList<>();
        int sinCambios = 0;
        for (Fila fila : filas) {
            String codigo = ReglasProducto.normalizarCodigo(sinApostrofo(fila.codigo()));
            String descripcion = ReglasProducto.normalizarDescripcion(sinApostrofo(fila.descripcion()));
            Optional<String> motivo = motivo(codigo, descripcion, fila.precio(), porCodigo.get(codigo));
            if (motivo.isPresent()) {
                errores.add(new Error(fila.numero(), fila.codigo(), motivo.get()));
                continue;
            }
            BigDecimal precio = ReglasProducto.normalizarPrecio(precio(fila.precio()).orElseThrow());
            Producto actual = existentes.get(codigo);
            if (actual == null) {
                nuevos.add(new Nuevo(fila.numero(), codigo, descripcion, precio));
                continue;
            }
            boolean cambiaDescripcion = !descripcion.equals(actual.descripcion());
            boolean cambiaPrecio = precio.compareTo(actual.precio()) != 0;
            if (!cambiaDescripcion && !cambiaPrecio) {
                sinCambios++;
            } else {
                actualizados.add(new Actualizado(fila.numero(), actual.id(), codigo,
                        cambiaDescripcion ? actual.descripcion() : null, cambiaDescripcion ? descripcion : null,
                        cambiaPrecio ? actual.precio() : null, cambiaPrecio ? precio : null));
            }
        }
        return new Revision(huella(contenido), filas.size(), List.copyOf(nuevos), List.copyOf(actualizados),
                sinCambios, List.copyOf(errores));
    }

    /**
     * Crit. 5: aplica la carga del archivo ya revisado. {@code huellaRevisada} debe ser la de la revisión; si el
     * archivo cambió, se rechaza para que no se cargue algo distinto de lo que se vio. Todo va en una transacción: o se
     * cargan todas las filas válidas, o ninguna.
     */
    @Transactional
    public Resultado aplicar(byte[] contenido, String huellaRevisada, SesionActiva sesion) {
        Revision revision;
        try {
            revision = revisar(contenido);
            if (huellaRevisada == null || !huellaRevisada.equals(revision.huella())) {
                throw new NegocioException("CARGA_NO_REVISADA",
                        "El archivo no es el que se revisó. Revíselo otra vez antes de confirmar la carga.");
            }
            if (revision.nuevos().isEmpty() && revision.actualizados().isEmpty() && revision.sinCambios() == 0) {
                throw new NegocioException("CARGA_SIN_PRODUCTOS",
                        "El archivo no tiene ningún producto válido para cargar.");
            }
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(evento(sesion, false, "Carga del catálogo rechazada: "
                    + error.getMessage(), null, null));
            throw error;
        }
        for (Nuevo nuevo : revision.nuevos()) {
            long id = productos.crear(nuevo.codigo(), nuevo.descripcion(), nuevo.precio());
            codigosNoEncontrados.resolver(nuevo.codigo());
            bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.PRODUCTO_CREADO,
                    true, "Producto '%s' (%s) creado con precio Q%s (carga del catálogo, fila %d)".formatted(
                            nuevo.descripcion(), nuevo.codigo(), nuevo.precio(), nuevo.fila()), "producto", id, null));
        }
        for (Actualizado cambio : revision.actualizados()) {
            Producto actual = productos.buscarPorId(cambio.productoId()).orElseThrow();
            String descripcion = cambio.descripcionDespues() != null ? cambio.descripcionDespues() : actual.descripcion();
            BigDecimal precio = cambio.precioDespues() != null ? cambio.precioDespues() : actual.precio();
            productos.actualizar(actual.id(), descripcion, precio);
            if (cambio.descripcionDespues() != null) {
                bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                        AccionSensible.PRODUCTO_EDITADO, true, "Producto %s: descripción cambiada de '%s' a '%s' "
                                .formatted(actual.codigoBarras(), actual.descripcion(), descripcion)
                                + "(carga del catálogo, fila %d)".formatted(cambio.fila()), "producto", actual.id(),
                        Map.of("descripcion", EventoBitacora.cambio(actual.descripcion(), descripcion))));
            }
            if (cambio.precioDespues() != null) {
                bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                        AccionSensible.PRECIO_CAMBIADO, true, "Producto '%s' (%s): precio cambiado de Q%s a Q%s "
                                .formatted(descripcion, actual.codigoBarras(), actual.precio(), precio)
                                + "(carga del catálogo, fila %d)".formatted(cambio.fila()), "producto", actual.id(),
                        Map.of("precio", EventoBitacora.cambio(actual.precio(), precio))));
            }
        }
        Resultado resultado = new Resultado(revision.nuevos().size(), revision.actualizados().size(),
                revision.sinCambios(), revision.errores().size());
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("creados", resultado.creados());
        detalle.put("actualizados", resultado.actualizados());
        detalle.put("sinCambios", resultado.sinCambios());
        detalle.put("filasConErrores", resultado.errores());
        detalle.put("huella", revision.huella());
        bitacora.registrar(evento(sesion, true, "Catálogo cargado desde un archivo: %d creados, %d actualizados, "
                .formatted(resultado.creados(), resultado.actualizados())
                + "%d sin cambios y %d filas con errores".formatted(resultado.sinCambios(), resultado.errores()),
                null, detalle));
        return resultado;
    }

    /** Crit. 4: por qué no se carga la fila, o vacío si es válida. */
    private static Optional<String> motivo(String codigo, String descripcion, String precio, List<Fila> conElCodigo) {
        if (codigo.isEmpty()) {
            return Optional.of("El código está vacío.");
        }
        if (!ReglasProducto.esCodigoValido(codigo)) {
            return Optional.of("El código solo puede tener letras, números y guiones (hasta %d)."
                    .formatted(ReglasProducto.LARGO_MAXIMO_CODIGO));
        }
        if (conElCodigo != null && conElCodigo.size() > 1) {
            return Optional.of("El código está repetido en el archivo (filas %s)."
                    .formatted(conElCodigo.stream().map(f -> String.valueOf(f.numero())).collect(Collectors.joining(", "))));
        }
        if (!ReglasProducto.esDescripcionValida(descripcion)) {
            return Optional.of(descripcion.isEmpty() ? "La descripción está vacía."
                    : "La descripción admite hasta %d caracteres.".formatted(ReglasProducto.LARGO_MAXIMO_DESCRIPCION));
        }
        Optional<BigDecimal> valor = precio(precio);
        if (valor.isEmpty() || !ReglasProducto.esPrecioValido(valor.get())) {
            return Optional.of(precio.isBlank() ? "El precio está vacío."
                    : "El precio \"%s\" no es válido: debe ser mayor que cero y con 2 decimales como máximo."
                            .formatted(precio));
        }
        return Optional.empty();
    }

    /**
     * El precio como número. Acepta "12.50", "12,50", "Q12.50" y "1,234.50"; con coma y punto, la coma es de miles.
     */
    static Optional<BigDecimal> precio(String texto) {
        String limpio = texto == null ? "" : texto.strip().replaceFirst("^[Qq]\\s*", "").replace(" ", "");
        if (limpio.contains(",") && limpio.contains(".")) {
            limpio = limpio.replace(",", "");
        } else {
            limpio = limpio.replace(',', '.');
        }
        try {
            return limpio.isEmpty() ? Optional.empty() : Optional.of(new BigDecimal(limpio));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** La exportación (HU-03.12) protege las fórmulas con un apóstrofo; al cargar se le quita. */
    static String sinApostrofo(String texto) {
        return texto != null && texto.length() > 1 && texto.charAt(0) == '\'' && "=+-@".indexOf(texto.charAt(1)) >= 0
                ? texto.substring(1) : texto;
    }

    private static String huella(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion, Long id,
                                         Map<String, Object> detalle) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.CATALOGO_CARGADO, exitoso,
                descripcion, "producto", id, detalle);
    }
}
