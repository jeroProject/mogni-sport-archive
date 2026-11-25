package mogni.system_backend.plan.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mogni.system_backend.alumno.entity.Alumno;
import mogni.system_backend.alumno.repository.AlumnoRepository;
import mogni.system_backend.config.ZonaHoraria;
import mogni.system_backend.ejercicio.entity.Ejercicio;
import mogni.system_backend.ejercicio.repository.EjercicioRepository;
import mogni.system_backend.exception.ConflictoException;
import mogni.system_backend.exception.RecursoNoEncontradoException;
import mogni.system_backend.exception.RegistroInvalidoException;
import mogni.system_backend.plan.dto.AsignarPlanDesdeCeroRequest;
import mogni.system_backend.plan.dto.AsignarPlanDesdePlantillaRequest;
import mogni.system_backend.plan.dto.PlanAsignadoResponse;
import mogni.system_backend.plan.dto.PlanRequest;
import mogni.system_backend.plan.dto.SesionConRegistroResponse;
import mogni.system_backend.plan.entity.EjercicioSesionPlan;
import mogni.system_backend.plan.entity.EstadoPlanAsignado;
import mogni.system_backend.plan.entity.PlanAsignado;
import mogni.system_backend.plan.entity.SemanaPlan;
import mogni.system_backend.plan.entity.SesionPlan;
import mogni.system_backend.plan.repository.PlanAsignadoRepository;
import mogni.system_backend.plantilla.dto.EjercicioSesionResponse;
import mogni.system_backend.plantilla.dto.SemanaRequest;
import mogni.system_backend.plantilla.dto.SemanaResponse;
import mogni.system_backend.plantilla.dto.SesionPlantillaResponse;
import mogni.system_backend.plantilla.dto.SesionRequest;
import mogni.system_backend.plantilla.entity.PlantillaPlan;
import mogni.system_backend.plantilla.repository.PlantillaPlanRepository;
import mogni.system_backend.plantilla.service.CatalogoEjercicios;
import mogni.system_backend.plantilla.service.ReglasSemanas;
import mogni.system_backend.progreso.entity.RegistroSesion;
import mogni.system_backend.progreso.repository.RegistroSesionRepository;

/**
 * HU-266: asignar un plan de entrenamiento a un alumno, copiando una plantilla o armándolo desde cero. El plan dura
 * las semanas que tiene y cada semana tiene sus propias sesiones. Un alumno tiene como mucho un plan activo: para
 * asignarle otro hay que finalizar el actual.
 * Las semanas avanzan por completitud de sesiones, no por fecha. Un plan se finaliza al completar todas las sesiones
 * o cuando el entrenador lo cierra a mano.
 */
@Service
public class PlanAsignadoService {

    private final PlanAsignadoRepository planRepository;
    private final AlumnoRepository alumnoRepository;
    private final PlantillaPlanRepository plantillaRepository;
    private final RegistroSesionRepository registroRepository;
    private final CatalogoEjercicios catalogoEjercicios;

    public PlanAsignadoService(PlanAsignadoRepository planRepository, AlumnoRepository alumnoRepository,
                               PlantillaPlanRepository plantillaRepository, EjercicioRepository ejercicioRepository,
                               RegistroSesionRepository registroRepository) {
        this.planRepository = planRepository;
        this.alumnoRepository = alumnoRepository;
        this.plantillaRepository = plantillaRepository;
        this.registroRepository = registroRepository;
        this.catalogoEjercicios = new CatalogoEjercicios(ejercicioRepository);
    }

    /** El plan activo del alumno, si tiene. Lo usa la pantalla para avisar antes de armar uno nuevo. */
    @Transactional(readOnly = true)
    public Optional<PlanAsignadoResponse> obtenerPlanActivo(Long alumnoId) {
        return obtenerPlanActivo(alumnoId, ZonaHoraria.hoy());
    }

    public Optional<PlanAsignadoResponse> obtenerPlanActivo(Long alumnoId, LocalDate hoy) {
        if (!alumnoRepository.existsById(alumnoId)) {
            throw new RecursoNoEncontradoException("No existe un alumno con id " + alumnoId);
        }
        return buscarPlanVigente(alumnoId, hoy).map(plan -> mapearAResponse(plan, hoy));
    }

