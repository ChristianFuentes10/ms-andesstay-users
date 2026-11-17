package cl.duoc.andesstay.users.repository;

import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.User;
import cl.duoc.andesstay.users.model.UserStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filtros dinamicos opcionales para el listado de usuarios (parametro null = no filtra). */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> filter(Role role, UserStatus estado, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (role != null) {
                // distinct: un usuario con varios roles no debe duplicarse en el resultado
                query.distinct(true);
                predicates.add(cb.equal(root.join("roles", JoinType.INNER), role));
            }
            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("nombre")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("apellido"), "")), like, '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Escapa % _ \ para que el texto del usuario se busque literalmente. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
