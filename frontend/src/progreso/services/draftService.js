import { getEmailFromToken } from '../../shared/services/tokenStorage';

const PREFIX = 'mogni.draft_';

function buildKey(email, planId, semana, sesion, ejercicioOrden) {
  return `${PREFIX}${email}_plan_${planId}_semana_${semana}_sesion_${sesion}_ejercicio_${ejercicioOrden}`;
}

export function cargarBorrador(planId, semana, sesion, ejercicioOrden, cantidadSeries) {
  const email = getEmailFromToken();
  if (!email) return null;
  try {
    const raw = localStorage.getItem(buildKey(email, planId, semana, sesion, ejercicioOrden));
    if (!raw) return null;
    const draft = JSON.parse(raw);
    if (!Array.isArray(draft.filas) || draft.filas.length !== cantidadSeries) {
      localStorage.removeItem(buildKey(email, planId, semana, sesion, ejercicioOrden));
      return null;
    }
    return draft;
  } catch {
    try { localStorage.removeItem(buildKey(email, planId, semana, sesion, ejercicioOrden)); } catch { /* ignore */ }
    return null;
  }
}

export function guardarBorrador(planId, semana, sesion, ejercicioOrden, filas, notas) {
  const email = getEmailFromToken();
  if (!email) return;
  try {
    localStorage.setItem(buildKey(email, planId, semana, sesion, ejercicioOrden), JSON.stringify({ filas, notas }));
  } catch (e) {
    console.warn('guardarBorrador: no se pudo escribir en localStorage', e);
  }
}

export function limpiarBorrador(planId, semana, sesion, ejercicioOrden) {
  const email = getEmailFromToken();
  if (!email) return;
  try {
    localStorage.removeItem(buildKey(email, planId, semana, sesion, ejercicioOrden));
  } catch (e) {
    console.warn('limpiarBorrador: no se pudo limpiar en localStorage', e);
  }
}

export function limpiarBorradoresDelUsuario() {
  const email = getEmailFromToken();
  if (!email) return;
  try {
    const prefijo = `${PREFIX}${email}_`;
    const aRemover = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(prefijo)) aRemover.push(key);
    }
    aRemover.forEach((key) => localStorage.removeItem(key));
  } catch (e) {
    console.warn('limpiarBorradoresDelUsuario: error limpiando localStorage', e);
  }
}
