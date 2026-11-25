package mogni.system_backend.turno.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Un día del selector de 5 días. Solo hoy, mañana y pasado mañana están habilitados. */
@Getter
@AllArgsConstructor
public class DiaTurnosResponse {
    private LocalDate fecha;
    private boolean habilitado;
}
