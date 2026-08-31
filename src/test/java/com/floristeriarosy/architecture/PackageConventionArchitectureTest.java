package com.floristeriarosy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de ubicación de clases.
 *
 * <p>A diferencia de NamingConventionArchitectureTest, que valida el nombre de las clases, este
 * test valida que cada tipo de clase resida en el paquete que le corresponde.
 *
 * <p>Gracias a ello todos los módulos mantienen exactamente la misma estructura.
 */
@AnalyzeClasses(
    packages = "com.floristeriarosy",
    importOptions = ImportOption.DoNotIncludeTests.class)
class PackageConventionArchitectureTest {

  @ArchTest
  static final ArchRule controllers_should_reside_in_controller_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .resideInAPackage("..infrastructure.web.controller..");

  @ArchTest
  static final ArchRule requests_should_reside_in_request_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Request")
          .should()
          .resideInAPackage("..infrastructure.web.request..");

  @ArchTest
  static final ArchRule responses_should_reside_in_response_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Response")
          .should()
          .resideInAPackage("..infrastructure.web.response..");

  @ArchTest
  static final ArchRule commands_should_reside_in_command_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Command")
          .should()
          .resideInAPackage("..application..command..");

  @ArchTest
  static final ArchRule queries_should_reside_in_query_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Query")
          .should()
          .resideInAPackage("..application..query..");

  @ArchTest
  static final ArchRule services_should_reside_in_service_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .should()
          .resideInAPackage("..application..service..");

  @ArchTest
  static final ArchRule use_cases_should_reside_in_port_in_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("UseCase")
          .should()
          .resideInAPackage("..application..port.in..");

  @ArchTest
  static final ArchRule output_ports_should_reside_in_port_out_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Port")
          .should()
          .resideInAPackage("..application..port.out..");

  @ArchTest
  static final ArchRule persistence_adapters_should_reside_in_adapter_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Adapter")
          .should()
          .resideInAPackage("..infrastructure.persistence.adapter..");

  @ArchTest
  static final ArchRule entities_should_reside_in_entity_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Entity")
          .should()
          .resideInAPackage("..infrastructure.persistence.entity..");

  @ArchTest
  static final ArchRule jpa_repositories_should_reside_in_jpa_repository_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("JpaRepository")
          .should()
          .resideInAPackage("..infrastructure.persistence.jpa..repository..");

  @ArchTest
  static final ArchRule jdbc_repositories_should_reside_in_jdbc_repository_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("JdbcRepository")
          .should()
          .resideInAPackage("..infrastructure.persistence.jdbc..repository..");

  @ArchTest
  static final ArchRule persistence_mappers_should_reside_in_persistence_mapper_package =
      classes()
          .that()
          .resideOutsideOfPackage("..infrastructure.web.mapper..")
          .and()
          .resideOutsideOfPackage("..application..mapper..")
          .and()
          .haveSimpleNameEndingWith("Mapper")
          .and()
          .haveSimpleNameNotEndingWith("RowMapper")
          .should()
          .resideInAPackage("..infrastructure.persistence.mapper..");

  @ArchTest
  static final ArchRule web_mappers_should_reside_in_web_mapper_package =
      classes()
          .that()
          .resideInAPackage("..infrastructure.web.mapper..")
          .should()
          .haveSimpleNameEndingWith("Mapper");

  @ArchTest
  static final ArchRule row_mappers_should_reside_in_row_mapper_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("RowMapper")
          .should()
          .resideInAPackage("..rowmapper..");

  @ArchTest
  static final ArchRule projections_should_reside_in_projection_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Projection")
          .should()
          .resideInAPackage("..projection..");
}
