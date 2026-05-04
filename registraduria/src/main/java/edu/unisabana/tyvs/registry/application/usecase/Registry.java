package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import org.springframework.dao.DataIntegrityViolationException;

public class Registry {

    private final RegistryRepositoryPort repo;

    public Registry(RegistryRepositoryPort repo) {
        this.repo = repo;
    }

    public RegisterResult registerVoter(Person p) {
        // 1. Validaciones de Negocio (Fail-fast)
        if (p == null || p.getId() <= 0)
            return RegisterResult.INVALID;
        if (!p.isAlive())
            return RegisterResult.DEAD;
        if (p.getAge() < 18)
            return RegisterResult.UNDERAGE;

        try {
            /* 
               ELIMINACIÓN DEL CHECK-THEN-ACT:
               No llamamos a existsById(). Intentamos persistir directamente.
               Esto resuelve la condición de carrera donde dos hilos pasaban 
               el check simultáneamente antes de que el primero guardara.
            */
            repo.save(p.getId(), p.getName(), p.getAge(), p.isAlive());
            return RegisterResult.VALID;
            
        } catch (DataIntegrityViolationException e) {
            // Captura específica de violación de unicidad en la DB (ID duplicado)
            return RegisterResult.DUPLICATED;
            
        } catch (Exception e) {
            // Manejo de errores inesperados de infraestructura
            throw new IllegalStateException("Error de persistencia no controlado: " 
                + e.getClass().getSimpleName(), e);
        }
    }
}