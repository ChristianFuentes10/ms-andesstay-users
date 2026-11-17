package cl.duoc.andesstay.users.exception;

/** Violacion de una regla de negocio (p.ej. dejar el sistema sin administradores). Responde 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
