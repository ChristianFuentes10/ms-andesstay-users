package cl.duoc.andesstay.users.repository;

import cl.duoc.andesstay.users.model.ReporteriaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReporteriaEventoRepository extends JpaRepository<ReporteriaEvento, UUID> {

    List<ReporteriaEvento> findTop20ByOrderByCreatedAtDesc();

    long countByEventType(String eventType);
}
