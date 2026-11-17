package cl.duoc.andesstay.users.model;

/**
 * Roles del sistema AndesStay. El nombre coincide EXACTAMENTE con el grupo de
 * Cognito (claim "cognito:groups") para que BFF, reservations, catalog y audit
 * los interpreten igual (authority Spring = "ROLE_" + nombre).
 */
public enum Role {
    HUESPED,
    RECEPCIONISTA,
    ADMIN,
    AUDITOR
}
