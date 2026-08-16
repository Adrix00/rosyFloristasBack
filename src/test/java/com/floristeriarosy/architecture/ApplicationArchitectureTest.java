package com.floristeriarosy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas arquitectónicas de la capa Application.
 *
 * <p>La capa Application implementa los casos de uso del sistema.
 *
 * <p>Su única responsabilidad es orquestar el dominio mediante Input Ports y Output Ports,
 * permaneciendo completamente independiente de los detalles tecnológicos.
 *
 * <p>Application nunca debe conocer cómo llegan las peticiones (REST, GraphQL, Scheduler...) ni
 * cómo se persisten los datos (JPA, JDBC, MongoDB...).
 */
@AnalyzeClasses(
    packages = "com.floristeriarosy",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationArchitectureTest {

  /**
   * Application nunca debe depender de la capa Web.
   *
   * <p>Motivo: Los casos de uso no deben conocer HTTP, JSON, REST ni ningún otro mecanismo de
   * entrada.
   *
   * <p>Gracias a esta regla podremos reutilizar exactamente los mismos casos de uso desde cualquier
   * adaptador de entrada.
   */
  @ArchTest
  static final ArchRule application_should_not_depend_on_web =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..infrastructure.web..");

  /**
   * Application nunca debe depender de entidades de persistencia.
   *
   * <p>Motivo: Las entidades JPA representan la estructura física de la base de datos.
   *
   * <p>Los casos de uso deben trabajar únicamente con el modelo de dominio.
   */
  @ArchTest
  static final ArchRule application_should_not_depend_on_persistence_entities =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..infrastructure.persistence.entity..");

  /**
   * Application nunca debe depender de implementaciones concretas de persistencia.
   *
   * <p>Motivo: Application únicamente conoce los Output Ports.
   *
   * <p>Si mañana cambiamos JPA por MyBatis, MongoDB o cualquier otra tecnología, los casos de uso
   * permanecerán exactamente iguales.
   */
  @ArchTest
  static final ArchRule application_should_not_depend_on_jpa_and_jdbc =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..infrastructure.persistence.jpa..", "..infrastructure.persistence.jdbc..");

  /**
   * Application nunca debe depender de los Persistence Adapters.
   *
   * <p>Motivo: Los adapters implementan los Output Ports definidos por Application.
   *
   * <p>La dirección de la dependencia siempre debe ir desde Infrastructure hacia Application y
   * nunca al revés.
   */
  @ArchTest
  static final ArchRule application_should_not_depend_on_adapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..infrastructure.persistence.adapter..");
}
