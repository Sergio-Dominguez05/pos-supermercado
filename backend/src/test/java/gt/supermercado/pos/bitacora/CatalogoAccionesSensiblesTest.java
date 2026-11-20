package gt.supermercado.pos.bitacora;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.AccionSensible.Categoria;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static gt.supermercado.pos.bitacora.dominio.AccionSensible.*;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-11.01 crit. 1: el catálogo de acciones sensibles cubre todos los eventos que pide la historia, y cada acción
 * que ya tiene su módulo la registra algún caso de uso.
 */
class CatalogoAccionesSensiblesTest {

    /**
     * Acciones de módulos que aún no existen, con la historia que debe registrarlas. Cuando esa historia empiece a
     * registrar la acción, esta prueba falla hasta quitarla de aquí y agregar su caso a
     * {@link AccionesSensiblesIntegracionTest}.
     */
    static final Map<AccionSensible, String> PENDIENTES = Map.ofEntries(
            entry(CATALOGO_CARGADO, "HU-03.05"));

    private static final Set<String> MODULOS = Set.of(
            "seguridad", "catalogo", "ventas", "clientes", "pagos", "facturacion", "caja", "configuracion", "reportes");

    @Test
    void elCatalogoTieneTodosLosEventosDelCriterio1() {
        assertThat(porCategoria(Categoria.SESION)).contains(INICIO_SESION, CIERRE_SESION);
        assertThat(porCategoria(Categoria.USUARIOS_Y_PERMISOS)).contains(USUARIO_CREADO, USUARIO_EDITADO,
                USUARIO_ACTIVADO, USUARIO_DESACTIVADO, PERMISOS_MODIFICADOS, CREDENCIAL_RESTABLECIDA);
        assertThat(porCategoria(Categoria.VENTAS)).contains(LINEA_VENTA_ELIMINADA, VENTA_CANCELADA);
        assertThat(porCategoria(Categoria.FACTURACION)).contains(FACTURA_ANULADA, FACTURA_REIMPRESA,
                FACTURA_RECHAZADA_CERTIFICADOR);
        assertThat(porCategoria(Categoria.PAGOS)).contains(PAGO_TARJETA_ELIMINADO);
        assertThat(porCategoria(Categoria.CAJA)).contains(TURNO_ABIERTO, TURNO_CERRADO, TURNO_CERRADO_POR_SUPERVISOR);
        assertThat(porCategoria(Categoria.CONFIGURACION)).contains(PARAMETRO_MODIFICADO, CAJA_CREADA, CAJA_EDITADA);
        assertThat(porCategoria(Categoria.CATALOGO)).contains(PRODUCTO_CREADO, PRODUCTO_EDITADO, PRECIO_CAMBIADO,
                PRODUCTO_ACTIVADO, PRODUCTO_DESACTIVADO, CATALOGO_CARGADO);
        // HU-10.15 crit. 3: la generación y la exportación de reportes.
        assertThat(porCategoria(Categoria.REPORTES)).contains(REPORTE_GENERADO, REPORTE_EXPORTADO);
    }

    @Test
    void cadaAccionPerteneceAUnModuloDelBackend() {
        assertThat(AccionSensible.values()).allSatisfy(accion -> assertThat(MODULOS).contains(accion.modulo()));
    }

    @Test
    void cadaAccionImplementadaLaRegistraAlgunCasoDeUsoYLasPendientesTodaviaNo() {
        JavaClasses produccion = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("gt.supermercado.pos");

        Set<AccionSensible> usadas = produccion.stream()
                .filter(clase -> !clase.isEquivalentTo(AccionSensible.class))
                .flatMap(clase -> clase.getFieldAccessesFromSelf().stream())
                .filter(acceso -> acceso.getTargetOwner().isEquivalentTo(AccionSensible.class))
                .map(acceso -> AccionSensible.valueOf(acceso.getTarget().getName()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AccionSensible.class)));

        Set<AccionSensible> implementadas = EnumSet.allOf(AccionSensible.class);
        implementadas.removeAll(PENDIENTES.keySet());
        assertThat(usadas).as("acciones registradas por algún caso de uso").isEqualTo(implementadas);
    }

    private static Set<AccionSensible> porCategoria(Categoria categoria) {
        return Arrays.stream(AccionSensible.values()).filter(a -> a.categoria() == categoria)
                .collect(Collectors.toSet());
    }
}
