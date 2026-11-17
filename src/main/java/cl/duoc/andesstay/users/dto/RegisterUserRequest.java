package cl.duoc.andesstay.users.dto;

import cl.duoc.andesstay.users.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Cuerpo de POST /api/users.
 *
 * - Auto-registro (usuario autenticado sin ser ADMIN): se ignoran "roles" y "password";
 *   el perfil queda vinculado al "sub" del token con rol HUESPED.
 * - Alta por ADMIN: puede indicar "roles" y una "password" temporal (si la integracion
 *   con Cognito esta habilitada, se crea tambien la identidad en el User Pool).
 */
public record RegisterUserRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String apellido,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato valido")
        @Size(max = 254, message = "El email no puede superar los 254 caracteres")
        String email,

        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "El telefono no tiene un formato valido")
        String telefono,

        @Size(min = 8, max = 128, message = "La password debe tener entre 8 y 128 caracteres")
        String password,

        Set<Role> roles
) {
}
