package gt.supermercado.pos.configuracion.dominio;

import java.util.List;
import java.util.Optional;

/** Las impresoras de la red (HU-12.08). */
public interface RepositorioImpresoras {

    List<Impresora> listar();

    Optional<Impresora> buscarPorId(long id);

    /** La impresora asignada a la caja, si tiene. */
    Optional<Impresora> deLaCaja(long cajaId);

    long crear(String nombre, String host, int puerto, int anchoMm, int copiasFactura, int copiasCierre,
               long usuarioId);

    void actualizar(long id, String nombre, String host, int puerto, int anchoMm, int copiasFactura, int copiasCierre,
                    long usuarioId);
}
