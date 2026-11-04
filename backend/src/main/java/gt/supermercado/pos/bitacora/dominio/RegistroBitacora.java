package gt.supermercado.pos.bitacora.dominio;

/** Almacenamiento de la bitácora. Solo agrega registros: no existe forma de editarlos ni borrarlos (HU-11.05). */
public interface RegistroBitacora {

    void agregar(EventoBitacora evento);
}
