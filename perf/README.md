# Taller de Pruebas de Carga y Rendimiento

## Sistema de Registro de Votantes — Spring Boot API

---

> **Curso:** Diseño y Arquitectura de Software <br>
> **Programa:** Ingeniería Informática — Universidad de La Sabana <br>
> **Año:** 2026 <br>
> **Equipo:**
> - Brayan Presiga Sepulveda - 0000301424
> - Juan David Sanchez Roldan - 0000340321
> - Yuly Dayana Rodríguez Salcedo - 0000305314

## 📋 Descripción del sistema bajo prueba

API REST desarrollada en Spring Boot que permite registrar votantes mediante el endpoint `POST /register`. Valida reglas de negocio como mayoría de edad, estado de vida y duplicados antes de persistir en base de datos H2 en memoria. El sistema retorna `VALID` cuando el registro es exitoso, o un estado descriptivo (`DEAD`, `UNDERAGE`, `DUPLICATED`, `INVALID`) cuando no lo es.

---

## 🎯 SLA / SLO definidos

| Métrica | Objetivo (SLO) | Justificación |
|---|---|---|
| p(95) latencia | ≤ 300 ms | Estándar para APIs REST interactivas en producción |
| p(99) latencia | ≤ 800 ms | Tolerancia máxima antes de timeout del cliente |
| Error rate HTTP | < 1% | Confiabilidad mínima aceptable en producción |
| register_failed | < 1% | Las validaciones de negocio deben pasar en > 99% |
| Throughput mínimo | ≥ 100 req/s | Demanda esperada en horario pico |

---

## 📁 Estructura del proyecto

```
perf/
 ├─ scripts/
 │   └─ register_person_k6.js     # Script principal de pruebas k6
 ├─ data/
 │   └─ persons.csv               # Dataset de prueba (250 filas)
 ├─ results/
 │   ├─ summary-baseline.json     # Resultado escenario baseline
 │   ├─ summary-load.json         # Resultado escenario carga
 │   ├─ summary-stress.json       # Resultado escenario estrés
 │   ├─ summary-spike.json        # Resultado escenario picos
 │   └─ summary-soak.json         # Resultado escenario resistencia
 ├─ ci/
 │   └─ perf-pipeline.yml         # Pipeline GitHub Actions
 ├─ defectos.md                   # Registro de defectos encontrados
 └─ README.md                     # Este documento
```

---

## 🛠️ Herramientas y dependencias

| Herramienta | Versión | Uso |
|---|---|---|
| k6 | v0.51.0 | Ejecución de pruebas de carga |
| OpenJDK | 17.0.18 | Runtime del sistema bajo prueba |
| Spring Boot | 3.x | Framework del sistema bajo prueba |
| H2 | En memoria | Base de datos del sistema bajo prueba |
| Python | 3.x | Generación del dataset CSV |

---

## ⚙️ Pre-requisitos

Antes de ejecutar las pruebas asegúrate de tener:

1. **Servicio corriendo** en `http://localhost:8080`
2. **k6 instalado** — verifica con `k6 version`
3. **Dataset generado** en `perf/data/persons.csv`
4. **Carpeta de resultados creada:**

```bash
mkdir -p perf/results
```

### Verificar que el endpoint responde

```bash
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Ana\",\"id\":1,\"age\":30,\"gender\":\"FEMALE\",\"alive\":true}"
```

✅ Respuesta esperada: `200 OK` con body `VALID`

### Generar el dataset

```bash
python generate_persons.py --rows 250 --output perf/data/persons.csv
```

> El generador produce únicamente personas con `alive=true` para evitar fallos de validación de negocio durante las pruebas.

---

## ▶️ Ejecución de escenarios

> ⚠️ Ejecutar siempre desde la **raíz del repositorio**

### Escenario 1 — Baseline

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e SCENARIO=baseline \
  perf/scripts/register_person_k6.js
```

- **VUs:** 20 constantes
- **Duración:** 5 minutos
- **Resultado:** `perf/results/summary-baseline.json`

### Escenario 2 — Carga

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e SCENARIO=load \
  perf/scripts/register_person_k6.js
```

- **VUs:** rampa 0 → 200 en 2 min, sostiene 10 min, baja en 2 min
- **Duración:** ~14 minutos
- **Resultado:** `perf/results/summary-load.json`

### Escenario 3 — Estrés

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e SCENARIO=stress \
  perf/scripts/register_person_k6.js
