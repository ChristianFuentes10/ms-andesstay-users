package cl.duoc.andesstay.users.config;

import cl.duoc.andesstay.users.security.CognitoJwtAuthenticationConverter;
import cl.duoc.andesstay.users.security.RestAccessDeniedHandler;
import cl.duoc.andesstay.users.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Seguridad del microservicio de usuarios (misma politica que el BFF para /api/users):
 *
 *   POST   /api/users          -> cualquier usuario autenticado (registro; el servicio decide
 *                                  si es auto-registro o alta por ADMIN)
 *   GET    /api/users/me       -> cualquier usuario autenticado
 *   GET    /api/users          -> solo ADMIN
 *   GET    /api/users/{id}     -> ADMIN o el propio usuario (@PreAuthorize)
 *   PUT    /api/users/{id}     -> ADMIN o el propio usuario (@PreAuthorize)
 *   DELETE /api/users/{id}     -> solo ADMIN
 *   /actuator/health           -> publico
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${cors.enabled:true}")
    private boolean corsEnabled;

    private final CognitoJwtAuthenticationConverter cognitoJwtAuthenticationConverter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CognitoJwtAuthenticationConverter cognitoJwtAuthenticationConverter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          CorsConfigurationSource corsConfigurationSource) {
        this.cognitoJwtAuthenticationConverter = cognitoJwtAuthenticationConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (corsEnabled) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        } else {
            http.cors(cors -> cors.disable());
        }

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Sin esto, errores resueltos con sendError() redirigen a /error sin token (401/403 falsos).
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()

                        // Rutas especificas ANTES de las generales.
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/users").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                        // GET/PUT /api/users/{id}: capa URL solo exige autenticacion; el control fino
                        // (ADMIN o dueno del perfil) lo aplica @PreAuthorize en el controller.
                        .requestMatchers(HttpMethod.GET, "/api/users/*").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(cognitoJwtAuthenticationConverter)));

        return http.build();
    }
}
