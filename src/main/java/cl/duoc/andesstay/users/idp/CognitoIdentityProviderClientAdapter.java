package cl.duoc.andesstay.users.idp;

import cl.duoc.andesstay.users.exception.IdentityProviderException;
import cl.duoc.andesstay.users.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Integracion real con el User Pool de AWS Cognito (cognito.admin.enabled=true).
 *
 * Requiere que el rol IAM de la instancia/tarea tenga permisos cognito-idp:Admin*
 * sobre el User Pool, y que existan los 4 grupos (HUESPED, RECEPCIONISTA, ADMIN, AUDITOR).
 */
@Component
@ConditionalOnProperty(name = "cognito.admin.enabled", havingValue = "true")
public class CognitoIdentityProviderClientAdapter implements IdentityProviderClient {

    private static final Logger log = LoggerFactory.getLogger(CognitoIdentityProviderClientAdapter.class);

    private final CognitoIdentityProviderClient cognito;
    private final String userPoolId;

    public CognitoIdentityProviderClientAdapter(CognitoIdentityProviderClient cognito,
                                                @Value("${cognito.user-pool-id}") String userPoolId) {
        this.cognito = cognito;
        this.userPoolId = userPoolId;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<String> findEmailBySub(String sub) {
        try {
            AdminGetUserResponse response = cognito.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(userPoolId).username(sub).build());
            return response.userAttributes().stream()
                    .filter(a -> "email".equals(a.name()))
                    .map(AttributeType::value)
                    .findFirst();
        } catch (UserNotFoundException e) {
            return Optional.empty();
        } catch (CognitoIdentityProviderException e) {
            throw wrap("consultar el usuario en Cognito", e);
        }
    }

    @Override
    public String createUser(String email, String nombre, String apellido, String temporaryPassword) {
        try {
            List<AttributeType> attrs = new ArrayList<>();
            attrs.add(attr("email", email));
            attrs.add(attr("email_verified", "true"));
            attrs.add(attr("given_name", nombre));
            if (apellido != null && !apellido.isBlank()) {
                attrs.add(attr("family_name", apellido));
            }

            AdminCreateUserResponse created = cognito.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .userAttributes(attrs)
                    // Si el ADMIN define password no se envia el correo de invitacion.
                    .messageAction(hasText(temporaryPassword) ? MessageActionType.SUPPRESS : null)
                    .build());

            if (hasText(temporaryPassword)) {
                // permanent=false: Cognito obliga a cambiarla en el primer login.
                cognito.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                        .userPoolId(userPoolId).username(email)
                        .password(temporaryPassword).permanent(false).build());
            }

            return created.user().attributes().stream()
                    .filter(a -> "sub".equals(a.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElse(null);
        } catch (CognitoIdentityProviderException e) {
            throw wrap("crear el usuario en Cognito", e);
        }
    }

    @Override
    public void syncGroups(String email, Set<Role> roles) {
        try {
            Set<Role> current = EnumSet.noneOf(Role.class);
            cognito.adminListGroupsForUserPaginator(AdminListGroupsForUserRequest.builder()
                            .userPoolId(userPoolId).username(email).build())
                    .groups().stream()
                    .map(GroupType::groupName)
                    .forEach(name -> toRole(name).ifPresent(current::add));

            Set<Role> target = roles == null || roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);

            for (Role role : target) {
                if (!current.contains(role)) {
                    cognito.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                            .userPoolId(userPoolId).username(email).groupName(role.name()).build());
                }
            }
            for (Role role : current) {
                if (!target.contains(role)) {
                    cognito.adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest.builder()
                            .userPoolId(userPoolId).username(email).groupName(role.name()).build());
                }
            }
        } catch (CognitoIdentityProviderException e) {
            throw wrap("sincronizar los grupos en Cognito", e);
        }
    }

    @Override
    public void setEnabled(String email, boolean enabled) {
        try {
            if (enabled) {
                cognito.adminEnableUser(AdminEnableUserRequest.builder()
                        .userPoolId(userPoolId).username(email).build());
            } else {
                cognito.adminDisableUser(AdminDisableUserRequest.builder()
                        .userPoolId(userPoolId).username(email).build());
            }
        } catch (CognitoIdentityProviderException e) {
            throw wrap("cambiar el estado del usuario en Cognito", e);
        }
    }

    @Override
    public void deleteUser(String email) {
        try {
            cognito.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId).username(email).build());
        } catch (UserNotFoundException e) {
            // nada que compensar
        } catch (CognitoIdentityProviderException e) {
            log.error("No se pudo eliminar {} de Cognito durante la compensacion: {}", email, e.getMessage());
        }
    }

    private static Optional<Role> toRole(String groupName) {
        try {
            return Optional.of(Role.valueOf(groupName));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // grupos ajenos al dominio no se tocan
        }
    }

    private static AttributeType attr(String name, String value) {
        return AttributeType.builder().name(name).value(value).build();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static IdentityProviderException wrap(String action, CognitoIdentityProviderException e) {
        String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
        return new IdentityProviderException("No fue posible " + action + ": " + detail, e);
    }
}
