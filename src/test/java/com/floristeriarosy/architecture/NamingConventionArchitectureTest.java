package com.floristeriarosy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de nomenclatura del proyecto.
 *
 * Estas reglas garantizan que todos los desarrolladores utilicen la misma
 * convención de nombres independientemente del módulo que implementen.
 *
 * Gracias a ello todos los módulos (Category, Product, Order, Customer...)
 * tendrán exactamente la misma estructura.
 */
@AnalyzeClasses(packages = "com.floristeriarosy", importOptions = ImportOption.DoNotIncludeTests.class)
class NamingConventionArchitectureTest {

    /**
     * Todos los Controllers deben terminar en "Controller".
     */
    @ArchTest
    static final ArchRule controllers_should_have_controller_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.web.controller..")
            .should()
            .haveSimpleNameEndingWith("Controller");

    /**
     * Todos los Requests deben terminar en "Request".
     */
    @ArchTest
    static final ArchRule requests_should_have_request_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.web.request..")
            .should()
            .haveSimpleNameEndingWith("Request");

    /**
     * Todos los Responses deben terminar en "Response".
     */
    @ArchTest
    static final ArchRule responses_should_have_response_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.web.response..")
            .should()
            .haveSimpleNameEndingWith("Response");

    /**
     * Todos los Commands deben terminar en "Command".
     */
    @ArchTest
    static final ArchRule commands_should_have_command_suffix = classes()
            .that()
            .resideInAPackage("..application..command..")
            .should()
            .haveSimpleNameEndingWith("Command");

    /**
     * Todas las Queries deben terminar en "Query".
     */
    @ArchTest
    static final ArchRule queries_should_have_query_suffix = classes()
            .that()
            .resideInAPackage("..application..query..")
            .should()
            .haveSimpleNameEndingWith("Query");

    /**
     * Todos los Services deben terminar en "Service".
     */
    @ArchTest
    static final ArchRule services_should_have_service_suffix = classes()
            .that()
            .resideInAPackage("..application..service..")
            .should()
            .haveSimpleNameEndingWith("Service");

    /**
     * Todos los Input Ports deben terminar en "UseCase".
     */
    @ArchTest
    static final ArchRule use_cases_should_have_use_case_suffix = classes()
            .that()
            .resideInAPackage("..application..port.in..")
            .should()
            .haveSimpleNameEndingWith("UseCase");

    /**
     * Todos los Output Ports deben terminar en "Port".
     */
    @ArchTest
    static final ArchRule output_ports_should_have_port_suffix = classes()
            .that()
            .resideInAPackage("..application..port.out..")
            .should()
            .haveSimpleNameEndingWith("Port");

    /**
     * Todos los Persistence Adapters deben terminar en "Adapter".
     */
    @ArchTest
    static final ArchRule adapters_should_have_adapter_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.persistence.adapter..")
            .should()
            .haveSimpleNameEndingWith("Adapter");

    /**
     * Todas las entidades JPA deben terminar en "Entity".
     */
    @ArchTest
    static final ArchRule entities_should_have_entity_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.persistence.entity..")
            .should()
            .haveSimpleNameEndingWith("Entity");

    /**
     * Todos los JPA Repositories deben terminar en "JpaRepository".
     */
    @ArchTest
    static final ArchRule jpa_repositories_should_have_jpa_repository_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.persistence.jpa..repository..")
            .should()
            .haveSimpleNameEndingWith("JpaRepository");

    /**
     * Todos los JDBC Repositories deben terminar en "JdbcRepository".
     */
    @ArchTest
    static final ArchRule jdbc_repositories_should_have_jdbc_repository_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.persistence.jdbc..repository..")
            .should()
            .haveSimpleNameEndingWith("JdbcRepository");

    /**
     * Todos los Persistence Mapper deben terminar en "Mapper".
     */
    @ArchTest
    static final ArchRule persistence_mappers_should_have_mapper_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.persistence.mapper..")
            .should()
            .haveSimpleNameEndingWith("Mapper");

    /**
     * Todos los Web Mapper deben terminar en "Mapper".
     */
    @ArchTest
    static final ArchRule web_mappers_should_have_mapper_suffix = classes()
            .that()
            .resideInAPackage("..infrastructure.web.mapper..")
            .should()
            .haveSimpleNameEndingWith("Mapper");

    /**
     * Todos los RowMapper deben terminar en "RowMapper".
     */
    @ArchTest
    static final ArchRule row_mappers_should_have_row_mapper_suffix = classes()
            .that()
            .resideInAPackage("..rowmapper..")
            .should()
            .haveSimpleNameEndingWith("RowMapper");

    /**
     * Todas las Projection deben terminar en "Projection".
     */
    @ArchTest
    static final ArchRule projections_should_have_projection_suffix = classes()
            .that()
            .resideInAPackage("..projection..")
            .should()
            .haveSimpleNameEndingWith("Projection");
}
