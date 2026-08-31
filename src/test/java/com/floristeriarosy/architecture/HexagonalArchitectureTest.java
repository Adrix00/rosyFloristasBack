package com.floristeriarosy.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas específicas de la Arquitectura Hexagonal.
 *
 * <p>Estas reglas representan decisiones propias del proyecto RosyFloristas.
 */
@AnalyzeClasses(
    packages = "com.floristeriarosy",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  // -------------------------------------------------------------------------
  // Controllers
  // -------------------------------------------------------------------------

  /** Los Controllers únicamente conocen Input Ports. */
  @ArchTest
  static final ArchRule controllers_should_only_access_input_ports =
      classes()
          .that()
          .resideInAPackage("..infrastructure.web.controller..")
          .should()
          .onlyDependOnClassesThat()
          .resideOutsideOfPackage("..application.port.out..");

  /** Nunca pueden acceder al dominio. */
  @ArchTest
  static final ArchRule controllers_should_not_access_domain =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.web.controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain..");

  /** Nunca pueden acceder a Persistence Adapter. */
  @ArchTest
  static final ArchRule controllers_should_not_access_persistence_adapter =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.web.controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure.persistence.adapter..");

  /** Nunca pueden acceder a Output Ports. */
  @ArchTest
  static final ArchRule controllers_should_not_access_output_ports =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.web.controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..application..port.out..");

  // -------------------------------------------------------------------------
  // Services
  // -------------------------------------------------------------------------

  /**
   * Los Services únicamente implementan Input Ports.
   *
   * <p>Cada Service deberá implementar exactamente un Input Port (UseCase) según ADR-001.
   */
  @ArchTest
  static final ArchRule services_should_implement_use_cases =
      classes()
          .that()
          .resideInAPackage("..application..service..")
          .should()
          .implement(INTERFACES.and(resideInAPackage("..application..port.in..")));

  /** Ningún Service debe depender de un Adapter. */
  @ArchTest
  static final ArchRule services_should_not_access_adapters =
      noClasses()
          .that()
          .resideInAPackage("..application..service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure.persistence.adapter..");

  // -------------------------------------------------------------------------
  // Persistence Adapter
  // -------------------------------------------------------------------------

  /** Los Adapters implementan Output Ports. */
  @ArchTest
  static final ArchRule adapters_should_implement_output_ports =
      classes()
          .that()
          .resideInAPackage("..infrastructure.persistence.adapter..")
          .should()
          .implement(INTERFACES.and(resideInAPackage("..application..port.out..")));

  /** Los Adapters nunca deben acceder a Controllers. */
  @ArchTest
  static final ArchRule adapters_should_not_access_web =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.persistence.adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure.web..");

  // -------------------------------------------------------------------------
  // Ports
  // -------------------------------------------------------------------------

  /** Los Output Ports deben ser interfaces. */
  @ArchTest
  static final ArchRule output_ports_should_be_interfaces =
      classes().that().resideInAPackage("..application..port.out..").should().beInterfaces();

  /** Los Input Ports deben ser interfaces. */
  @ArchTest
  static final ArchRule input_ports_should_be_interfaces =
      classes().that().resideInAPackage("..application..port.in..").should().beInterfaces();
}
