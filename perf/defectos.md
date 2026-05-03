# Registro de Defectos — Taller de Pruebas de Carga y Rendimiento

---

## Tabla de defectos (bug tracking)

| ID | Caso de Prueba | Entrada | Resultado Esperado | Resultado Obtenido | Causa Probable | Estado |
|---|---|---|---|---|---|---|
| DEF-01 | Baseline — POST /register con personas `alive=false` | `{ "name": "Juan", "id": 101, "age": 28, "gender": "MALE", "alive": false }` (~5% del CSV original) | HTTP 200, body `VALID`, `register_failed < 1%` | HTTP 200, body `DEAD`, `register_failed = 7.1%` (21,888 fallos de 307,928 requests) | `registerVoter()` retorna `RegisterResult.DEAD` cuando `isAlive() == false`. El CSV de prueba incluía ~5% de filas con `alive=false`, generando fallos sistemáticos de validación de negocio | Resuelto |
| DEF-02 | Baseline / Carga / Estrés — POST /register con VUs concurrentes | Múltiples requests simultáneas con IDs coincidentes generados por `buildUniqueId()` entre diferentes VUs en la misma iteración | Inserción atómica: una request retorna `VALID`, las concurrentes detectan duplicado antes del intento de escritura | Body `DUPLICATED` en requests que pasan simultáneamente el `existsById()` antes de que cualquiera ejecute `save()`. Tasa de fallo: 0.34% (baseline), 0.77% (carga 200 VUs), 1.96% (estrés 600 VUs) | Bloque `existsById + save` en `registerVoter()` no está envuelto en transacción atómica. Bajo concurrencia, el check y la escritura no son una operación única, lo que permite que dos hilos pasen el guard simultáneamente con el mismo ID. El problema escala proporcionalmente con los VUs | Abierto |

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

### DEF-02 — Abierto
**Propuesta de corrección:**
```java
// Opción 1: Manejar la excepción de constraint único (recomendada)
try {
    repo.save(p.getId(), p.getName(), p.getAge(), p.isAlive());
    return RegisterResult.VALID;
} catch (DataIntegrityViolationException e) {
    return RegisterResult.DUPLICATED;
}

// Opción 2: @Transactional con nivel SERIALIZABLE
@Transactional(isolation = Isolation.SERIALIZABLE)
public RegisterResult registerVoter(Person p) { ... }
```
**Impacto observado:** La tasa de fallo escala con la concurrencia — 0.34% a 20 VUs, 0.77% a 200 VUs, 1.96% a 600 VUs. En producción con alta concurrencia superaría el SLO de 1%.
