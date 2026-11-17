package cl.duoc.andesstay.users.controller;

import cl.duoc.andesstay.users.dto.reporteria.KpiResponse;
import cl.duoc.andesstay.users.dto.reporteria.ReporteriaEventoResponse;
import cl.duoc.andesstay.users.service.ReporteriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de reporteria en tiempo real y KPIs.
 * Requerimiento de la rubrica:
 * "El rol admin tendra acceso a la reporteria/KPI."
 */
@RestController
@RequestMapping("/api/reporteria")
@Tag(name = "Reporteria & KPI", description = "Servicios de reporteria en tiempo real derivados de eventos Kafka (Solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class ReporteriaController {

    private final ReporteriaService reporteriaService;

    public ReporteriaController(ReporteriaService reporteriaService) {
        this.reporteriaService = reporteriaService;
    }

    @GetMapping("/kpi")
    @Operation(summary = "Obtener resumen de KPIs", description = "Retorna metricas consolidadas en tiempo real: usuarios, roles y eventos")
    public KpiResponse getKpiSummary() {
        return reporteriaService.getKpiSummary();
    }

    @GetMapping("/eventos")
    @Operation(summary = "Listar eventos procesados", description = "Retorna la traza de eventos de Kafka procesados por el consumidor")
    public List<ReporteriaEventoResponse> getRecentEvents() {
        return reporteriaService.getRecentEvents();
    }
}
