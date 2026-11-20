package gt.supermercado.pos.impresion.dominio;

/** HU-08.12: el registro de cada reimpresión de una factura (Architecture.md §5.2, tabla {@code reimpresion}). */
public interface RepositorioReimpresiones {

    void registrar(long facturaId, long usuarioId, Long cajaId);
}