    @Transactional(readOnly = true)
    public PlanAsignadoResponse obtenerPlan(Long id) {
        return mapearAResponse(buscarPlanOLanzarError(id), ZonaHoraria.hoy());
    }

    /** Copia profunda de la plantilla, con todas sus semanas: el plan del alumno y la plantilla quedan independientes. */
    @Transactional
    public PlanAsignadoResponse asignarDesdePlantilla(AsignarPlanDesdePlantillaRequest request) {
        return asignarDesdePlantilla(request, ZonaHoraria.hoy());
    }

    public PlanAsignadoResponse asignarDesdePlantilla(AsignarPlanDesdePlantillaRequest request, LocalDate hoy) {
        Alumno alumno = alumnoSinPlanActivo(request.getAlumnoId(), hoy);
        PlantillaPlan plantilla = plantillaRepository.findByIdAndActivaTrue(request.getPlantillaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una plantilla con id " + request.getPlantillaId()));

        PlanAsignado plan = planRepository.save(PlanAsignado.copiaDe(plantilla, alumno, hoy));
        plantillaRepository.sumarAsignacion(plantilla.getId());
        return mapearAResponse(plan, hoy);
    }

    /** Plan armado a mano para el alumno; no queda guardado como plantilla. */
    @Transactional
    public PlanAsignadoResponse asignarDesdeCero(AsignarPlanDesdeCeroRequest request) {
        return asignarDesdeCero(request, ZonaHoraria.hoy());
    }

    public PlanAsignadoResponse asignarDesdeCero(AsignarPlanDesdeCeroRequest request, LocalDate hoy) {
        ReglasSemanas.validar(request.getSemanas(), "El plan");
        Alumno alumno = alumnoSinPlanActivo(request.getAlumnoId(), hoy);
        PlanAsignado plan = new PlanAsignado(alumno, hoy);
        aplicarDatos(plan, request);
        Map<Long, Ejercicio> catalogo = catalogoEjercicios.buscar(ReglasSemanas.sesiones(request.getSemanas()), Set.of());
        plan.agregarSemanas(request.getSemanas().stream()
                .map(semana -> crearSemana(semana, catalogo))
                .toList());
        return mapearAResponse(planRepository.save(plan), hoy);
    }

    /**
     * Modifica el plan activo; uno finalizado o vencido ya no se modifica. No toca la plantilla de la que salió.
     * Llega el plan completo, pero no todo se aplica:
     * <ul>
     * <li>Las semanas que ya pasaron quedan como están: lo que venga para ellas se ignora.</li>
     * <li>En la semana en curso, las sesiones que el alumno ya empezó o registró también quedan como están. Por eso
     * esa semana no puede tener menos sesiones que el mayor número de sesión con registro.</li>
     * <li>El resto de la semana en curso y las semanas futuras se reemplazan por lo recibido.</li>
     * </ul>
     * El plan no puede terminar antes de la semana en curso, y la fecha fin se recalcula con la cantidad de semanas.
     * Los registros del alumno no se tocan: están en otras tablas y apuntan por número de semana y de sesión.
     */
    @Transactional
    public PlanAsignadoResponse actualizarPlan(Long id, PlanRequest request) {
        return actualizarPlan(id, request, ZonaHoraria.hoy());
    }

    public PlanAsignadoResponse actualizarPlan(Long id, PlanRequest request, LocalDate hoy) {
        PlanAsignado plan = buscarPlanOLanzarError(id);
        if (!plan.estaActivo()) {
            throw new ConflictoException("El plan está finalizado y ya no se puede modificar");
        }
        ReglasSemanas.validar(request.getSemanas(), "El plan");
        int enCurso = semanaActual(plan);
        if (request.getSemanas().size() < enCurso) {
            throw new RegistroInvalidoException("El plan ya va por la semana " + enCurso
                    + ": no se pueden eliminar la semana en curso ni las anteriores");
        }

        // Sesiones de la semana en curso que el alumno ya empezó o registró: se conservan como están
        Map<Integer, SesionPlan> conRegistro = sesionesConRegistro(plan, enCurso);
        SemanaRequest semanaEnCurso = request.getSemanas().get(enCurso - 1);
        int ultimaConRegistro = conRegistro.keySet().stream().max(Integer::compare).orElse(0);
        if (semanaEnCurso.getSesiones().size() < ultimaConRegistro) {
            throw new RegistroInvalidoException("En la semana " + enCurso + " no se pueden quitar las sesiones que "
                    + plan.getAlumno().getNombre() + " ya empezó o registró");
        }
        // Copias hechas antes de borrar la semana: van en la semana nueva con el mismo número
        Map<Integer, SesionPlan> conservadas = conRegistro.values().stream()
                .collect(Collectors.toMap(SesionPlan::getNumero, SesionPlan::copiar));

        // Solo se validan los ejercicios de lo que se va a aplicar. Uno que se dio de baja del catálogo puede quedar
        // en el plan si ya estaba.
        List<SemanaRequest> editables = request.getSemanas().subList(enCurso - 1, request.getSemanas().size());
        List<SesionRequest> sesionesNuevas = new ArrayList<>();
        for (int i = 0; i < editables.size(); i++) {
            List<SesionRequest> sesiones = editables.get(i).getSesiones();
            for (int numero = 1; numero <= sesiones.size(); numero++) {
                if (i > 0 || !conRegistro.containsKey(numero)) {
                    sesionesNuevas.add(sesiones.get(numero - 1));
                }
            }
        }
        Map<Long, Ejercicio> catalogo = catalogoEjercicios.buscar(sesionesNuevas, ejerciciosDe(plan));

        plan.getSemanas().removeIf(semana -> semana.getNumero() >= enCurso);
        // Al hacer flush, Hibernate ejecuta los INSERT antes que los DELETE. Sin este flush, la semana nueva se
        // insertaría mientras la vieja con el mismo número todavía existe y chocaría con la restricción única
        // (plan, número). Así los DELETE de las semanas que se reemplazan salen primero.
        planRepository.flush();

        aplicarDatos(plan, request);
        List<SemanaPlan> nuevas = new ArrayList<>();
        for (int i = 0; i < editables.size(); i++) {
            nuevas.add(crearSemana(editables.get(i), catalogo, i == 0 ? conservadas : Map.of()));
        }
        plan.agregarSemanas(nuevas);
        return mapearAResponse(planRepository.save(plan), hoy);
    }

    /** Deja al alumno sin plan activo, para poder asignarle otro. */
    @Transactional
    public PlanAsignadoResponse finalizarPlan(Long id) {
        return finalizarPlan(id, ZonaHoraria.hoy());
    }

    public PlanAsignadoResponse finalizarPlan(Long id, LocalDate hoy) {
        PlanAsignado plan = buscarPlanOLanzarError(id);
        if (!plan.estaActivo()) {
            throw new ConflictoException("El plan ya está finalizado");
        }
        return mapearAResponse(finalizar(plan, hoy), hoy);
    }

    /**
     * Al dar de baja a un alumno, su plan activo (si tiene) se finaliza.
     */
    @Transactional
    public void finalizarPlanActivoDe(Long alumnoId) {
        LocalDate hoy = ZonaHoraria.hoy();
        planRepository.findFirstByAlumnoIdAndEstado(alumnoId, EstadoPlanAsignado.ACTIVO)
                .ifPresent(plan -> finalizar(plan, hoy));
    }

    /** Auto-finaliza un plan cuando se completaron todas las sesiones. */
    @Transactional
    public void finalizarPorCompletitud(PlanAsignado plan, LocalDate fecha) {
        finalizar(plan, fecha);
    }

    // --- Semana actual ---

    /** Semana actual de un plan basada en completitud de sesiones. Carga las semanas y los registros. */
    public int semanaActual(PlanAsignado plan) {
        return plan.semanaActual(completadasPorSemana(plan.getId()));
    }

    /** Mapa de semana → cantidad de sesiones completadas para un plan. */
    public Map<Integer, Long> completadasPorSemana(Long planId) {
        return registroRepository.findByPlanIdAndCompletadaTrue(planId).stream()
                .collect(Collectors.groupingBy(r -> r.getSemana(), Collectors.counting()));
    }

    /**
     * Semana actual de varios planes, optimizado con queries batch. Devuelve planId → semana actual.
     * Los planes deben ser ACTIVO; para los que no tienen registros, devuelve 1.
     */
    public Map<Long, Integer> semanasActualesBatch(List<PlanAsignado> planes) {
        if (planes.isEmpty()) return Map.of();
        List<Long> ids = planes.stream().map(PlanAsignado::getId).toList();

        Map<Long, Map<Integer, Long>> sesionesPorPlan = new HashMap<>();
        planRepository.contarSesionesPorPlanYSemana(ids).forEach(row -> sesionesPorPlan
                .computeIfAbsent((Long) row[0], k -> new HashMap<>())
                .put(((Number) row[1]).intValue(), ((Number) row[2]).longValue()));

        Map<Long, Map<Integer, Long>> completadasPorPlan = new HashMap<>();
        registroRepository.contarCompletadasPorPlanYSemana(ids).forEach(row -> completadasPorPlan
                .computeIfAbsent((Long) row[0], k -> new HashMap<>())
                .put(((Number) row[1]).intValue(), ((Number) row[2]).longValue()));

        return planes.stream().collect(Collectors.toMap(PlanAsignado::getId, plan ->
                PlanAsignado.calcularSemanaActual(plan.cantidadSemanas(),
                        sesionesPorPlan.getOrDefault(plan.getId(), Map.of()),
                        completadasPorPlan.getOrDefault(plan.getId(), Map.of()))));
    }

    // --- Auxiliares ---

    /** El único plan activo del alumno. */
    private Optional<PlanAsignado> buscarPlanVigente(Long alumnoId, LocalDate hoy) {
        return planRepository.findFirstByAlumnoIdAndEstado(alumnoId, EstadoPlanAsignado.ACTIVO);
    }

    private PlanAsignado finalizar(PlanAsignado plan, LocalDate fecha) {
        plan.finalizar(fecha);
        if (plan.getPlantillaOrigen() != null) {
            plantillaRepository.restarAsignacion(plan.getPlantillaOrigen().getId());
        }
        return planRepository.save(plan);
    }

    /**
     * El alumno, bloqueado hasta el fin de la transacción: dos asignaciones al mismo alumno a la vez se hacen una
     * detrás de otra, así la segunda ya ve el plan activo de la primera y no quedan dos.
     */
    private Alumno alumnoSinPlanActivo(Long alumnoId, LocalDate hoy) {
        Alumno alumno = alumnoRepository.findByIdConBloqueo(alumnoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un alumno con id " + alumnoId));
        if (alumno.getCuenta() != null && !alumno.getCuenta().isActiva()) {
            throw new RegistroInvalidoException("No se puede asignar un plan a un alumno dado de baja");
        }
        planRepository.findFirstByAlumnoIdAndEstado(alumnoId, EstadoPlanAsignado.ACTIVO).ifPresent(activo -> {
            throw new ConflictoException(alumno.getNombre() + " ya tiene un plan activo (\"" + activo.getNombre()
                    + "\"). Para asignarle uno nuevo, primero finalizá su plan actual");
        });
        return alumno;
    }

    private static void aplicarDatos(PlanAsignado plan, PlanRequest request) {
        plan.setNombre(request.getNombre().trim());
        plan.setNivel(request.getNivel());
        plan.setObjetivo(request.getObjetivo());
    }

    /** Los ejercicios que ya tiene el plan, en cualquier semana. */
    private static Set<Long> ejerciciosDe(PlanAsignado plan) {
        return plan.getSemanas().stream()
                .flatMap(semana -> semana.getSesiones().stream())
                .flatMap(sesion -> sesion.getEjercicios().stream())
                .map(ejercicio -> ejercicio.getEjercicio().getId())
                .collect(Collectors.toSet());
    }

    /** Las sesiones de esa semana que el alumno ya empezó o registró, por número. */
    private Map<Integer, SesionPlan> sesionesConRegistro(PlanAsignado plan, int semana) {
        Optional<SemanaPlan> actual = plan.buscarSemana(semana);
        return registroRepository.findByPlanIdAndSemana(plan.getId(), semana).stream()
                .map(registro -> actual.flatMap(s -> s.buscarSesion(registro.getNumeroSesion())))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(SesionPlan::getNumero, Function.identity()));
    }

    private SemanaPlan crearSemana(SemanaRequest request, Map<Long, Ejercicio> catalogo) {
        return crearSemana(request, catalogo, Map.of());
    }

    /** Una semana con las sesiones del request, salvo las de {@code fijas}, que se ponen en su mismo número. */
    private SemanaPlan crearSemana(SemanaRequest request, Map<Long, Ejercicio> catalogo, Map<Integer, SesionPlan> fijas) {
        SemanaPlan semana = new SemanaPlan(CatalogoEjercicios.textoOpcional(request.getNota()));
        List<SesionPlan> sesiones = new ArrayList<>();
        for (int numero = 1; numero <= request.getSesiones().size(); numero++) {
            SesionPlan fija = fijas.get(numero);
            sesiones.add(fija != null ? fija : crearSesion(request.getSesiones().get(numero - 1), catalogo));
        }
        semana.agregarSesiones(sesiones);
        return semana;
    }

    private static SesionPlan crearSesion(SesionRequest request, Map<Long, Ejercicio> catalogo) {
        SesionPlan sesion = new SesionPlan(CatalogoEjercicios.nombreDeSesion(request));
        sesion.agregarEjercicios(request.getEjercicios().stream()
                .map(e -> CatalogoEjercicios.conIndicaciones(new EjercicioSesionPlan(catalogo.get(e.getEjercicioId()),
                        e.getSeries(), e.getRepeticiones(), e.getRepeticionesMax(), e.getDescansoMinutos(),
                        e.getPesoSugerido(), e.getRirObjetivo()), e))
                .toList());
        return sesion;
    }

    private PlanAsignado buscarPlanOLanzarError(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un plan asignado con id " + id));
    }

    private PlanAsignadoResponse mapearAResponse(PlanAsignado plan, LocalDate hoy) {
        Alumno alumno = plan.getAlumno();
        boolean activo = plan.estaActivo();
        Integer enCurso = activo ? semanaActual(plan) : null;
        List<SesionConRegistroResponse> conRegistro = enCurso == null ? List.of()
                : registroRepository.findByPlanIdAndSemana(plan.getId(), enCurso).stream()
                        .sorted(Comparator.comparingInt(RegistroSesion::getNumeroSesion))
                        .map(registro -> new SesionConRegistroResponse(registro.getSemana(), registro.getNumeroSesion(),
                                registro.isCompletada(), registro.getFecha()))
                        .toList();
        return new PlanAsignadoResponse(
                plan.getId(),
                alumno.getId(),
                alumno.getNombre() + " " + alumno.getApellido(),
                plan.getNombre(),
                plan.getNivel(),
                plan.getObjetivo(),
                plan.getEstado(),
                plan.getPlantillaOrigen() == null ? null : plan.getPlantillaOrigen().getId(),
                plan.getFechaAsignacion(),
                plan.getFechaFinalizacion(),
                plan.getFechaFin(),
                plan.cantidadSemanas(),
                enCurso,
                plan.getSemanas().stream().map(PlanAsignadoService::mapearSemana).toList(),
                conRegistro);
    }

    private static SemanaResponse mapearSemana(SemanaPlan semana) {
        return new SemanaResponse(
                semana.getId(),
                semana.getNumero(),
                semana.getNota(),
                semana.getSesiones().stream().map(PlanAsignadoService::mapearSesion).toList());
    }

    private static SesionPlantillaResponse mapearSesion(SesionPlan sesion) {
        return new SesionPlantillaResponse(
                sesion.getId(),
                sesion.getNumero(),
                sesion.getNombre(),
                sesion.getEjercicios().stream().map(EjercicioSesionResponse::desde).toList());
    }
}
