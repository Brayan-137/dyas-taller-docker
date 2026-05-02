<<<<<<< HEAD
package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de integración con Mockito para {@link Registry}.
 *
 * <p>El repositorio es simulado para validar exclusivamente la lógica
 * del caso de uso, sin depender de infraestructura real.</p>
 *
 * <p>Formato: <b>AAA (Arrange – Act – Assert)</b> y BDD (Given–When–Then)</p>
 */
public class RegistryWithMockTest {

    private RegistryRepositoryPort repo;
    private Registry registry;

    @Before
    public void setUp() {
        repo = mock(RegistryRepositoryPort.class);
        registry = new Registry(repo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 1 – Duplicado detectado vía mock
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given: el mock indica que ID=7 ya existe.
     * When:  se intenta registrar a "Ana" con ID=7.
     * Then:  el resultado es DUPLICATED y save() NUNCA se invoca.
     */
    @Test
    public void shouldReturnDuplicatedWhenRepoSaysExists() throws Exception {
        // Arrange
        when(repo.existsById(7)).thenReturn(true);
        Person p = new Person("Ana", 7, 25, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert – resultado correcto
        assertEquals(RegisterResult.DUPLICATED, result);
        // Assert – save() no debe haberse llamado
        verify(repo, never()).save(anyInt(), anyString(), anyInt(), anyBoolean());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 2 – Persona válida: save() debe invocarse exactamente una vez
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given: el mock indica que ID=10 NO existe.
     * When:  se registra a "Pedro" (adulto, vivo).
     * Then:  el resultado es VALID y save() se invoca UNA sola vez con los datos correctos.
     */
    @Test
    public void shouldCallSaveWhenPersonIsValid() throws Exception {
        // Arrange
        when(repo.existsById(10)).thenReturn(false);
        Person p = new Person("Pedro", 10, 35, Gender.MALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert – resultado de negocio
        assertEquals(RegisterResult.VALID, result);
        // Assert – save() invocado exactamente una vez con los parámetros correctos
        verify(repo, times(1)).save(10, "Pedro", 35, true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 3 – Persona fallecida: ni existsById ni save se llaman
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given: persona con alive=false.
     * When:  se intenta registrar.
     * Then:  resultado DEAD, y el repositorio NO se consulta (la validación ocurre antes).
     */
    @Test
    public void shouldReturnDeadWithoutAccessingRepo() throws Exception {
        // Arrange
        Person p = new Person("Laura", 20, 40, Gender.FEMALE, false);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert
        assertEquals(RegisterResult.DEAD, result);
        // El repositorio no debe tocarse para personas fallecidas
        verify(repo, never()).existsById(anyInt());
        verify(repo, never()).save(anyInt(), anyString(), anyInt(), anyBoolean());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 4 – Menor de edad: el repo no debe invocarse
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given: persona con 15 años.
     * When:  se intenta registrar.
     * Then:  resultado UNDERAGE, sin interacción con el repositorio.
     */
    @Test
    public void shouldReturnUnderageWithoutAccessingRepo() throws Exception {
        // Arrange
        Person p = new Person("Juana", 30, 15, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert
        assertEquals(RegisterResult.UNDERAGE, result);
        verifyNoInteractions(repo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 5 – Excepción en save() → IllegalStateException propagado
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given: el mock simula que existsById devuelve false PERO save() lanza SQLException.
     * When:  se intenta registrar la persona.
     * Then:  el caso de uso no silencia el error, lo envuelve en IllegalStateException.
     */
    @Test(expected = IllegalStateException.class)
    public void shouldPropagateExceptionWhenSaveFails() throws Exception {
        // Arrange
        when(repo.existsById(50)).thenReturn(false);
        doThrow(new SQLException("Simulated DB error"))
                .when(repo).save(anyInt(), anyString(), anyInt(), anyBoolean());
        Person p = new Person("Roberto", 50, 28, Gender.MALE, true);

        // Act – debe lanzar IllegalStateException
        registry.registerVoter(p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 6 – Excepción en existsById()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given: existsById() lanza una excepción inesperada.
     * Then:  el caso de uso la convierte en IllegalStateException (no la silencia).
     */
    @Test(expected = IllegalStateException.class)
    public void shouldPropagateExceptionWhenExistsByIdFails() throws Exception {
        // Arrange
        when(repo.existsById(60)).thenThrow(new SQLException("Connection refused"));
        Person p = new Person("Camila", 60, 22, Gender.FEMALE, true);

        // Act
        registry.registerVoter(p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 7 – ID inválido: el repo no se consulta
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given: persona con ID=0.
     * Then:  resultado INVALID y ninguna interacción con el repositorio.
     */
    @Test
    public void shouldReturnInvalidForZeroIdWithoutHittingRepo() throws Exception {
        // Arrange
        Person p = new Person("Test", 0, 25, Gender.MALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert
        assertEquals(RegisterResult.INVALID, result);
        verifyNoInteractions(repo);
    }
}
=======
package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Clase de prueba unitaria para {@link Registry} utilizando un mock de {@link RegistryRepositoryPort}.
 *
 * <p>Estas pruebas ilustran cómo aislar el caso de uso del repositorio real,
 * aplicando dobles de prueba (Mockito) para simular los escenarios.</p>
 *
 * <p><b>Formato AAA:</b></p>
 * <ul>
 *   <li><b>Arrange</b>: se preparan datos y comportamiento del mock.</li>
 *   <li><b>Act</b>: se ejecuta el método bajo prueba.</li>
 *   <li><b>Assert</b>: se verifican resultados y que no haya interacciones no deseadas.</li>
 * </ul>
 *
 * <p><b>Beneficio:</b> este tipo de prueba es una <i>unitaria pura</i>,
 * sin necesidad de levantar bases de datos ni infraestructura adicional.</p>
 */
public class RegistryWithMockTest {

    /** Mock del puerto de persistencia. */
    private RegistryRepositoryPort repo;

    /** Caso de uso bajo prueba, instanciado con el mock. */
    private Registry registry;

    /**
     * Configura el mock y el caso de uso antes de cada prueba.
     *
     * <p>Se crea un mock de {@link RegistryRepositoryPort} usando Mockito
     * y se inyecta en la instancia de {@link Registry}.</p>
     */
    @Before
    public void setUp() {
        repo = mock(RegistryRepositoryPort.class);
        registry = new Registry(repo);
    }

    /**
     * Caso de prueba: detectar registros duplicados.
     *
     * <p><b>Escenario (BDD):</b></p>
     * <ul>
     *   <li><b>Given</b>: una persona con ID=7 y el repositorio ya indica que ese ID existe.</li>
     *   <li><b>When</b>: se intenta registrar la persona.</li>
     *   <li><b>Then</b>: el resultado debe ser {@link RegisterResult#DUPLICATED}
     *       y no se debe invocar el método {@code save(...)} en el repositorio.</li>
     * </ul>
     *
     * @throws Exception propagada en caso de error durante la ejecución.
     */
    @Test
    public void shouldReturnDuplicatedWhenRepoSaysExists() throws Exception {
        // Arrange: configurar mock y datos
        when(repo.existsById(7)).thenReturn(true);
        Person p = new Person("Ana", 7, 25, Gender.FEMALE, true);

        // Act: ejecutar método bajo prueba
        RegisterResult result = registry.registerVoter(p);

        // Assert: verificar resultado y comportamiento esperado del mock
        assertEquals(RegisterResult.DUPLICATED, result);
        verify(repo, never()).save(anyInt(), anyString(), anyInt(), anyBoolean());
    }
}
>>>>>>> fd314fea567d9dc83567bd91d654a3d36b51d9dc
