package com.floristeriarosy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas arquitectónicas de la capa Domain.
 *
 * <p>El dominio representa el núcleo del negocio y debe permanecer completamente independiente de
 * frameworks y detalles tecnológicos.
 *
 * <p>Estas reglas garantizan que la lógica de negocio pueda evolucionar sin depender de Spring
 * Boot, la base de datos o cualquier otra tecnología.
 */
@AnalyzeClasses(
    packages = "com.floristeriarosy",
    importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {

  /**
   * El dominio nunca debe depender de Spring.
   *
   * <p>Motivo: El modelo de negocio debe ser completamente independiente del framework. Esto nos
   * permite cambiar Spring Boot por otro framework (Quarkus, Micronaut, etc.) sin modificar la
   * lógica de negocio.
   *
   * <p>Ejemplos prohibidos: - @Service - @Component - @Transactional - @Autowired
   */
  @ArchTest
  static final ArchRule domain_should_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");

  /**
   * El dominio nunca debe conocer la infraestructura.
   *
   * <p>Motivo: El dominio no debe saber si los datos se almacenan en PostgreSQL, MongoDB o
   * cualquier otro sistema.
   *
   * <p>Toda comunicación con el exterior debe realizarse mediante puertos definidos en Application.
   */
  @ArchTest
  static final ArchRule domain_should_not_depend_on_infrastructure =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..infrastructure..");

  /**
   * El dominio nunca debe depender de la capa Application.
   *
   * <p>Motivo: El dominio representa únicamente reglas de negocio. No debe conocer casos de uso,
   * comandos, consultas ni servicios de aplicación.
   *
   * <p>De esta forma el dominio puede reutilizarse desde cualquier caso de uso sin quedar acoplado
   * a ellos.
   */
  @ArchTest
  static final ArchRule domain_should_not_depend_on_application =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..");

  /**
   * El dominio nunca debe depender de la capa Web.
   *
   * <p>Motivo: El dominio no conoce HTTP, REST, JSON ni DTOs.
   *
   * <p>Si en un futuro cambiamos REST por GraphQL o gRPC, esta regla garantiza que el modelo de
   * negocio no necesite modificarse.
   */
  @ArchTest
  static final ArchRule domain_should_not_depend_on_web =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..web..");
}
