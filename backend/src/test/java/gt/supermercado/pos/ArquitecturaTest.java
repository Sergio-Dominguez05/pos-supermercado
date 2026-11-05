package gt.supermercado.pos;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de dependencias entre módulos y capas (Architecture.md §4): un módulo solo usa la capa
 * {@code aplicacion} (y los tipos de {@code dominio}) de otro, nunca su {@code api} ni su {@code infraestructura}.
 */
@AnalyzeClasses(packages = "gt.supermercado.pos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final String BASE = "gt.supermercado.pos.";

    private static final List<String> MODULOS = List.of(
            "compartido", "seguridad", "catalogo", "impuestos", "clientes", "ventas", "pagos",
            "facturacion", "caja", "reportes", "bitacora", "configuracion", "impresion");

    @ArchTest
    static final ArchRule elDominioNoDependeDeApiNiInfraestructura = noClasses()
            .that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infraestructura..", "org.springframework..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule impuestosEsCalculoPuro = classes()
            .that().resideInAPackage(BASE + "impuestos..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", BASE + "impuestos..", BASE + "compartido.dominio..")
            .allowEmptyShould(true);

    /**
     * HU-01.11 crit. 3: los permisos se validan en el servidor. Cada endpoint declara el suyo; un endpoint nuevo
     * sin {@code @PreAuthorize} hace fallar el build. Excepciones: el estado del sistema (público) y la
     * autenticación (iniciar y cerrar sesión, ver la sesión y cambiar la credencial propia: cualquier usuario).
     */
    @ArchTest
    static final ArchRule cadaEndpointDeclaraSuPermiso = methods()
            .that().areMetaAnnotatedWith(RequestMapping.class)
            .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().areDeclaredInClassesThat().doNotHaveSimpleName("SistemaController")
            .and().areDeclaredInClassesThat().doNotHaveSimpleName("AutenticacionController")
            .should().beAnnotatedWith(PreAuthorize.class)
            .because("los permisos se validan en el servidor, no solo en la interfaz (HU-01.11)");

    @ArchTest
    static void apiEInfraestructuraSonPrivadasDeCadaModulo(JavaClasses clases) {
        for (String modulo : MODULOS) {
            noClasses()
                    .that().resideOutsideOfPackage(BASE + modulo + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(BASE + modulo + ".api..", BASE + modulo + ".infraestructura..")
                    .allowEmptyShould(true)
                    .because("otros módulos deben usar la capa aplicacion de '" + modulo + "'")
                    .check(clases);
        }
    }
}
