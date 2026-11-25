package mogni.system_backend.panel.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mogni.system_backend.alumno.entity.Alumno;
import mogni.system_backend.config.ZonaHoraria;
import mogni.system_backend.panel.dto.PlanesPorVencerResponse;
import mogni.system_backend.plan.entity.EstadoPlanAsignado;
import mogni.system_backend.plan.entity.PlanAsignado;
import mogni.system_backend.plan.repository.PlanAsignadoRepository;
import mogni.system_backend.progreso.repository.RegistroSesionRepository;

/**
 * Inicio del personal: planes por completar. Muestra los planes activos cuyas sesiones restantes son ≤ SESIONES_RESTANTES_AVISO.
 */
@Service
public class PlanesPorVencerService {

    public static final int SESIONES_RESTANTES_AVISO = 3;

    private static final Comparator<Alumno> POR_APELLIDO_Y_NOMBRE = Comparator
            .comparing(Alumno::getApellido, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Alumno::getNombre, String.CASE_INSENSITIVE_ORDER);

    private final PlanAsignadoRepository planAsignadoRepository;
    private final RegistroSesionRepository registroSesionRepository;

    public PlanesPorVencerService(PlanAsignadoRepository planAsignadoRepository,
                                  RegistroSesionRepository registroSesionRepository) {
        this.planAsignadoRepository = planAsignadoRepository;
        this.registroSesionRepository = registroSesionRepository;
    }

    @Transactional(readOnly = true)
    public PlanesPorVencerResponse consultar() {
        List<PlanAsignado> activos = planAsignadoRepository.findByEstado(EstadoPlanAsignado.ACTIVO).stream()
                .filter(plan -> plan.getAlumno().getCuenta().isActiva())
                .toList();
        if (activos.isEmpty()) {
            return new PlanesPorVencerResponse(ZonaHoraria.hoy(), SESIONES_RESTANTES_AVISO, List.of());
        }

        List<Long> ids = activos.stream().map(PlanAsignado::getId).toList();

        Map<Long, Long> totalPorPlan = new HashMap<>();
        planAsignadoRepository.contarSesionesTotalesPorPlan(ids)
                .forEach(row -> totalPorPlan.put((Long) row[0], ((Number) row[1]).longValue()));

        Map<Long, Long> completadasPorPlan = new HashMap<>();
        registroSesionRepository.contarCompletadasPorPlan(ids)
                .forEach(row -> completadasPorPlan.put((Long) row[0], ((Number) row[1]).longValue()));

        List<PlanesPorVencerResponse.PlanPorVencer> planes = activos.stream()
                .map(plan -> {
                    long total = totalPorPlan.getOrDefault(plan.getId(), 0L);
                    long completadas = completadasPorPlan.getOrDefault(plan.getId(), 0L);
                    int restantes = (int) Math.max(0, total - completadas);
                    return new Object[] { plan, restantes, (int) total };
                })
                .filter(arr -> (int) arr[1] <= SESIONES_RESTANTES_AVISO)
                .sorted(Comparator.<Object[], Integer>comparing(arr -> (int) arr[1])
                        .thenComparing(arr -> ((PlanAsignado) arr[0]).getAlumno(), POR_APELLIDO_Y_NOMBRE))
                .map(arr -> {
                    PlanAsignado plan = (PlanAsignado) arr[0];
                    return new PlanesPorVencerResponse.PlanPorVencer(
                            plan.getId(),
                            plan.getAlumno().getId(),
                            plan.getAlumno().getNombre(),
                            plan.getAlumno().getApellido(),
                            plan.getNombre(),
                            (int) arr[1],
                            (int) arr[2]);
                })
                .toList();

        return new PlanesPorVencerResponse(ZonaHoraria.hoy(), SESIONES_RESTANTES_AVISO, planes);
    }
}
