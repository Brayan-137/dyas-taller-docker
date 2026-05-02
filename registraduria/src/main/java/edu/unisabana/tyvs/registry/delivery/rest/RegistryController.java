<<<<<<< HEAD
// src/main/java/edu/unisabana/tyvs/registry/delivery/rest/RegistryController.java
package edu.unisabana.tyvs.registry.delivery.rest;

import edu.unisabana.tyvs.registry.application.usecase.Registry;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import edu.unisabana.tyvs.registry.domain.model.rq.PersonDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/register")
public class RegistryController {

    private final Registry registry;

    // Constructor explícito para inyección por constructor
    public RegistryController(Registry registry) {
        this.registry = registry;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String register(@RequestBody PersonDTO dto) {
        Person p = new Person(dto.getName(), dto.getId(), dto.getAge(),
                Gender.valueOf(dto.getGender()), dto.isAlive());
        RegisterResult r = registry.registerVoter(p);   
        return r.name();
    }
}
=======
// src/main/java/edu/unisabana/tyvs/registry/delivery/rest/RegistryController.java
package edu.unisabana.tyvs.registry.delivery.rest;

import edu.unisabana.tyvs.registry.application.usecase.Registry;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import edu.unisabana.tyvs.registry.domain.model.rq.PersonDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/register")
public class RegistryController {

    private final Registry registry;

    // Constructor explícito para inyección por constructor
    public RegistryController(Registry registry) {
        this.registry = registry;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String register(@RequestBody PersonDTO dto) {
        Person p = new Person(dto.getName(), dto.getId(), dto.getAge(),
                Gender.valueOf(dto.getGender()), dto.isAlive());
        RegisterResult r = registry.registerVoter(p);   
        return r.name();
    }
}
>>>>>>> fd314fea567d9dc83567bd91d654a3d36b51d9dc
