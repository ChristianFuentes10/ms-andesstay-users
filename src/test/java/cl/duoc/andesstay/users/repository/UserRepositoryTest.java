package cl.duoc.andesstay.users.repository;

import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.User;
import cl.duoc.andesstay.users.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de persistencia sobre H2 (usa el application.properties de test). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @BeforeEach
    void setUp() {
        repository.save(new User("s1", "ana@test.com", "Ana", "Soto", null, EnumSet.of(Role.HUESPED)));
        repository.save(new User("s2", "bob@test.com", "Bob", "Diaz", null, EnumSet.of(Role.ADMIN, Role.AUDITOR)));
        User inactive = new User("s3", "eve@test.com", "Eve", null, null, EnumSet.of(Role.ADMIN));
        inactive.setEstado(UserStatus.INACTIVO);
        repository.saveAndFlush(inactive);
    }

    @Test
    void save_generatesIdAndTimestamps() {
        User u = repository.findByEmailIgnoreCase("ANA@test.com").orElseThrow();
        assertNotNull(u.getId());
        assertNotNull(u.getCreatedAt());
        assertEquals(Set.of(Role.HUESPED), u.getRoles());
    }

    @Test
    void emailMustBeUnique() {
        assertThrows(DataIntegrityViolationException.class, () ->
                repository.saveAndFlush(new User("s9", "ana@test.com", "Otra", null, null, EnumSet.of(Role.HUESPED))));
    }

    @Test
    void countByRoleAndEstado_countsOnlyActiveAdmins() {
        assertEquals(1, repository.countByRoleAndEstado(Role.ADMIN, UserStatus.ACTIVO));
        assertEquals(1, repository.countByRoleAndEstado(Role.ADMIN, UserStatus.INACTIVO));
    }

    @Test
    void specification_filtersByRole_withoutDuplicates() {
        List<User> admins = repository.findAll(UserSpecifications.filter(Role.ADMIN, null, null),
                PageRequest.of(0, 10)).getContent();
        assertEquals(2, admins.size());
    }

    @Test
    void specification_filtersByStatusAndFreeText() {
        assertEquals(1, repository.findAll(UserSpecifications.filter(null, UserStatus.INACTIVO, null),
                PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, repository.findAll(UserSpecifications.filter(null, null, "DIA"),
                PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, repository.findAll(UserSpecifications.filter(null, null, "%"),
                PageRequest.of(0, 10)).getTotalElements(), "los comodines se buscan literalmente");
    }
}
