package cl.duoc.andesstay.users.model;

/**
 * Estado del ciclo de vida de un usuario. La baja es logica (INACTIVO) para
 * conservar la trazabilidad de reservas y auditoria asociadas al usuario.
 */
public enum UserStatus {
    ACTIVO,
    INACTIVO
}
