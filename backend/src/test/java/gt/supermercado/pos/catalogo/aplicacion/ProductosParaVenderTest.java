package gt.supermercado.pos.catalogo.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.catalogo.dominio.CodigoNoEncontrado;
import gt.supermercado.pos.catalogo.dominio.FiltroProductos;
import gt.supermercado.pos.catalogo.dominio.PaginaProductos;
import gt.supermercado.pos.catalogo.dominio.Producto;
import gt.supermercado.pos.catalogo.dominio.RepositorioCodigosNoEncontrados;
import gt.supermercado.pos.catalogo.dominio.RepositorioProductos;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-03.09 Excluir productos inactivos de la venta: la regla del servidor que usará la venta (HU-06.01) al agregar
 * un producto escaneado. La venta todavía no existe, así que se prueba directamente sobre el caso de uso.
 */
class ProductosParaVenderTest {

    private final List<Producto> catalogo = new ArrayList<>();
    private final List<String> noEncontrados = new ArrayList<>();
    private final GestionProductos gestion = new GestionProductos(new ProductosEnMemoria(), new CodigosEnMemoria(),
            new Bitacora(evento -> { }));
    private final SesionActiva cajero = new SesionActiva(1, 20, "ana", "Ana López", Rol.CAJERO, "Cajero", false,
            Set.of("PRODUCTO_CONSULTAR", "VENTA_REGISTRAR"), 5L, "Caja 1");

    @Test
    void unProductoActivoSePuedeVender() {
        catalogo.add(producto("7401", "Leche entera 1 L", true));

        assertThat(gestion.paraVender("7401", cajero).descripcion()).isEqualTo("Leche entera 1 L");
    }

    @Test
    void unProductoInactivoEscaneadoNoEstaDisponibleParaLaVenta() {   // crit. 2
        catalogo.add(producto("7402", "Galleta descontinuada", false));

        assertThatThrownBy(() -> gestion.paraVender(" 7402 ", cajero))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Galleta descontinuada (7402) no está disponible para la venta.")
                .extracting(e -> ((NegocioException) e).getCodigo()).isEqualTo("PRODUCTO_INACTIVO");
        assertThat(noEncontrados).as("un inactivo existe: no es un código no encontrado").isEmpty();
    }

    @Test
    void unCodigoInexistenteSigueSiendoNoEncontrado() {   // HU-03.08
        assertThatThrownBy(() -> gestion.paraVender("9999", cajero)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(noEncontrados).containsExactly("9999");
    }

    private static Producto producto(String codigo, String descripcion, boolean activo) {
        return new Producto(1, codigo, descripcion, new BigDecimal("10.00"), activo, OffsetDateTime.now());
    }

    /** Doble en memoria con lo necesario para buscar por código. */
    private final class ProductosEnMemoria implements RepositorioProductos {

        @Override
        public Optional<Producto> buscarPorCodigo(String codigoBarras) {
            return catalogo.stream().filter(p -> p.codigoBarras().equals(codigoBarras)).findFirst();
        }

        @Override
        public Optional<Producto> buscarPorId(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaginaProductos buscar(FiltroProductos filtro) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Producto> buscarActivosPorDescripcion(String texto, int limite) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long crear(String codigoBarras, String descripcion, BigDecimal precio) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void actualizar(long id, String descripcion, BigDecimal precio) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cambiarActivo(long id, boolean activo) {
            throw new UnsupportedOperationException();
        }
    }

    private final class CodigosEnMemoria implements RepositorioCodigosNoEncontrados {

        @Override
        public void registrar(String codigo, Long cajaId, long usuarioId) {
            noEncontrados.add(codigo);
        }

        @Override
        public List<CodigoNoEncontrado> pendientes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resolver(String codigo) {
            throw new UnsupportedOperationException();
        }
    }
}
