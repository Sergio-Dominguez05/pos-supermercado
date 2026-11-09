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
            entry(PERMISOS_MODIFICADOS, "HU-01.10"),
            entry(LINEA_VENTA_ELIMINADA, "HU-06.07"),
            entry(VENTA_CANCELADA, "HU-06.14"),
            entry(FACTURA_ANULADA, "HU-08.10"),
            entry(FACTURA_REIMPRESA, "HU-08.12"),
            entry(FACTURA_RECHAZADA_CERTIFICADOR, "HU-08.06"),
            entry(PAGO_TARJETA_ELIMINADO, "HU-07.06"),
            entry(TURNO_ABIERTO, "HU-09.01"),
            entry(TURNO_CERRADO, "HU-09.08"),
            entry(TURNO_CERRADO_POR_SUPERVISOR, "HU-09.12"),
            entry(PARAMETRO_MODIFICADO, "HU-12.01, 12.02, 12.03, 12.07, 12.08 y 12.13"),
            entry(PRODUCTO_EDITADO, "HU-03.02"),
            entry(PRECIO_CAMBIADO, "HU-03.02"),
            entry(PRODUCTO_ACTIVADO, "HU-03.03"),
            entry(PRODUCTO_DESACTIVADO, "HU-03.03"),
            entry(CATALOGO_CARGADO, "HU-03.05"));

    private static final Set<String> MODULOS = Set.of(
            "seguridad", "catalogo", "ventas", "pagos", "facturacion", "caja", "configuracion");

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
