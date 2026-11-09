package gt.supermercado.pos.compartido.api;

import gt.supermercado.pos.compartido.dominio.ConflictoException;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.NoAutenticadoException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.compartido.dominio.RegistroAccesoDenegado;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    private final RegistroAccesoDenegado accesosDenegados;

    public ManejadorErrores(RegistroAccesoDenegado accesosDenegados) {
        this.accesosDenegados = accesosDenegados;
    }

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

    /**
     * Sin el permiso que exige el endpoint ({@code @PreAuthorize}): se rechaza y queda en la bitácora
     * (HU-01.11 crit. 2). Si el registro fallara, el rechazo se mantiene igual.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorApi> accesoDenegado(AccessDeniedException e, HttpServletRequest peticion) {
        try {
            accesosDenegados.registrar(peticion.getMethod(), peticion.getRequestURI());
        } catch (RuntimeException fallo) {
            log.error("No se pudo registrar el acceso denegado en la bitácora", fallo);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorApi.de("ACCESO_DENEGADO", "No tiene permiso para realizar esta acción."));
    }

    /**
     * Errores de la petición que Spring MVC ya clasifica con su código HTTP: método no admitido (405), ruta
     * inexistente (404), tipo de contenido no admitido (415), parámetro obligatorio faltante (400), etc. Antes caían
     * en {@link #inesperado} y respondían 500, como si fuera una falla del servidor.
     */
    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ErrorApi> peticionInvalida(ErrorResponseException e) {
        return respuestaDe(e);
    }

    private static ResponseEntity<ErrorApi> respuestaDe(ErrorResponse e) {
        HttpStatusCode estado = e.getStatusCode();
        ErrorApi error = switch (estado.value()) {
            case 404 -> ErrorApi.de("RECURSO_NO_ENCONTRADO", "El recurso solicitado no existe.");
            case 405 -> ErrorApi.de("METODO_NO_PERMITIDO", "La operación no está permitida en este recurso.");
            case 415 -> ErrorApi.de("CONTENIDO_NO_ADMITIDO", "El formato de los datos enviados no es válido.");
            default -> ErrorApi.de("DATOS_INVALIDOS", "Revise los datos ingresados.");
        };
        return ResponseEntity.status(estado).body(error);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception e) {
        // Algunas excepciones de Spring MVC (p. ej. HttpRequestMethodNotSupportedException) implementan ErrorResponse
        // sin heredar de ErrorResponseException: también traen su propio código HTTP.
        if (e instanceof ErrorResponse respuesta && respuesta.getStatusCode().is4xxClientError()) {
            return respuestaDe(respuesta);
        }
        log.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorApi.de("ERROR_INTERNO", "Ocurrió un error inesperado. Intente de nuevo."));
    }
}
