# Taller de Pruebas de Carga y Rendimiento
## [NOMBRE DE TU SISTEMA / API]
<!-- Ej: "Sistema de Registro de Votantes — Spring Boot API" -->

> **Curso:** Testing y Validación de Software  
> **Programa:** Maestría en Ingeniería de Software — Universidad de La Sabana  
> **Autor:** [TU NOMBRE COMPLETO]  
> **Año:** 2025

---

## 📋 Descripción del sistema bajo prueba

<!-- 
    Escribe 2-3 oraciones describiendo qué hace tu API.
    Ej: "API REST desarrollada en Spring Boot que permite registrar votantes
    mediante el endpoint POST /register. Valida reglas de negocio como
    mayoría de edad, estado de vida y duplicados antes de persistir en BD."
-->

[DESCRIPCIÓN DE TU SISTEMA AQUÍ]

---

## 🎯 SLA / SLO definidos

<!-- 
    Estos son los criterios de aceptación de tus pruebas.
    Puedes usar los del taller o ajustarlos a tu máquina.
    IMPORTANTE: estos valores deben coincidir con los thresholds de tu script k6.
-->

| Métrica | Objetivo (SLO) | Justificación |
|---|---|---|
| p(95) latencia | ≤ 300 ms | [Ej: estándar para APIs REST interactivas] |
| p(99) latencia | ≤ 800 ms | [Ej: tolerancia máxima antes de timeout de cliente] |
| Error rate HTTP | < 1% | [Ej: confiabilidad mínima aceptable en producción] |
| register_failed | < 1% | [Ej: validaciones de negocio deben pasar en > 99%] |
| Throughput mínimo | ≥ 100 req/s | [Ej: demanda esperada en horario pico] |

---

## 📁 Estructura del proyecto

```
perf/
 ├─ scripts/
 │   └─ register_person_k6.js     # Script principal de pruebas k6
 ├─ data/
 │   └─ persons.csv               # Dataset de prueba (200+ filas)
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

<!-- 
    Lista las versiones exactas que usaste.
    Ejecuta "k6 version" y "java -version" para obtenerlas.
-->

| Herramienta | Versión | Uso |
|---|---|---|
| k6 | [EJ: v0.55.0] | Ejecución de pruebas de carga |
| Java / Spring Boot | [EJ: 17 / 3.2.0] | Sistema bajo prueba |
| Python | [EJ: 3.11] | Generación del dataset CSV |

---

## ⚙️ Pre-requisitos

Antes de ejecutar las pruebas asegúrate de tener:

1. **Servicio corriendo** en `http://localhost:8080`
2. **k6 instalado** — verifica con `k6 version`
3. **Dataset generado** en `perf/data/persons.csv`

### Verificar que el endpoint responde

```bash
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Ana\",\"id\":1,\"age\":30,\"gender\":\"FEMALE\",\"alive\":true}"
```

✅ Respuesta esperada: `200 OK` con body `VALID`

### Generar el dataset (si no lo tienes)

```bash
python generate_persons.py --rows 200 --output perf/data/persons.csv
```

---

## ▶️ Ejecución de escenarios

<!-- 
    IMPORTANTE: ejecuta SIEMPRE desde la raíz del repositorio,
    no desde dentro de perf/scripts/. De lo contrario las rutas
    del CSV y de los resultados no funcionarán.
-->

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
- **VUs:** rampa 0 → 600 VUs progresivamente
- **Duración:** ~10 minutos
- **Resultado:** `perf/results/summary-stress.json`

---

## 📊 Resultados obtenidos

<!--
    Completa esta tabla DESPUÉS de correr los tres escenarios.
    Los valores los encuentras en los archivos summary-*.json
    o en la salida de consola de k6.
    Ejemplo de dónde leer cada valor:
      - p(95): metrics > http_req_duration > values > p(95)
      - error rate: metrics > register_failed > values > rate  (multiplica x100 para %)
      - req/s: metrics > http_reqs > values > rate
-->

| Métrica | Baseline | Carga | Estrés | SLO | ¿Cumple? |
|---|---|---|---|---|---|
| p(95) latencia | [X ms] | [X ms] | [X ms] | ≤ 300 ms | ✅/❌ |
| p(99) latencia | [X ms] | [X ms] | [X ms] | ≤ 800 ms | ✅/❌ |
| Error rate HTTP | [X%] | [X%] | [X%] | < 1% | ✅/❌ |
| register_failed | [X%] | [X%] | [X%] | < 1% | ✅/❌ |
| Throughput | [X req/s] | [X req/s] | [X req/s] | ≥ 100 req/s | ✅/❌ |
| VUs máximos | 20 | 200 | 600 | — | — |

---

## 🐛 Defectos encontrados

<!-- 
    Referencia directa a tu archivo defectos.md.
    Pon aquí solo un resumen; el detalle va en el archivo.
-->

Ver [`defectos.md`](./defectos.md) para el registro completo.

| ID | Descripción breve | Escenario | Estado |
|---|---|---|---|
| DEF-01 | [Ej: Body DEAD en personas alive=false] | Baseline | Abierto |
| DEF-02 | [Ej: Race condition en existsById + save] | Baseline | Abierto |

---

## 🔁 Integración continua

<!-- 
    Describe brevemente qué hace tu pipeline.
    El archivo completo está en ci/perf-pipeline.yml
-->

El pipeline en `ci/perf-pipeline.yml` ejecuta automáticamente:

- **En cada PR:** escenario baseline como gate de calidad
- **On-demand:** escenarios de carga y estrés
- **Gate automático:** el pipeline falla si `p(95) > 300ms` o `register_failed > 1%`

---

## 📚 Recursos

- [k6 Documentation](https://docs.k6.io)
- [Google SRE Book — SLOs](https://sre.google/sre-book/service-level-objectives/)
- [Spring Boot Performance Tuning](https://spring.io/guides)
