package cl.duoc.andesstay.users.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Valida que el token sea un access token de Cognito (token_use = "access"). Rechaza id tokens. */
public class CognitoTokenUseValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ERROR = new OAuth2Error(
            "invalid_token", "Se requiere un access token (token_use=access)", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if ("access".equals(jwt.getClaimAsString("token_use"))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERROR);
    }
}
