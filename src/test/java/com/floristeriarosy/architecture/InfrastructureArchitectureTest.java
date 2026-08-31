package com.floristeriarosy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas arquitectónicas de la capa Infrastructure.
 *
 * <p>Infrastructure contiene exclusivamente adaptadores tecnológicos.
 *
 * <p>Su responsabilidad es adaptar el mundo exterior al modelo de la aplicación, implementando los
 * Output Ports y exponiendo los mecanismos de entrada (REST, Scheduler, Security, etc.).
 *
 * <p>Nunca debe contener reglas de negocio.
 */
@AnalyzeClasses(
    packages = "com.floristeriarosy",
    importOptions = ImportOption.DoNotIncludeTests.class)
class InfrastructureArchitectureTest {

  /**
   * Persistence nunca debe depender directamente de Commands.
   *
   * <p>Motivo: Los Commands pertenecen exclusivamente a la capa Application y representan
   * peticiones internas de un caso de uso; los Adapters de persistencia solo conocen el dominio.
   *
   * <p>El Controller sí construye Commands a partir del Request (ADR-001, flujo de comunicación):
   * esta regla se acota a persistence para no contradecirlo.
   */
  @ArchTest
  static final ArchRule persistence_should_not_depend_on_commands =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.persistence..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..application..command..");

  /**
   * Persistence nunca debe depender directamente de Queries.
   *
   * <p>Motivo: Las Queries forman parte del contrato interno de Application; los Adapters de
   * persistencia solo conocen el dominio.
   *
   * <p>El Controller sí construye Queries a partir del Request (ADR-001, flujo de comunicación):
   * esta regla se acota a persistence para no contradecirlo.
   */
  @ArchTest
  static final ArchRule persistence_should_not_depend_on_queries =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.persistence..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..application..query..");

  /**
   * Infrastructure nunca debe depender directamente de los Services.
   *
   * <p>Motivo: Los Services son la implementación interna de los casos de uso.
   *
   * <p>Infrastructure siempre debe depender de las interfaces públicas (Input Ports), dejando que
   * Spring resuelva la implementación.
   */
  @ArchTest
  static final ArchRule infrastructure_should_not_depend_on_application_services =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..application..service..");
}
