package cl.duoc.andesstay.users.service;

import cl.duoc.andesstay.users.dto.reporteria.KpiResponse;
import cl.duoc.andesstay.users.dto.reporteria.ReporteriaEventoResponse;
import cl.duoc.andesstay.users.model.ReporteriaEvento;
import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.UserStatus;
import cl.duoc.andesstay.users.repository.ReporteriaEventoRepository;
import cl.duoc.andesstay.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de reporteria en tiempo real y KPIs.
 * Provee la informacion consolidada solicitada para los dashboards de administracion y Grafana.
 */
@Service
public class ReporteriaService {

    private final UserRepository userRepository;
    private final ReporteriaEventoRepository reporteriaEventoRepository;

    public ReporteriaService(UserRepository userRepository,
                             ReporteriaEventoRepository reporteriaEventoRepository) {
        this.userRepository = userRepository;
        this.reporteriaEventoRepository = reporteriaEventoRepository;
    }

    @Transactional(readOnly = true)
    public KpiResponse getKpiSummary() {
        long totalUsuarios = userRepository.count();
        long activos = userRepository.countByEstado(UserStatus.ACTIVO);
        long inactivos = userRepository.countByEstado(UserStatus.INACTIVO);

        Map<Role, Long> porRol = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            long count = userRepository.countByRoleAndEstado(role, UserStatus.ACTIVO)
                    + userRepository.countByRoleAndEstado(role, UserStatus.INACTIVO);
            porRol.put(role, count);
        }

        long totalEventos = reporteriaEventoRepository.count();

        Map<String, Long> eventosPorTipo = new HashMap<>();
        for (String type : List.of("USER_REGISTERED", "USER_UPDATED", "USER_ROLE_CHANGED", "USER_DEACTIVATED", "USER_ACTIVATED")) {
            long count = reporteriaEventoRepository.countByEventType(type);
            if (count > 0) {
                eventosPorTipo.put(type, count);
            }
        }

        List<ReporteriaEventoResponse> ultimos = reporteriaEventoRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return new KpiResponse(
                totalUsuarios,
                activos,
                inactivos,
                porRol,
                totalEventos,
                eventosPorTipo,
                ultimos
        );
    }

    @Transactional(readOnly = true)
    public List<ReporteriaEventoResponse> getRecentEvents() {
        return reporteriaEventoRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ReporteriaEventoResponse mapToResponse(ReporteriaEvento entity) {
        return new ReporteriaEventoResponse(
                entity.getId(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getPayload(),
                entity.getCreatedAt()
        );
    }
}
