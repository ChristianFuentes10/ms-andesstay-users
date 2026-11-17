package cl.duoc.andesstay.users.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CognitoSecurityComponentsTest {

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("sub-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }

    // --- client_id ---

    @Test
    void audienceValidator_acceptsExpectedClientId() {
        assertFalse(new CognitoAudienceValidator("abc").validate(jwt(Map.of("client_id", "abc"))).hasErrors());
    }

    @Test
    void audienceValidator_rejectsOtherOrMissingClientId() {
        CognitoAudienceValidator v = new CognitoAudienceValidator("abc");
        assertTrue(v.validate(jwt(Map.of("client_id", "zzz"))).hasErrors());
        assertTrue(v.validate(jwt(Map.of("foo", "bar"))).hasErrors());
    }

    // --- token_use ---

    @Test
    void tokenUseValidator_onlyAcceptsAccessTokens() {
        CognitoTokenUseValidator v = new CognitoTokenUseValidator();
        assertFalse(v.validate(jwt(Map.of("token_use", "access"))).hasErrors());
        assertTrue(v.validate(jwt(Map.of("token_use", "id"))).hasErrors());
        assertTrue(v.validate(jwt(Map.of("foo", "bar"))).hasErrors());
    }

    // --- cognito:groups -> ROLE_* ---

    private static CognitoJwtAuthenticationConverter converter() {
        CognitoJwtAuthenticationConverter c = new CognitoJwtAuthenticationConverter();
        ReflectionTestUtils.setField(c, "defaultRole", "HUESPED");
        return c;
    }

    private static Set<String> authorities(AbstractAuthenticationToken t) {
        return t.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void converter_mapsGroupsToRolesAndUsesSubAsName() {
        AbstractAuthenticationToken token = converter().convert(
                jwt(Map.of("cognito:groups", List.of("admin", "AUDITOR"))));

        assertEquals(Set.of("ROLE_ADMIN", "ROLE_AUDITOR"), authorities(token));
        assertEquals("sub-1", token.getName());
    }

    @Test
    void converter_withoutGroups_assignsDefaultRole() {
        assertEquals(Set.of("ROLE_HUESPED"), authorities(converter().convert(jwt(Map.of()))));
    }
}
