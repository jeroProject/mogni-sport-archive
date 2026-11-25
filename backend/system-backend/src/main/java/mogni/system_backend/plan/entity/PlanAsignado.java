package mogni.system_backend.plan.entity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mogni.system_backend.alumno.entity.Alumno;
import mogni.system_backend.plantilla.entity.NivelPlantilla;
import mogni.system_backend.plantilla.entity.ObjetivoPlantilla;
import mogni.system_backend.plantilla.entity.PlantillaPlan;

/**
 * HU-266: plan de entrenamiento de un alumno. Se arma desde cero o copiando una plantilla; en ese caso es una
 * copia profunda (semanas, sesiones y ejercicios propios), así que modificar el plan no toca la plantilla y viceversa.
 * Cada semana tiene sus propias sesiones, y la cantidad de semanas es la duración del plan.
 */
@Entity
@Table(name = "planes_asignados")
@Getter
@Setter
@NoArgsConstructor
public class PlanAsignado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelPlantilla nivel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObjetivoPlantilla objetivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPlanAsignado estado = EstadoPlanAsignado.ACTIVO;

    /**
     * De qué plantilla salió, o null si se armó desde cero. Solo sirve para saber el origen y llevar la cuenta
     * de asignaciones activas de la plantilla: los datos del plan son una copia y no se leen de acá.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_origen_id")
    private PlantillaPlan plantillaOrigen;

    @Column(nullable = false)
    private LocalDate fechaAsignacion;

    /** Null mientras está ACTIVO. */
    private LocalDate fechaFinalizacion;

    /**
     * HU-266: último día del plan (inclusiva): el último día de la última semana. Se recalcula cada vez que cambian
     * las semanas (ver {@link #agregarSemanas}). Se guarda para no tener que cargar las semanas en los listados.
     */
    @Column(nullable = false)
    private LocalDate fechaFin;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero ASC")
    private List<SemanaPlan> semanas = new ArrayList<>();

    public PlanAsignado(Alumno alumno, LocalDate fechaAsignacion) {
        this.alumno = alumno;
        this.fechaAsignacion = fechaAsignacion;
    }

    /** Copia profunda de la plantilla para el alumno: mismo nombre, nivel, objetivo y semanas. */
    public static PlanAsignado copiaDe(PlantillaPlan plantilla, Alumno alumno, LocalDate fechaAsignacion) {
        PlanAsignado plan = new PlanAsignado(alumno, fechaAsignacion);
        plan.setNombre(plantilla.getNombre());
        plan.setNivel(plantilla.getNivel());
        plan.setObjetivo(plantilla.getObjetivo());
        plan.setPlantillaOrigen(plantilla);
        plan.agregarSemanas(plantilla.getSemanas().stream().map(SemanaPlan::copiaDe).toList());
        return plan;
    }

    /** Solo mira el estado guardado. Para saber si el alumno lo tiene como plan actual, usar {@link #estaVigente}. */
    public boolean estaActivo() {
        return estado == EstadoPlanAsignado.ACTIVO;
    }

    /**
     * El plan está vigente mientras esté ACTIVO. Ya no vence por fecha: se finaliza al completar todas las sesiones
     * o cuando el entrenador lo cierra a mano.
     */
    public boolean estaVigente(LocalDate hoy) {
        return estaActivo();
    }

    /** Ya no hay vencimiento por fecha: un plan ACTIVO nunca está vencido. */
    public boolean estaVencido(LocalDate hoy) {
        return false;
    }

    public EstadoPlanAsignado estadoAl(LocalDate hoy) {
        return estado;
    }

    public LocalDate fechaFinalizacionAl(LocalDate hoy) {
        return fechaFinalizacion;
    }

    public void finalizar(LocalDate fecha) {
        estado = EstadoPlanAsignado.FINALIZADO;
        fechaFinalizacion = fecha;
    }


    /**
     * Sale de la fecha fin, que siempre es el último día de la última semana: así los listados no tienen que cargar
     * las semanas.
     */
    public int cantidadSemanas() {
        long dias = ChronoUnit.DAYS.between(fechaAsignacion, fechaFin) + 1;
        return (int) Math.max(1, Math.ceilDiv(dias, 7));
    }

    /**
     * La primera semana con sesiones sin completar. Requiere las semanas cargadas.
     * @param completadasPorSemana mapa de número de semana → cantidad de sesiones completadas
     */
    public int semanaActual(Map<Integer, Long> completadasPorSemana) {
        for (SemanaPlan semana : semanas) {
            if (completadasPorSemana.getOrDefault(semana.getNumero(), 0L) < semana.getSesiones().size()) {
                return semana.getNumero();
            }
        }
        return cantidadSemanas();
    }

    /** True si todas las sesiones de todas las semanas están completadas. Requiere las semanas cargadas. */
    public boolean planCompletado(Map<Integer, Long> completadasPorSemana) {
        if (semanas.isEmpty()) return false;
        for (SemanaPlan semana : semanas) {
            if (completadasPorSemana.getOrDefault(semana.getNumero(), 0L) < semana.getSesiones().size()) {
                return false;
            }
        }
        return true;
    }

    /** Para cálculos batch sin cargar las semanas de la entidad. */
    public static int calcularSemanaActual(int cantidadSemanas,
            Map<Integer, Long> sesionesPorSemana, Map<Integer, Long> completadasPorSemana) {
        for (int semana = 1; semana <= cantidadSemanas; semana++) {
            if (completadasPorSemana.getOrDefault(semana, 0L) < sesionesPorSemana.getOrDefault(semana, 0L)) {
                return semana;
            }
        }
        return Math.max(1, cantidadSemanas);
    }

    /**
     * Agrega las semanas al final, en el orden recibido, siguiendo la numeración, y corre la fecha fin al último día
     * de la última semana. Para reemplazar semanas, primero hay que sacarlas y hacer flush (ver
     * PlanAsignadoService.actualizarPlan).
     */
    public void agregarSemanas(List<SemanaPlan> nuevas) {
        for (SemanaPlan semana : nuevas) {
            semana.setNumero(semanas.size() + 1);
            semana.setPlan(this);
            semanas.add(semana);
        }
        fechaFin = fechaAsignacion.plusWeeks(semanas.size()).minusDays(1);
    }

    public Optional<SemanaPlan> buscarSemana(int numero) {
        return semanas.stream().filter(semana -> semana.getNumero() == numero).findFirst();
    }

    /** Lo que el profe indicó para esa sesión de esa semana, si existe. */
    public Optional<SesionPlan> buscarSesion(int semana, int numeroSesion) {
        return buscarSemana(semana).flatMap(encontrada -> encontrada.buscarSesion(numeroSesion));
    }

    /** Todas las sesiones del plan: la suma de las de cada semana. */
    public int sesionesTotales() {
        return semanas.stream().mapToInt(semana -> semana.getSesiones().size()).sum();
    }
}
