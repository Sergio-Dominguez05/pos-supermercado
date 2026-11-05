package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.seguridad.dominio.DirectorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.ResumenUsuario;
import gt.supermercado.pos.seguridad.dominio.Rol;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lista de usuarios con búsqueda y filtros (HU-01.06). */
@Service
public class ConsultaUsuarios {

    private final DirectorioUsuarios directorio;

    public ConsultaUsuarios(DirectorioUsuarios directorio) {
        this.directorio = directorio;
    }

    @Transactional(readOnly = true)
    public List<ResumenUsuario> listar(String texto, Rol rol, Boolean activo) {
        String busqueda = texto == null || texto.isBlank() ? null : texto.strip();
        return directorio.buscar(busqueda, rol, activo);
    }
}
