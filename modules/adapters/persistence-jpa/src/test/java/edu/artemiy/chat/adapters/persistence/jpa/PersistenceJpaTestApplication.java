package edu.artemiy.chat.adapters.persistence.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(PersistenceJpaConfiguration.class)
@ComponentScan("edu.artemiy.chat.adapters.persistence.jpa.identity")
public class PersistenceJpaTestApplication {
}
