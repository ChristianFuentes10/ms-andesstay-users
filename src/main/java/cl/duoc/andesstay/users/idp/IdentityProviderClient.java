package cl.duoc.andesstay.users.idp;

import cl.duoc.andesstay.users.model.Role;

import java.util.Optional;
import java.util.Set;

/**
 * Puerto hacia el proveedor de identidad (AWS Cognito). Aisla al dominio del SDK
 * de AWS y permite ejecutar el microservicio sin Cognito (implementacion no-op)
 * en desarrollo y en tests.
 *
 * Las operaciones se identifican por email porque el User Pool inicia sesion con email.
 */
public interface IdentityProviderClient {

    /** true si hay integracion real con Cognito. */
    boolean isEnabled();

    /** Email autoritativo (verificado por Cognito) del usuario con ese "sub", si existe. */
    Optional<String> findEmailBySub(String sub);

    /**
     * Crea la identidad en el User Pool y devuelve su "sub".
     * Si temporaryPassword es null/vacia, Cognito envia la invitacion por email.
     */
    String createUser(String email, String nombre, String apellido, String temporaryPassword);

    /** Deja los grupos de Cognito del usuario exactamente igual al conjunto de roles. */
    void syncGroups(String email, Set<Role> roles);

    /** Habilita o deshabilita el login del usuario en Cognito. */
    void setEnabled(String email, boolean enabled);

    /** Elimina la identidad (compensacion cuando falla el guardado local tras crearla). */
    void deleteUser(String email);
}
