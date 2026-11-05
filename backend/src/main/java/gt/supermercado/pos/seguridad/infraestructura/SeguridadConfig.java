package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.aplicacion.ConsultaSesion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * El estado del sistema y el inicio de sesión son públicos; todo lo demás exige una sesión válida
 * (cookie {@link CookieSesion}, tabla {@code sesion}). Cada endpoint declara además su permiso con
 * {@code @PreAuthorize("hasAuthority('...')")} (Architecture.md §7.3).
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, CookieSesion cookie, ConsultaSesion consultaSesion)
            throws Exception {
        return http
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers("/api/sistema/**", "/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/login-pin").permitAll()
                        .anyRequest().authenticated())
                // La cookie de sesión es SameSite=Strict, así que el navegador no la envía en peticiones
                // iniciadas desde otro sitio; eso cubre CSRF sin un token adicional.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new FiltroSesion(cookie, consultaSesion), AnonymousAuthenticationFilter.class)
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /** Argon2id para contraseñas y PIN (Architecture.md §3). */
    @Bean
    PasswordEncoder codificadorCredenciales() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
