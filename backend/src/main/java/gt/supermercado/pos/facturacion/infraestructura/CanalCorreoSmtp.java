package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.dominio.CanalCorreo;
import gt.supermercado.pos.facturacion.dominio.ConexionCorreo;
import gt.supermercado.pos.facturacion.dominio.CorreoNoEnviadoException;
import gt.supermercado.pos.facturacion.dominio.MensajeCorreo;
import gt.supermercado.pos.facturacion.dominio.SeguridadCorreo;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * HU-08.15: envía por SMTP con el servidor que configuró el Administrador. Arma la conexión en cada envío para usar
 * siempre la configuración vigente, con el tiempo límite configurado para conectarse, leer y escribir.
 */
@Component
@ConditionalOnProperty(name = "pos.correo.modo", havingValue = "smtp")
class CanalCorreoSmtp implements CanalCorreo {

    @Override
    public void enviar(ConexionCorreo conexion, MensajeCorreo mensaje) {
        JavaMailSenderImpl remitente = remitente(conexion);
        try {
            MimeMessage correo = remitente.createMimeMessage();
            MimeMessageHelper armado = new MimeMessageHelper(correo, true, StandardCharsets.UTF_8.name());
            armado.setFrom(conexion.remitente(), conexion.nombreRemitente() == null ? conexion.remitente()
                    : conexion.nombreRemitente());
            armado.setTo(mensaje.destinatario());
            armado.setSubject(mensaje.asunto());
            armado.setText(mensaje.texto(), false);
            for (MensajeCorreo.Adjunto adjunto : mensaje.adjuntos()) {
                armado.addAttachment(adjunto.nombre(), new ByteArrayResource(adjunto.contenido()), adjunto.tipo());
            }
            remitente.send(correo);
        } catch (MailAuthenticationException e) {
            throw new CorreoNoEnviadoException("El servidor de correo rechazó el usuario o la contraseña.", e);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            throw new CorreoNoEnviadoException("No se pudo enviar el correo: " + causa(e), e);
        }
    }

    private static JavaMailSenderImpl remitente(ConexionCorreo conexion) {
        JavaMailSenderImpl remitente = new JavaMailSenderImpl();
        remitente.setHost(conexion.servidor());
        remitente.setPort(conexion.puerto());
        remitente.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties propiedades = remitente.getJavaMailProperties();
        String tiempo = Integer.toString(conexion.timeoutMs());
        String protocolo = conexion.seguridad() == SeguridadCorreo.SSL ? "smtps" : "smtp";
        remitente.setProtocol(protocolo);
        propiedades.put("mail." + protocolo + ".connectiontimeout", tiempo);
        propiedades.put("mail." + protocolo + ".timeout", tiempo);
        propiedades.put("mail." + protocolo + ".writetimeout", tiempo);
        if (conexion.seguridad() == SeguridadCorreo.STARTTLS) {
            propiedades.put("mail.smtp.starttls.enable", "true");
            propiedades.put("mail.smtp.starttls.required", "true");
        }
        if (conexion.usuario() != null) {
            remitente.setUsername(conexion.usuario());
            remitente.setPassword(conexion.secreto());
            propiedades.put("mail." + protocolo + ".auth", "true");
        }
        return remitente;
    }

    /** El motivo más concreto de la falla, sin repetir la clase de la excepción. */
    private static String causa(Exception e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String mensaje = raiz.getMessage();
        return mensaje == null || mensaje.isBlank() ? raiz.getClass().getSimpleName() : mensaje;
    }
}
