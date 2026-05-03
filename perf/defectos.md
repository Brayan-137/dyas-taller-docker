# Registro de Defectos — Taller de Pruebas de Carga y Rendimiento

---

## Tabla de defectos (bug tracking)

| ID | Caso de Prueba | Entrada | Resultado Esperado | Resultado Obtenido | Causa Probable | Estado |
|---|---|---|---|---|---|---|
| DEF-01 | Baseline — POST /register con personas `alive=false` | `{ "name": "Juan", "id": 101, "age": 28, "gender": "MALE", "alive": false }` (~5% del CSV original) | HTTP 200, body `VALID`, `register_failed < 1%` | HTTP 200, body `DEAD`, `register_failed = 7.1%` (21,888 fallos de 307,928 requests) | `registerVoter()` retorna `RegisterResult.DEAD` cuando `isAlive() == false`. El CSV de prueba incluía ~5% de filas con `alive=false`, generando fallos sistemáticos de validación de negocio | Resuelto |
| DEF-02 | Baseline / Carga / Estrés — POST /register con VUs concurrentes | Múltiples requests simultáneas con IDs coincidentes generados por `buildUniqueId()` entre diferentes VUs en la misma iteración | Inserción atómica: una request retorna `VALID`, las concurrentes detectan duplicado antes del intento de escritura | Body `DUPLICATED` en requests que pasan simultáneamente el `existsById()` antes de que cualquiera ejecute `save()`. Tasa de fallo: 0.34% (baseline), 0.77% (carga 200 VUs), 1.96% (estrés 600 VUs) | Bloque `existsById + save` en `registerVoter()` no está envuelto en transacción atómica. Bajo concurrencia, el check y la escritura no son una operación única, lo que permite que dos hilos pasen el guard simultáneamente con el mismo ID. El problema escala proporcionalmente con los VUs | Resuelto |

---

## Convenciones de Estado

- **Abierto** → El defecto aún no se corrige.
- **En progreso** → El defecto está siendo trabajado.
- **Resuelto** → El defecto fue corregido y validado con pruebas.

---

## Detalle de resolución

### DEF-01 — Resuelto
**Acción tomada:** Se modificó el generador `generate_persons.py` para producir únicamente registros con `alive=true`, eliminando la fuente de datos que activaba el retorno `DEAD` del servicio. Adicionalmente se corrigió el assert de k6 de `body.includes('VALID')` a `body === 'VALID'` para evitar falsos positivos con el valor `INVALID`.

**Validación:** Baseline re-ejecutado con el CSV corregido — `register_failed` bajó de 7.1% a 0.34%, confirmando que los fallos restantes corresponden exclusivamente al DEF-02.

### DEF-02 — Resuelto

**Acción tomada:** Se eliminó el patrón Check-Then-Act (`existsById + save`) y se reemplazó por un intento de inserción directa con captura explícita de `DataIntegrityViolationException`. La atomicidad se delega al motor de base de datos, que garantiza que solo una inserción por ID sea exitosa mediante la restricción de Primary Key.

**Función corregida — `registerVoter`:**
```java
public RegisterResult registerVoter(Person p) {
    // 1. Validaciones de negocio (Fail-fast) — sin cambios
    if (p == null || p.getId() <= 0)
        return RegisterResult.INVALID;
    if (!p.isAlive())
        return RegisterResult.DEAD;
    if (p.getAge() < 18)
        return RegisterResult.UNDERAGE;

    try {
        // ELIMINACIÓN DEL CHECK-THEN-ACT: no se llama a existsById().
        // Se intenta persistir directamente; la BD garantiza la atomicidad.
        repo.save(p.getId(), p.getName(), p.getAge(), p.isAlive());
        return RegisterResult.VALID;

    } catch (DataIntegrityViolationException e) {
        // Violación de unicidad en BD (ID duplicado) — respuesta correcta
        return RegisterResult.DUPLICATED;

    } catch (Exception e) {
        throw new IllegalStateException("Error de persistencia no controlado: "
            + e.getClass().getSimpleName(), e);
    }
}
```

Al eliminar `existsById()`, se reduce el número de interacciones con la BD de dos a una por request exitosa, lo que elimina la ventana de race condition y mejora la latencia. La tasa de colisión real de IDs es baja (< 2%), por lo que es más eficiente manejar la excepción ocasional que serializar todas las escrituras con `SERIALIZABLE`, lo cual degradaría el throughput general.

**Validación — re-ejecución de los 3 escenarios:**

| Métrica | Pre-corrección | Post-corrección | Δ |
|---|---|---|---|
| register_failed — Baseline (20 VUs) | 0.34% | **0.32%** | -0.02 pp ✅ |
| register_failed — Carga (200 VUs) | 0.77% | **0.51%** | -0.26 pp ✅ |
| register_failed — Estrés (600 VUs) | 1.96% | **1.72%** | -0.24 pp |
| p(99) latencia — Estrés | 990.10 ms | **741.86 ms** | -248 ms ✅ SLO cumplido |
| Throughput — Estrés | 2,071 req/s | **2,346 req/s** | +13.3% ✅ |

**Resultado:** `register_failed` cumple el SLO de < 1% en baseline y carga. En estrés (600 VUs) baja de 1.96% a 1.72% — mejorado, aunque aún sobre el umbral por la alta tasa de colisión estadística con el dataset de 250 IDs a ~2,346 req/s. El p(99) de estrés pasa de incumplir a cumplir el SLO de 800 ms.
