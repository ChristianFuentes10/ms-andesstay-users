package cl.duoc.andesstay.users.idp;

import cl.duoc.andesstay.users.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Implementacion por defecto (cognito.admin.enabled=false): solo gestiona la base local.
 * Util para desarrollo local y tests; en AWS se activa CognitoIdentityProviderClient.
 */
@Component
@ConditionalOnProperty(name = "cognito.admin.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpIdentityProviderClient implements IdentityProviderClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpIdentityProviderClient.class);

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Optional<String> findEmailBySub(String sub) {
        return Optional.empty();
    }

    @Override
    public String createUser(String email, String nombre, String apellido, String temporaryPassword) {
        log.debug("Cognito deshabilitado: no se crea identidad para {}", email);
        return null;
    }

    @Override
    public void syncGroups(String email, Set<Role> roles) {
        log.debug("Cognito deshabilitado: no se sincronizan grupos de {} ({})", email, roles);
    }

    @Override
    public void setEnabled(String email, boolean enabled) {
        log.debug("Cognito deshabilitado: no se cambia el estado de {}", email);
    }

    @Override
    public void deleteUser(String email) {
        log.debug("Cognito deshabilitado: no se elimina identidad de {}", email);
    }
}
