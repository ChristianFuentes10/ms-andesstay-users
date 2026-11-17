package cl.duoc.andesstay.users.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }

    public static UserNotFoundException byId(Object id) {
        return new UserNotFoundException("No se encontro el usuario con id " + id);
    }

    public static UserNotFoundException bySub(String sub) {
        return new UserNotFoundException(
                "El usuario autenticado aun no tiene perfil. Registrelo con POST /api/users");
    }
}
