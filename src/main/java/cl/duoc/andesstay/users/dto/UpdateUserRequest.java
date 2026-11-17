package cl.duoc.andesstay.users.dto;

import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Cuerpo de PUT /api/users/{id}.
 * "roles" y "estado" son opcionales y solo se aplican si quien llama es ADMIN
 * (un usuario editando su propio perfil no puede escalar privilegios).
 */
public record UpdateUserRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String apellido,

        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "El telefono no tiene un formato valido")
        String telefono,

        Set<Role> roles,

        UserStatus estado
) {
}
