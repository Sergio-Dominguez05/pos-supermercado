package gt.supermercado.pos;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL real en Docker para las pruebas de integración. Misma versión que docker-compose.yml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withInitScript("db/roles-test.sql");
    }

    /**
     * HU-01.15: el Administrador inicial se crea con contraseña temporal y, mientras no la cambie, el servidor solo le
     * permite cambiarla. Las pruebas actúan como un administrador que ya la cambió; las que prueban el cambio
     * obligatorio usan sus propios usuarios con credencial temporal.
     */
    @Bean
    ApplicationListener<ApplicationReadyEvent> administradorInicialSinCredencialTemporal(JdbcTemplate jdbc) {
        return evento -> jdbc.update("UPDATE usuario SET credencial_temporal = false WHERE username = 'admin'");
    }
}
