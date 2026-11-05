package gt.supermercado.pos.bitacora.dominio;

/** Recorre la cadena de hashes de la bitácora y reporta lo que no cuadra (HU-11.05 crit. 3). */
public interface VerificadorCadena {

    ResultadoVerificacion verificar();
}
