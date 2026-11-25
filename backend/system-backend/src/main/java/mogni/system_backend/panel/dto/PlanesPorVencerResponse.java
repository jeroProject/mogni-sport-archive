package mogni.system_backend.panel.dto;

import java.time.LocalDate;
import java.util.List;


/**
 * Inicio del personal: planes activos de alumnos que están por completarse (≤ sesionesRestantesAviso sesiones restantes).
 */
public record PlanesPorVencerResponse(LocalDate fecha, int sesionesRestantesAviso, List<PlanPorVencer> planes) {

    public record PlanPorVencer(Long planId, Long alumnoId, String nombreAlumno, String apellidoAlumno,
                                String nombre, int sesionesRestantes, int sesionesTotales) {
    }
}
