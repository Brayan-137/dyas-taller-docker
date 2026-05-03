"""
generate_persons.py
-------------------
Genera el archivo perf/data/persons.csv con datos sintéticos
para las pruebas de carga k6 del taller de rendimiento.

Uso:
    python generate_persons.py                  # 200 filas (por defecto)
    python generate_persons.py --rows 500       # cantidad personalizada
    python generate_persons.py --rows 200 --output perf/data/persons.csv
    python generate_persons.py --seed 42        # resultado reproducible
"""

import csv
import random
import argparse
import os
from pathlib import Path

# ── Datos de prueba ──────────────────────────────────────────────────────────

MALE_NAMES = [
    "Juan", "Carlos", "Andrés", "Miguel", "David", "Luis", "Jorge",
    "Alejandro", "Sebastián", "Felipe", "Daniel", "Diego", "Nicolás",
    "Camilo", "Ricardo", "Gabriel", "Eduardo", "Fernando", "Alberto",
    "Héctor", "Pablo", "Santiago", "Mateo", "Joaquín", "Manuel",
    "Arturo", "Rodrigo", "Samuel", "Óscar", "Rafael", "Iván", "Emilio",
    "Tomás", "Ernesto", "Mario", "Alfredo", "Ramón", "César", "Enrique",
    "Hugo", "Víctor", "Gustavo", "Javier", "Rubén", "Sergio", "Omar",
    "Raúl", "Ignacio", "Agustín", "Bernardo",
]

FEMALE_NAMES = [
    "María", "Ana", "Sofía", "Valentina", "Isabella", "Camila", "Daniela",
    "Alejandra", "Mariana", "Natalia", "Lucía", "Paula", "Sara", "Laura",
    "Juliana", "Andrea", "Carolina", "Valeria", "Gabriela", "Patricia",
    "Fernanda", "Elena", "Adriana", "Cristina", "Mónica", "Paola",
    "Diana", "Verónica", "Lorena", "Claudia", "Ximena", "Liliana",
    "Beatriz", "Gloria", "Rosa", "Marta", "Teresa", "Pilar", "Carmen",
    "Esperanza", "Nora", "Amparo", "Cecilia", "Irene", "Silvia",
    "Rebeca", "Yolanda", "Miriam", "Consuelo", "Dolores",
]

# ── Generador ────────────────────────────────────────────────────────────────

def generate_person(index: int, rng: random.Random) -> dict:
    """Genera una persona con datos variados y realistas."""
    is_male = rng.random() < 0.5
    name    = rng.choice(MALE_NAMES if is_male else FEMALE_NAMES)
    gender  = "MALE" if is_male else "FEMALE"
    age     = rng.randint(18, 65)
    alive   = "true"

    return {
        "id":     100 + index,
        "name":   name,
        "age":    age,
        "gender": gender,
        "alive":  alive,
    }


def generate_csv(rows: int, output: str, seed: int | None) -> None:
    rng = random.Random(seed)

    # Crea los directorios intermedios si no existen
    output_path = Path(output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    persons = [generate_person(i, rng) for i in range(1, rows + 1)]

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["id", "name", "age", "gender", "alive"])
        writer.writeheader()
        writer.writerows(persons)

    # ── Estadísticas de resumen ──────────────────────────────────────────────
    males   = sum(1 for p in persons if p["gender"] == "MALE")
    females = rows - males
    alive   = sum(1 for p in persons if p["alive"] == "true")
    ages    = [p["age"] for p in persons]
    avg_age = sum(ages) / len(ages)

    abs_path = output_path.resolve()

    print(f"\n✅  Archivo generado: {abs_path}")
    print(f"   Filas totales : {rows}")
    print(f"   MALE          : {males}  ({males/rows*100:.1f} %)")
    print(f"   FEMALE        : {females}  ({females/rows*100:.1f} %)")
    print(f"   alive=true    : {alive}  ({alive/rows*100:.1f} %)")
    print(f"   Edad promedio : {avg_age:.1f} años")
    print(f"   Rango de IDs  : 101 – {100 + rows}")
    if seed is not None:
        print(f"   Seed usada    : {seed}  (resultado reproducible)")
    print()

    # Vista previa de las primeras 5 filas
    print("── Vista previa (primeras 5 filas) ──────────────────────")
    print(f"{'id':<6} {'name':<14} {'age':<5} {'gender':<8} {'alive'}")
    print("-" * 48)
    for p in persons[:5]:
        print(f"{p['id']:<6} {p['name']:<14} {p['age']:<5} {p['gender']:<8} {p['alive']}")
    print("─" * 48)
    print(f"... y {rows - 5} filas más en el archivo.\n")


# ── CLI ──────────────────────────────────────────────────────────────────────

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Genera persons.csv para pruebas de carga con k6.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--rows",
        type=int,
        default=200,
        help="Número de filas a generar (default: 200, mínimo requerido por el taller).",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="perf/data/persons.csv",
        help="Ruta de salida del CSV (default: perf/data/persons.csv).",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="Semilla aleatoria para resultados reproducibles (opcional).",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()

    if args.rows < 1:
        raise SystemExit("❌  --rows debe ser mayor a 0.")

    generate_csv(rows=args.rows, output=args.output, seed=args.seed)
