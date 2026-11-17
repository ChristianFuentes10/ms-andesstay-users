package cl.duoc.andesstay.users.config;

import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.User;
import cl.duoc.andesstay.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Crea (de forma idempotente) el perfil del primer ADMIN cuando se define
 * USERS_BOOTSTRAP_ADMIN_EMAIL. Resuelve el "huevo y la gallina": sin ningun ADMIN en la
 * base nadie podria asignar roles. El perfil queda sin "sub" y se vincula automaticamente en
 * el primer GET /api/users/me (requiere COGNITO_ADMIN_ENABLED=true). El usuario debe estar
 * ademas en el grupo ADMIN de Cognito.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final String email;
    private final String nombre;

    public BootstrapAdminInitializer(UserRepository userRepository,
                                     @Value("${users.bootstrap-admin.email:}") String email,
                                     @Value("${users.bootstrap-admin.nombre:Administrador}") String nombre) {
        this.userRepository = userRepository;
        this.email = email;
        this.nombre = nombre;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            return;
        }
        userRepository.save(new User(null, normalized, nombre, null, null, EnumSet.of(Role.ADMIN)));
        log.info("Perfil ADMIN inicial creado para {}", normalized);
    }
}
