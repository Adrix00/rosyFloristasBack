package com.floristeriarosy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas arquitectónicas de la capa Infrastructure.
 *
 * Infrastructure contiene exclusivamente adaptadores tecnológicos.
 *
 * Su responsabilidad es adaptar el mundo exterior al modelo de la aplicación,
 * implementando los Output Ports y exponiendo los mecanismos de entrada
 * (REST, Scheduler, Security, etc.).
 *
 * Nunca debe contener reglas de negocio.
 */
@AnalyzeClasses(packages = "com.floristeriarosy", importOptions = ImportOption.DoNotIncludeTests.class)
class InfrastructureArchitectureTest {

        /**
         * Infrastructure nunca debe depender directamente de Commands.
         *
         * Motivo:
         * Los Commands pertenecen exclusivamente a la capa Application y
         * representan peticiones internas de un caso de uso.
         *
         * Infrastructure debe invocar únicamente los Input Ports.
         */
        @ArchTest
        static final ArchRule infrastructure_should_not_depend_on_commands = noClasses()
                        .that()
                        .resideInAPackage("..infrastructure..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..application..command..");

        /**
         * Infrastructure nunca debe depender directamente de Queries.
         *
         * Motivo:
         * Las Queries forman parte del contrato interno de Application.
         *
         * Infrastructure únicamente ejecuta los casos de uso mediante
         * los Input Ports.
         */
        @ArchTest
        static final ArchRule infrastructure_should_not_depend_on_queries = noClasses()
                        .that()
                        .resideInAPackage("..infrastructure..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..application..query..");

        /**
         * Infrastructure nunca debe depender directamente de los Services.
         *
         * Motivo:
         * Los Services son la implementación interna de los casos de uso.
         *
         * Infrastructure siempre debe depender de las interfaces públicas
         * (Input Ports), dejando que Spring resuelva la implementación.
         */
        @ArchTest
        static final ArchRule infrastructure_should_not_depend_on_application_services = noClasses()
                        .that()
                        .resideInAPackage("..infrastructure..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..application..service..");
}
