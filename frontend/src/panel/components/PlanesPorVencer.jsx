import { CalendarClock, ChevronRight } from 'lucide-react';

import { cx, FOCUS } from '../../shared/components/clases';
import { Badge } from '../../shared/components/ui';

import { textoSesionesRestantes } from '../services/panelService';
import { CLASE_TARJETA, TONOS_ICONO } from './estilos';
import { ErrorSeccion } from './SeccionSegura';

const tonoRestantes = (restantes) => (restantes === 0 ? 'error' : restantes === 1 ? 'alert' : 'warning');

function Icono({ tono }) {
  return (
    <span className={cx('flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', TONOS_ICONO[tono])}>
      <CalendarClock className="h-5 w-5" />
    </span>
  );
}

/**
 * Tarjeta de Inicio con los planes por completar, en la misma grilla que los indicadores. Vacía ocupa una sola celda;
 * con planes ocupa todo el ancho. Si la carga falló, muestra el error en su lugar.
 * resultado: { datos: { sesionesRestantesAviso, planes }, error } (lo trae InicioPanel).
 */
export default function PlanesPorVencer({ resultado, onVerAlumno }) {
  if (resultado.error) {
    return (
      <div className="col-span-full">
        <ErrorSeccion titulo="No se pudieron cargar los planes por completar" mensaje={resultado.error.message} />
      </div>
    );
  }

  const { sesionesRestantesAviso, planes } = resultado.datos;

  const encabezado = (
    <div className="flex min-w-0 items-center gap-3">
      <Icono tono={planes.length === 0 ? 'neutral' : 'warning'} />
      <span className="min-w-0 flex-1">
        <span className="block text-2xl font-bold tabular-nums text-ink">{planes.length}</span>
        <span className="block text-xs text-ink-soft">Planes por completar</span>
        <span className="mt-0.5 block text-[11px] text-ink-muted">{sesionesRestantesAviso} sesiones restantes o menos</span>
      </span>
    </div>
  );

  if (planes.length === 0) {
    return <div className={cx(CLASE_TARJETA, 'min-w-0')}>{encabezado}</div>;
  }

  return (
    <div className={cx(CLASE_TARJETA, 'col-span-full min-w-0')}>
      {encabezado}
      <ul className="mt-3 divide-y divide-line border-t border-line">
        {planes.map((plan) => (
          <li key={plan.planId}>
            <button
              type="button"
              onClick={() => onVerAlumno(plan.alumnoId)}
              className={cx('flex w-full cursor-pointer items-center gap-3 rounded py-2.5 text-left hover:bg-row-hover', FOCUS)}
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold text-ink">
                  {plan.nombreAlumno} {plan.apellidoAlumno}
                </span>
                <span className="block truncate text-xs text-ink-soft">{plan.nombre}</span>
              </span>
              <span className="shrink-0 text-right">
                <Badge tone={tonoRestantes(plan.sesionesRestantes)}>{textoSesionesRestantes(plan.sesionesRestantes)}</Badge>
                <span className="mt-1 block text-[11px] tabular-nums text-ink-muted">{plan.sesionesTotales} sesiones totales</span>
              </span>
              <ChevronRight className="h-4 w-4 shrink-0 text-ink-muted" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
