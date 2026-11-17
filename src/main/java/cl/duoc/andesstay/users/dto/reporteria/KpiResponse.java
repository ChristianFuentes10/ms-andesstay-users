package cl.duoc.andesstay.users.dto.reporteria;

import cl.duoc.andesstay.users.model.Role;

import java.util.List;
import java.util.Map;

/**
 * Resumen consolidado de KPIs de reporteria en tiempo real.
 */
public record KpiResponse(
        long totalUsuarios,
        long usuariosActivos,
        long usuariosInactivos,
        Map<Role, Long> usuariosPorRol,
        long totalEventosKafkaProcesados,
        Map<String, Long> eventosPorTipo,
        List<ReporteriaEventoResponse> ultimosEventos
) {
}
