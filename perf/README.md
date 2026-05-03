# Taller de Pruebas de Carga y Rendimiento

> **Curso:** Diseño y Arquitectura de Software <br>
> **Programa:** Ingeniería Informática — Universidad de La Sabana <br>
> **Año:** 2026 <br>
> **Equipo:**
> - Brayan Presiga Sepulveda - 0000301424
> - Juan David Sanchez Roldan - 0000340321
> - Yuly Dayana Rodríguez Salcedo - 0000305314

## Sistema de Registro de Votantes — Spring Boot API

---

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
 │   └─ persons.csv               # Dataset de prueba (200 filas)
 ├─ results/
 │   ├─ summary-baseline.json     # Resultado escenario baseline
 │   ├─ summary-load.json         # Resultado escenario carga
 │   └─ summary-stress.json       # Resultado escenario estrés
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

---

## 📊 Resultados obtenidos (post-corrección DEF-02)

| Métrica | Baseline | Carga | Estrés | SLO | ¿Cumple global? |
|---|---|---|---|---|---|
| p(95) latencia | **26.41 ms** | 455.51 ms | 541.56 ms | ≤ 300 ms | ❌ |
| p(99) latencia | **46.88 ms** | 616.48 ms | **741.86 ms** | ≤ 800 ms | ✅ |
| Error rate HTTP | **0.32%** | **0.51%** | 1.72% | < 1% | ❌ |
| register_failed | **0.32%** | **0.51%** | 1.72% | < 1% | ❌ |
| Throughput | **1,759 req/s** | **2,303 req/s** | **2,346 req/s** | ≥ 100 req/s | ✅ |
| VUs máximos | 20 | 200 | 600 | — | — |
| Total iteraciones | 527,777 | 1,934,551 | 1,266,784 | — | — |

### Análisis por escenario

**Baseline:** Todos los SLO cumplidos. p(95) de **26.41 ms** con amplio margen sobre el límite de 300 ms. p(99) de **46.88 ms**, muy por debajo del límite de 800 ms. `register_failed` de **0.32%**, bajo el umbral de 1%. Línea base establecida correctamente.

**Carga:** p(95) supera el SLO (455.51 ms vs 300 ms). p(99) de **616.48 ms**, dentro del límite de 800 ms. `register_failed` de **0.51%**, por debajo del umbral de 1% y mejorado respecto a la ejecución pre-corrección (0.77%). La corrección del DEF-02 redujo los fallos de negocio en carga, aunque el cuello de botella de latencia de escritura en H2 persiste.

**Estrés:** Sistema bajo presión evidente a 600 VUs. p(95) de **541.56 ms** y p(99) de **741.86 ms** — este último ahora **dentro del SLO de 800 ms** (mejora de 248 ms respecto a la ejecución anterior donde era 990.1 ms). `register_failed` de **1.72%**, mejorado respecto al 1.96% pre-corrección pero aún por encima del umbral de 1% bajo carga extrema. El throughput mejoró un **13.3%** (de 2,071 a 2,346 req/s), evidenciando el impacto positivo de eliminar la doble consulta a BD.

---

## 🐛 Defectos encontrados

Ver [`defectos.md`](./defectos.md) para el registro completo con evidencias y análisis detallado.

| ID | Descripción breve | Escenario | Estado |
|---|---|---|---|
| DEF-01 | Body `DEAD` en personas `alive=false` — 7.1% de fallos | Baseline inicial | ✅ Resuelto |
| DEF-02 | Race condition en `existsById + save` sin atomicidad | Baseline / Carga / Estrés | ✅ Resuelto |

---

## 🔁 Integración continua

El pipeline en `ci/perf-pipeline.yml` ejecuta automáticamente:

- **En cada PR:** escenario baseline como gate de calidad
- **On-demand:** escenarios de carga y estrés vía `workflow_dispatch`
- **Gate automático:** el pipeline falla si `p(95) > 300 ms` o `register_failed > 1%`
