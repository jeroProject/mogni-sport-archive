package mogni.system_backend.plan.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mogni.system_backend.plan.entity.EstadoPlanAsignado;
import mogni.system_backend.plantilla.dto.SemanaResponse;
import mogni.system_backend.plantilla.entity.NivelPlantilla;
import mogni.system_backend.plantilla.entity.ObjetivoPlantilla;

@Getter
@AllArgsConstructor
public class PlanAsignadoResponse {
    private Long id;
    private Long alumnoId;
    /** Nombre y apellido. */
    private String alumnoNombre;
    private String nombre;
    private NivelPlantilla nivel;
    private ObjetivoPlantilla objetivo;
    private EstadoPlanAsignado estado;
    /** De qué plantilla se copió; null si se armó desde cero. */
    private Long plantillaOrigenId;
    /** Fecha de inicio del plan: el primer día de la semana 1. */
    private LocalDate fechaAsignacion;
    /** Cuándo terminó. Null mientras está activo. */
    private LocalDate fechaFinalizacion;
    /** Fecha fin estimada original (informativa, no controla vigencia). */
    private LocalDate fechaFin;
    private int cantidadSemanas;
    /**
     * La semana en curso, o null si el plan ya no está vigente. Al modificar, las anteriores son de solo lectura y
     * esta no se puede eliminar.
     */
    private Integer semanaEnCurso;
    /** Mismo formato que las semanas de una plantilla. */
    private List<SemanaResponse> semanas;
    /** Las sesiones de la semana en curso que el alumno ya empezó o registró: en el editor son de solo lectura. */
    private List<SesionConRegistroResponse> sesionesConRegistro;
}
