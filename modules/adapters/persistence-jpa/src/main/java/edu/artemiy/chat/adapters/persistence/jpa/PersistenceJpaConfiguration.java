package edu.artemiy.chat.adapters.persistence.jpa;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = PersistenceJpaModule.class)
@EnableJpaRepositories(basePackageClasses = PersistenceJpaModule.class)
public class PersistenceJpaConfiguration {
}
