# Registro de Defectos — Taller Pruebas de Carga y Rendimiento

---

## Tabla de defectos (bug tracking)

| ID | Caso de Prueba | Entrada | Resultado Esperado | Resultado Obtenido | Causa Probable | Estado |
|---|---|---|---|---|---|---|
| DEF-01 | Baseline — POST /register, personas `alive=false` | `{ "alive": false, ... }` (~5% del CSV) | HTTP 200, body `VALID`, `register_failed < 1%` | HTTP 200, body `DEAD`, `register_failed = 7.1%` (21,888 fallos) | `registerVoter()` retorna `DEAD` cuando `isAlive() == false`; datos de prueba incluyen filas con `alive=false` | Abierto |
| DEF-02 | Baseline — POST /register, 20 VUs concurrentes | Requests simultáneas con IDs coincidentes entre VUs | Inserción atómica; una respuesta `VALID`, duplicados detectados correctamente | Body `DUPLICATED` en requests que llegan segundo al `existsById()` antes del `save()` | `existsById + save` sin `@Transactional`; race condition bajo concurrencia | Abierto |

---

## Convenciones de Estado

- **Abierto** → El defecto aún no se corrige.
- **En progreso** → El defecto está siendo trabajado.
- **Resuelto** → El defecto fue corregido y validado con pruebas.
