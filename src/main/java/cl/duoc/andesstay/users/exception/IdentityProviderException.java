package cl.duoc.andesstay.users.exception;

/** Fallo al comunicarse con el proveedor de identidad (Cognito). Responde 502. */
public class IdentityProviderException extends RuntimeException {

    public IdentityProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
