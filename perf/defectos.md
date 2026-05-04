# Registro de Defectos — Taller de Pruebas de Carga y Rendimiento

---

## Tabla de defectos (bug tracking)

| ID | Caso de Prueba | Entrada | Resultado Esperado | Resultado Obtenido | Causa Probable | Estado |
|---|---|---|---|---|---|---|
| DEF-01 | Baseline — POST /register con personas `alive=false` | `{ "name": "Juan", "id": 101, "age": 28, "gender": "MALE", "alive": false }` (~5% del CSV original) | HTTP 200, body `VALID`, `register_failed < 1%` | HTTP 200, body `DEAD`, `register_failed = 7.1%` (21,888 fallos de 307,928 requests) | `registerVoter()` retorna `RegisterResult.DEAD` cuando `isAlive() == false`. El CSV de prueba incluía ~5% de filas con `alive=false`, generando fallos sistemáticos de validación de negocio | ✅ Resuelto |
| DEF-02 | Baseline / Carga / Estrés — POST /register con VUs concurrentes | Múltiples requests simultáneas con IDs coincidentes generados por `buildUniqueId()` entre diferentes VUs en la misma iteración | Inserción atómica: una request retorna `VALID`, las concurrentes detectan duplicado antes del intento de escritura | Body `DUPLICATED` en requests que pasan simultáneamente el `existsById()` antes de que cualquiera ejecute `save()`. Tasa de fallo: 0.34% (baseline), 0.77% (carga 200 VUs), 1.96% (estrés 600 VUs) | Bloque `existsById + save` en `registerVoter()` no estaba envuelto en transacción atómica. Bajo concurrencia, el check y la escritura no eran una operación única, permitiendo que dos hilos pasaran el guard simultáneamente con el mismo ID | ✅ Resuelto |
| DEF-03 | Soak — POST /register con 100 VUs constantes durante 2 horas | 100 VUs sostenidos, 13,923,758 requests totales en 2 horas | `http_req_failed < 1%` sostenido durante toda la prueba | `http_req_failed = 4.18%` (582,604 requests con respuesta HTTP no-200). Ambos checks (`status 200` y `body VALID`) reportan el mismo número de fallos, confirmando que son fallos de transporte y no de validación de negocio | Degradación acumulada en el tiempo: el pool de conexiones de H2 se agota progresivamente bajo carga sostenida de 100 VUs durante 2 horas. Posible fuga de conexiones o acumulación de objetos en heap sin liberación entre iteraciones | 🔴 Abierto |

---

## Convenciones de Estado

- **🔴 Abierto** → El defecto aún no se corrige.
- **🟡 En progreso** → El defecto está siendo trabajado.
- **✅ Resuelto** → El defecto fue corregido y validado con pruebas.

---

## Detalle de resolución

### DEF-01 — ✅ Resuelto

**Acción tomada:** Se modificó el generador `generate_persons.py` para producir únicamente registros con `alive=true`, eliminando la fuente de datos que activaba el retorno `DEAD` del servicio. Adicionalmente se corrigió el assert de k6 de `body.includes('VALID')` a `body === 'VALID'` para evitar falsos positivos con el valor `INVALID`.

**Validación:** Baseline re-ejecutado con el CSV corregido — `register_failed` bajó de 7.1% a 0.34%, confirmando que los fallos restantes corresponden exclusivamente al DEF-02.

---

### DEF-02 — ✅ Resuelto

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

**Validación — re-ejecución de los 3 escenarios:**

| Métrica | Pre-corrección | Post-corrección | Δ |
|---|---|---|---|
| register_failed — Baseline (20 VUs) | 0.34% | **0.32%** | -0.02 pp ✅ |
| register_failed — Carga (200 VUs) | 0.77% | **0.51%** | -0.26 pp ✅ |
| register_failed — Estrés (600 VUs) | 1.96% | **1.72%** | -0.24 pp |
| p(99) latencia — Estrés | 990.10 ms | **741.86 ms** | -248 ms ✅ SLO cumplido |
| Throughput — Estrés | 2,071 req/s | **2,346 req/s** | +13.3% ✅ |

**Resultado:** `register_failed` cumple el SLO de < 1% en baseline y carga. En estrés (600 VUs) baja de 1.96% a 1.72% — mejorado aunque aún sobre el umbral por la alta tasa de colisión estadística con el dataset de 250 IDs a ~2,346 req/s. El p(99) de estrés pasa de incumplir a cumplir el SLO de 800 ms.

---

### DEF-03 — 🔴 Abierto

**Evidencia del hallazgo:**

| Métrica | Valor soak (2h, 100 VUs) | SLO | Estado |
|---|---|---|---|
| http_req_failed | **4.18%** (582,604 fallos) | < 1% | ❌ |
| register_failed | **4.18%** (582,604 fallos) | < 1% | ❌ |
| p(95) latencia | 269.9 ms | ≤ 300 ms | ✅ |
| p(99) latencia | 433.5 ms | ≤ 800 ms | ✅ |
| Throughput | 1,934 req/s | ≥ 100 req/s | ✅ |

**Análisis:** El hecho de que `http_req_failed` y `register_failed` tengan exactamente el mismo valor (4.18%) indica que los 582,604 fallos son a nivel de transporte HTTP — el servidor devolvió respuestas no-200, no un body diferente a `VALID`. La latencia p(95) de 269.9 ms dentro del SLO sugiere que el sistema no estaba saturado en términos de tiempo de respuesta, sino que empezó a rechazar conexiones por agotamiento de recursos internos (pool de BD o threads).

**Causa probable:** H2 en memoria acumula conexiones abiertas o registros a lo largo de 2 horas de escritura continua (~13.9 millones de inserciones). El pool de conexiones de HikariCP alcanza su límite y comienza a rechazar requests con excepciones no controladas que Spring Boot convierte en respuestas 5xx.

**Propuesta de corrección:**
```yaml
# application.properties — ajuste del pool de HikariCP
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=3000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

Adicionalmente, configurar el escenario soak con un dataset más amplio (50,000+ IDs) para reducir la acumulación de registros duplicados y aliviar la presión sobre H2.
