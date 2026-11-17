package cl.duoc.andesstay.users.dto;

import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** DTO de salida: nunca se expone la entidad JPA. */
public record UserResponse(
        UUID id,
        String email,
        String nombre,
        String apellido,
        String telefono,
        String estado,
        Set<Role> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNombre(),
                user.getApellido(),
                user.getTelefono(),
                user.getEstado().name(),
                new TreeSet<>(user.getRoles()),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
