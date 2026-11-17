package cl.duoc.andesstay.users.service;

import org.springframework.security.core.Authentication;

/**
 * Quien invoca la operacion: el "sub" del token y si tiene rol ADMIN.
 * Desacopla el servicio de Spring Security (mas facil de testear).
 */
public record CurrentUser(String sub, boolean admin) {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    public static CurrentUser from(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
        return new CurrentUser(authentication.getName(), admin);
    }
}
