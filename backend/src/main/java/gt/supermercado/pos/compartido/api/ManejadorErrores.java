package gt.supermercado.pos.compartido.api;

import gt.supermercado.pos.compartido.dominio.ConflictoException;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.NoAutenticadoException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(NegocioException.class)
    ResponseEntity<ErrorApi> negocio(NegocioException e) {
        return ResponseEntity.status(422).body(ErrorApi.de(e.getCodigo(), e.getMessage()));
    }

    @ExceptionHandler(ConflictoException.class)
    ResponseEntity<ErrorApi> conflicto(ConflictoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorApi.de(e.getCodigo(), e.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ErrorApi> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorApi.de(e.getCodigo(), e.getMessage()));
    }

    @ExceptionHandler(NoAutenticadoException.class)
    ResponseEntity<ErrorApi> noAutenticado(NoAutenticadoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorApi.de(e.getCodigo(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorApi> validacion(MethodArgumentNotValidException e) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorApi("DATOS_INVALIDOS", "Revise los datos ingresados.", detalles));
    }

    /** Un parámetro de la URL con un valor que no corresponde a su tipo, por ejemplo {@code ?rol=GERENTE}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorApi> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorApi("DATOS_INVALIDOS", "Revise los datos ingresados.", List.of(e.getName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorApi> cuerpoIlegible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorApi.de("DATOS_INVALIDOS", "Revise los datos ingresados."));
    }

    /** Sin el permiso que exige el endpoint ({@code @PreAuthorize}). Registrarlo en la bitácora es HU-11.04. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorApi> accesoDenegado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorApi.de("ACCESO_DENEGADO", "No tiene permiso para realizar esta acción."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception e) {
        log.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorApi.de("ERROR_INTERNO", "Ocurrió un error inesperado. Intente de nuevo."));
    }
}
