package mogni.system_backend.turno.dto;

/** MOGNI-281: por qué el alumno no puede reservar ningún turno de hoy, mañana ni pasado mañana. */
public enum MotivoNoReserva {
    /** La cuota está en mora: no puede reservar hasta regularizar el pago. */
    EN_MORA,
    /**
     * La cuota vence hoy o venció ayer: todavía no está en mora, pero los días que le quedan libres ya lo va a estar,
     * así que para reservarlos tiene que regularizar el pago.
     */
    CUOTA_VENCIDA,
    /** No tiene una inscripción activa, así que su membresía no le da turnos. */
    SIN_MEMBRESIA_ACTIVA,
    /** Usó todos los turnos de la semana y no le queda ninguna clase extra. */
    CUPO_SEMANAL_AGOTADO,
    /** Ya tiene un turno en cada uno de los días que se pueden reservar (hoy, mañana y pasado mañana). */
    SIN_DIAS_DISPONIBLES
}
