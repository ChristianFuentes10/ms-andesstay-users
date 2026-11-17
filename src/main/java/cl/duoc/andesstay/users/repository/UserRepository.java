package cl.duoc.andesstay.users.repository;

import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.User;
import cl.duoc.andesstay.users.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByCognitoSub(String cognitoSub);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCognitoSub(String cognitoSub);

    long countByEstado(UserStatus estado);

    /** Cantidad de usuarios con un rol y estado dado; se usa para no dejar el sistema sin ADMIN activo. */
    @Query("select count(u) from User u join u.roles r where r = :role and u.estado = :estado")
    long countByRoleAndEstado(@Param("role") Role role, @Param("estado") UserStatus estado);
}
