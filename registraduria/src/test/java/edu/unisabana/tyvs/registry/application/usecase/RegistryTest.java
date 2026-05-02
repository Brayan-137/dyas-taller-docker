<<<<<<< HEAD
package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRepository;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pruebas de integración para el caso de uso {@link Registry} con base de datos H2.
 *
 * <p>Se valida la interacción real entre el caso de uso y el repositorio,
 * comprobando que los datos se persisten correctamente.</p>
 *
 * <p>Formato: <b>AAA (Arrange – Act – Assert)</b></p>
 */
public class RegistryTest {

    private RegistryRepositoryPort repo;
    private Registry registry;

    /**
     * Configuración antes de cada prueba:
     * - Crea BD H2 en memoria
     * - Inicializa el esquema
     * - Limpia datos previos para aislar cada test
     */
    @Before
    public void setup() throws Exception {
        String jdbc = "jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1";
        repo = new RegistryRepository(jdbc);
        repo.initSchema();
        repo.deleteAll();
        registry = new Registry(repo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 1 – Persona válida
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Caso: una persona adulta, viva y con ID positivo debe registrarse como VALID.
     * También se verifica que el registro persiste realmente en H2.
     */
    @Test
    public void shouldRegisterValidPerson() throws Exception {
        // Arrange
        Person p = new Person("Ana", 100, 30, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert – resultado de negocio
        assertEquals(RegisterResult.VALID, result);
        // Assert – persistencia real en H2
        assertTrue("El registro debe existir en la BD", repo.existsById(100));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 2 – Persona duplicada
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Caso: registrar dos personas con el mismo ID.
     * La primera debe ser VALID; la segunda debe ser DUPLICATED.
     * Se verifica que sólo hay un registro en la BD.
     */
    @Test
    public void shouldPersistValidVoterAndRejectDuplicates() throws Exception {
        // Arrange
        Person p1 = new Person("Ana", 200, 30, Gender.FEMALE, true);
        Person p2 = new Person("AnaDos", 200, 40, Gender.FEMALE, true);

        // Act – primer registro
        RegisterResult result1 = registry.registerVoter(p1);

        // Assert – primer registro válido
        assertEquals(RegisterResult.VALID, result1);
        assertTrue(repo.existsById(200));

        // Act – segundo registro con el mismo ID
        RegisterResult result2 = registry.registerVoter(p2);

        // Assert – segundo registro rechazado
        assertEquals(RegisterResult.DUPLICATED, result2);
        // Solo debe existir UN registro (el original)
        assertTrue(repo.existsById(200));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 3 – Menor de edad
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Caso: persona con edad menor a 18 años debe ser rechazada como UNDERAGE.
     * Además se verifica que NO se persistió ningún registro en H2.
     */
    @Test
    public void shouldRejectUnderagePerson() throws Exception {
        // Arrange
        Person p = new Person("Carlos", 301, 17, Gender.MALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert – resultado de negocio
        assertEquals(RegisterResult.UNDERAGE, result);
        // Assert – no debe haberse persistido en H2
        assertFalse("No debe existir registro de menor de edad", repo.existsById(301));
    }

    /**
     * Caso borde: persona exactamente con 18 años debe ser VALID.
     */
    @Test
    public void shouldAcceptPersonWithExactly18Years() throws Exception {
        // Arrange
        Person p = new Person("Sofia", 302, 18, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert
        assertEquals(RegisterResult.VALID, result);
        assertTrue(repo.existsById(302));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 4 – Persona fallecida
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Caso: persona con alive=false debe ser rechazada como DEAD.
     * No debe haber ningún INSERT en la BD.
     */
    @Test
    public void shouldRejectDeadPerson() throws Exception {
        // Arrange
        Person p = new Person("Luis", 401, 40, Gender.MALE, false);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert – resultado de negocio
        assertEquals(RegisterResult.DEAD, result);
        // Assert – no debe existir en H2
        assertFalse("No debe existir registro de persona fallecida", repo.existsById(401));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 5 – ID inválido
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Caso: persona con ID = 0 debe ser rechazada como INVALID.
     */
    @Test
    public void shouldRejectPersonWithZeroId() throws Exception {
        // Arrange
        Person p = new Person("María", 0, 25, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert
        assertEquals(RegisterResult.INVALID, result);
        assertFalse(repo.existsById(0));
    }

    /**
     * Caso: persona con ID negativo debe ser rechazada como INVALID.
     */
    @Test
    public void shouldRejectPersonWithNegativeId() throws Exception {
        // Arrange
        Person p = new Person("María", -5, 25, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert
        assertEquals(RegisterResult.INVALID, result);
    }

    /**
     * Caso: persona null debe ser rechazada como INVALID.
     */
    @Test
    public void shouldRejectNullPerson() throws Exception {
        // Arrange – persona nula (simulación de error en entrada)

        // Act
        RegisterResult result = registry.registerVoter(null);

        // Assert
        assertEquals(RegisterResult.INVALID, result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASO 6 – Múltiples registros válidos independientes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Caso: dos personas con distintos IDs deben registrarse ambas como VALID.
     */
    @Test
    public void shouldRegisterMultipleValidPersons() throws Exception {
        // Arrange
        Person p1 = new Person("Ana",   601, 30, Gender.FEMALE, true);
        Person p2 = new Person("Pedro", 602, 45, Gender.MALE,   true);

        // Act
        RegisterResult r1 = registry.registerVoter(p1);
        RegisterResult r2 = registry.registerVoter(p2);

        // Assert
        assertEquals(RegisterResult.VALID, r1);
        assertEquals(RegisterResult.VALID, r2);
        assertTrue(repo.existsById(601));
        assertTrue(repo.existsById(602));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETO – Simular fallo de conexión
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reto: al usar una URL de BD inválida, la operación lanza una excepción.
     * El caso de uso debe propagar (o envolver) el error de forma controlada.
     */
    @Test(expected = IllegalStateException.class)
    public void shouldThrowWhenDatabaseIsUnreachable() {
        // Arrange – URL de BD inválida
        RegistryRepositoryPort badRepo = new RegistryRepository("jdbc:h2:mem:nonexistent_db_xyz_000");
        Registry registryBad = new Registry(badRepo);
        Person p = new Person("Test", 999, 30, Gender.MALE, true);

        // Act – debe lanzar excepción porque la tabla no existe
        registryBad.registerVoter(p);
    }
}
=======
package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRepository;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pruebas de integración para el caso de uso {@link Registry}, aplicando el formato AAA:
 * <ul>
 *   <li><b>Arrange</b>: preparación de datos y objetos a probar.</li>
 *   <li><b>Act</b>: ejecución del método bajo prueba.</li>
 *   <li><b>Assert</b>: verificación de los resultados esperados.</li>
 * </ul>
 */
public class RegistryTest {

    private RegistryRepositoryPort repo;
    private Registry registry;

    /**
     * Arrange común a todos los tests:
     * <ul>
     *   <li>Instancia un repositorio H2 en memoria.</li>
     *   <li>Inicializa el esquema (tabla) y limpia datos previos.</li>
     *   <li>Construye el caso de uso inyectando el repositorio.</li>
     * </ul>
     */
    @Before
    public void setup() throws Exception {
        String jdbc = "jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1";
        repo = new RegistryRepository(jdbc);

        repo.initSchema();   // Arrange: crear tabla
        repo.deleteAll();    // Arrange: limpiar datos previos

        registry = new Registry(repo); // Arrange: inyectar dependencia
    }

    /**
     * Caso de prueba:
     * <p>Una persona válida debe ser registrada exitosamente.</p>
     */
    @Test
    public void shouldRegisterValidPerson() throws Exception {
        // Arrange
        Person p1 = new Person("Ana", 100, 30, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p1);

        // Assert
        assertEquals(RegisterResult.VALID, result);
        assertTrue(repo.existsById(100));
    }

    /**
     * Caso de prueba:
     * <p>Al intentar registrar dos personas con el mismo ID:</p>
     * <ul>
     *   <li>La primera se guarda como válida.</li>
     *   <li>La segunda es rechazada como duplicada.</li>
     * </ul>
     */
    @Test
    public void shouldPersistValidVoterAndRejectDuplicates() throws Exception {
        // Arrange
        Person p1 = new Person("Ana", 100, 30, Gender.FEMALE, true);
        Person p2 = new Person("AnaDos", 100, 40, Gender.FEMALE, true);

        // Act (primer registro)
        RegisterResult result1 = registry.registerVoter(p1);

        // Assert primer registro
        assertEquals(RegisterResult.VALID, result1);
        assertTrue(repo.existsById(100));

        // Act (segundo registro con mismo ID)
        RegisterResult result2 = registry.registerVoter(p2);

        // Assert segundo registro
        assertEquals(RegisterResult.DUPLICATED, result2);
    }
}
>>>>>>> fd314fea567d9dc83567bd91d654a3d36b51d9dc
