package mogni.system_backend.progreso.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mogni.system_backend.progreso.entity.RegistroSesion;

public interface RegistroSesionRepository extends JpaRepository<RegistroSesion, Long> {

    /** Todas, también las que están en curso. */
    List<RegistroSesion> findByPlanId(Long planId);

    /** HU-269: el registro de una sesión en una semana; hay como mucho uno (restricción única). */
    Optional<RegistroSesion> findByPlanIdAndSemanaAndNumeroSesion(Long planId, int semana, int numeroSesion);

    /** Las sesiones de una semana del plan que el alumno empezó o registró. */
    List<RegistroSesion> findByPlanIdAndSemana(Long planId, int semana);

    /** Una sesión registrada o en curso, solo si es de un plan de ese alumno. */
    Optional<RegistroSesion> findByIdAndPlanAlumnoId(Long id, Long alumnoId);

    /**
     * Solo las finalizadas, agrupables por semana: de la semana más reciente a la más antigua y, dentro de cada una,
     * por fecha.
     */
    List<RegistroSesion> findByPlanIdAndCompletadaTrueOrderBySemanaDescFechaDescIdDesc(Long planId);

    /** Las completadas de un plan. */
    List<RegistroSesion> findByPlanIdAndCompletadaTrue(Long planId);

    /** Batch: sesiones completadas por plan y semana, para calcular semanaActual de varios planes a la vez. */
    @Query("SELECT r.plan.id, r.semana, COUNT(r) FROM RegistroSesion r "
            + "WHERE r.plan.id IN :planIds AND r.completada = true GROUP BY r.plan.id, r.semana")
    List<Object[]> contarCompletadasPorPlanYSemana(@Param("planIds") Collection<Long> planIds);

    /** Batch: total de sesiones completadas por plan. */
    @Query("SELECT r.plan.id, COUNT(r) FROM RegistroSesion r "
            + "WHERE r.plan.id IN :planIds AND r.completada = true GROUP BY r.plan.id")
    List<Object[]> contarCompletadasPorPlan(@Param("planIds") Collection<Long> planIds);
}