```

- **VUs:** rampa 0 → 200 (warmup 2 min) → 600 (estrés 5 min) → 0 (bajada 2 min)
- **Duración:** ~9 minutos
- **Resultado:** `perf/results/summary-stress.json`

### Escenario 4 — Spike (picos)

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e SCENARIO=spike \
  perf/scripts/register_person_k6.js
```

- **VUs:** 50 → pico a 300 en 1 min → recuperación a 50 en 2 min → 0 en 1 min
- **Duración:** ~4 minutos
- **Resultado:** `perf/results/summary-spike.json`

### Escenario 5 — Soak (resistencia)

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e SCENARIO=soak \
  perf/scripts/register_person_k6.js
```

- **VUs:** 100 constantes
- **Duración:** 2 horas
- **Resultado:** `perf/results/summary-soak.json`

> ⚠️ El escenario soak tarda 2 horas. Ejecutar on-demand, no en cada PR.

---

## 📊 Resultados obtenidos

| Métrica | Baseline | Carga | Estrés | Spike | Soak | SLO |
|---|---|---|---|---|---|---|
| p(95) latencia | **26.41 ms** | 455.51 ms | 541.56 ms | 410.9 ms | **269.9 ms** | ≤ 300 ms |
| p(99) latencia | **46.88 ms** | 616.48 ms | **741.86 ms** | **681.8 ms** | **433.5 ms** | ≤ 800 ms |
| Error rate HTTP | **0%** | **0%** | 1.72% | 2.89% | 4.18% | < 1% |
| register_failed | **0.32%** | **0.51%** | 1.72% | 2.89% | 4.18% | < 1% |
| Throughput | **1,759 req/s** | **2,303 req/s** | **2,346 req/s** | **2,104 req/s** | **1,934 req/s** | ≥ 100 req/s |
| VUs máximos | 20 | 200 | 600 | 300 | 100 | — |
| Total iteraciones | 527,777 | 1,934,551 | 1,266,784 | 504,897 | 13,923,758 | — |
| Duración | 5 min | 14 min | 9 min | 4 min | 2 horas | — |

### Análisis por escenario

**Baseline:** Todos los SLO cumplidos. p(95) de 26.41 ms y `register_failed` de 0.32%. Línea base establecida correctamente.

**Carga:** p(95) supera el SLO (455.51 ms vs 300 ms). p(99) dentro del límite. `register_failed` de 0.51%, por debajo del umbral.

**Estrés:** p(95) de 541.56 ms y p(99) de 741.86 ms (dentro del SLO de 800 ms tras la corrección del DEF-02). `register_failed` de 1.72%, supera el umbral. Sistema degradado pero funcional a 600 VUs.

**Spike:** El pico abrupto de 50 → 300 VUs genera p(95) de 410.9 ms y `register_failed` de 2.89%. El sistema no absorbe el pico sin degradación pero p(99) de 681.8 ms se mantiene dentro del SLO. La recuperación a 50 VUs se produce pero la tasa de error durante el pico es significativa.

**Soak:** Hallazgo crítico — tras 2 horas a 100 VUs el `http_req_failed` llega al 4.18% (582,604 requests con HTTP no-200). La latencia p(95) de 269.9 ms está dentro del SLO, pero la tasa de fallos HTTP revela degradación acumulada en el tiempo, consistente con agotamiento del pool de conexiones o fuga de recursos en H2. Documentado como DEF-03.

---

## Defectos encontrados

Ver [`defectos.md`](./defectos.md) para el registro completo con evidencias y análisis detallado.

| ID | Descripción breve | Escenario | Estado |
|---|---|---|---|
| DEF-01 | Body `DEAD` en personas `alive=false` — 7.1% de fallos | Baseline inicial | ✅ Resuelto |
| DEF-02 | Race condition en `existsById + save` sin atomicidad | Baseline / Carga / Estrés | ✅ Resuelto |
| DEF-03 | Degradación acumulada en soak — `http_req_failed` al 4.18% tras 2h | Soak (100 VUs, 2h) | 🔴 Abierto |

---

## 🔁 Integración continua

El pipeline en `ci/perf-pipeline.yml` ejecuta automáticamente:

- **En cada PR:** escenario baseline como gate de calidad
- **On-demand:** escenarios de carga, estrés y spike vía `workflow_dispatch`
- **Programado (semanal):** escenario soak para detectar degradación acumulada
- **Gate automático:** el pipeline falla si `p(95) > 300 ms` o `register_failed > 1%`
