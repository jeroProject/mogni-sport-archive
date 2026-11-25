package mogni.system_backend.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import mogni.system_backend.alumno.entity.Alumno;
import mogni.system_backend.alumno.repository.AlumnoRepository;
import mogni.system_backend.auth.entity.Cuenta;
import mogni.system_backend.auth.entity.Rol;
import mogni.system_backend.panel.dto.PlanesPorVencerResponse;
import mogni.system_backend.panel.service.PlanesPorVencerService;
import mogni.system_backend.plan.entity.PlanAsignado;
import mogni.system_backend.plan.entity.SemanaPlan;
import mogni.system_backend.plan.entity.SesionPlan;
import mogni.system_backend.plan.repository.PlanAsignadoRepository;
import mogni.system_backend.plantilla.entity.NivelPlantilla;
import mogni.system_backend.plantilla.entity.ObjetivoPlantilla;
import mogni.system_backend.progreso.entity.RegistroSesion;
import mogni.system_backend.progreso.repository.RegistroSesionRepository;

/**
 * Planes por completar de Inicio: planes activos con pocas sesiones restantes. Contra PostgreSQL embebido.
 */
@SpringBootTest(properties = "mogni.test.contexto=planes-por-vencer")
@ActiveProfiles("test")
class PlanesPorVencerIntegrationTest {

    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SECUENCIA = new AtomicInteger(7000);

    @Autowired
    private PlanesPorVencerService service;

    @Autowired
    private AlumnoRepository alumnoRepository;

    @Autowired
    private PlanAsignadoRepository planRepository;

    @Autowired
    private RegistroSesionRepository registroRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void vaciarBase() {
        jdbc.execute("TRUNCATE TABLE alumnos, cuentas RESTART IDENTITY CASCADE");
    }

    private Alumno alumno(String apellido, boolean activo) {
        int n = SECUENCIA.incrementAndGet();
        Cuenta cuenta = new Cuenta();
        cuenta.setEmail("planes" + n + "@test.com");
        cuenta.setPassword("x");
        cuenta.setRol(Rol.ALUMNO);
        cuenta.setActiva(activo);
        Alumno alumno = new Alumno();
        alumno.setNombre("Alumno");
        alumno.setApellido(apellido);
        alumno.setEdad(30);
        alumno.setDni(String.valueOf(43_000_000 + n));
        alumno.setEmail(cuenta.getEmail());
        alumno.setCuenta(cuenta);
        return alumnoRepository.save(alumno);
    }

    /** Plan ACTIVO con la cantidad de sesiones indicada (una por semana). */
    private PlanAsignado plan(Alumno alumno, int sesiones) {
        PlanAsignado plan = new PlanAsignado(alumno, HOY.minusMonths(1));
        plan.setNombre("Plan de " + alumno.getApellido());
        plan.setNivel(NivelPlantilla.INTERMEDIO);
        plan.setObjetivo(ObjetivoPlantilla.HIPERTROFIA);
        for (int i = 1; i <= sesiones; i++) {
            SemanaPlan semana = new SemanaPlan(null);
            SesionPlan sesion = new SesionPlan("Sesión " + i);
            semana.agregarSesiones(List.of(sesion));
            plan.agregarSemanas(List.of(semana));
        }
        return planRepository.save(plan);
    }

    /** Completa las primeras N sesiones del plan. */
    private void completar(PlanAsignado plan, int cantidad) {
        for (int i = 1; i <= cantidad; i++) {
            RegistroSesion reg = new RegistroSesion(plan, i, 1, HOY.minusDays(cantidad - i));
            reg.setCompletada(true);
            registroRepository.save(reg);
        }
    }

    private static List<String> apellidos(PlanesPorVencerResponse respuesta) {
        return respuesta.planes().stream().map(PlanesPorVencerResponse.PlanPorVencer::apellidoAlumno).toList();
    }

    @Test
    void planConTresSesionesRestantesApareceYConCuatroNo() {
        PlanAsignado planTres = plan(alumno("Tres", true), 5);
        completar(planTres, 2);
        PlanAsignado planCuatro = plan(alumno("Cuatro", true), 6);
        completar(planCuatro, 2);

        PlanesPorVencerResponse respuesta = service.consultar();

        assertEquals(List.of("Tres"), apellidos(respuesta));
        PlanesPorVencerResponse.PlanPorVencer tres = respuesta.planes().get(0);
        assertEquals(3, tres.sesionesRestantes());
        assertEquals(5, tres.sesionesTotales());
        assertEquals("Plan de Tres", tres.nombre());
    }

    @Test
    void ordenadoPorSesionesRestantesYLuegoApellido() {
        PlanAsignado planUno = plan(alumno("Zeta", true), 4);
        completar(planUno, 3);
        PlanAsignado planDos = plan(alumno("Alfa", true), 5);
        completar(planDos, 3);
        PlanAsignado planTres = plan(alumno("Beta", true), 5);
        completar(planTres, 3);

        PlanesPorVencerResponse respuesta = service.consultar();

        assertEquals(PlanesPorVencerService.SESIONES_RESTANTES_AVISO, respuesta.sesionesRestantesAviso());
        assertEquals(List.of("Zeta", "Alfa", "Beta"), apellidos(respuesta));
        assertEquals(1, respuesta.planes().get(0).sesionesRestantes());
        assertEquals(2, respuesta.planes().get(1).sesionesRestantes());
    }

    @Test
    void planSinRegistrosYConPocasSesionesTotalesAparece() {
        plan(alumno("NuevoCorto", true), 2);

        PlanesPorVencerResponse respuesta = service.consultar();

        assertEquals(List.of("NuevoCorto"), apellidos(respuesta));
        assertEquals(2, respuesta.planes().get(0).sesionesRestantes());
    }

    @Test
    void elPlanDeUnAlumnoInactivoNoAparece() {
        PlanAsignado plan = plan(alumno("Inactivo", false), 3);
        completar(plan, 1);

        assertTrue(service.consultar().planes().isEmpty());
    }
}
