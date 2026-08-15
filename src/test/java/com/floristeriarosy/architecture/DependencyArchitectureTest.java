package com.floristeriarosy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas globales de dependencia entre capas.
 *
 * Esta clase define la dirección permitida de las dependencias entre las
 * distintas capas de la aplicación.
 *
 * El objetivo es garantizar que la Arquitectura Hexagonal se mantenga
 * intacta durante toda la vida del proyecto.
 *
 * Diagrama de dependencias:
 *
 * Infrastructure (Web, Persistence, Security...)
 * │
 * ▼
 * Application
 * │
 * ▼
 * Domain
 *
 * El dominio representa el núcleo del negocio y nunca conoce las capas
 * exteriores.
 */
@AnalyzeClasses(packages = "com.floristeriarosy", importOptions = ImportOption.DoNotIncludeTests.class)
class DependencyArchitectureTest {

    /**
     * El dominio nunca puede acceder a Application.
     *
     * Motivo:
     * El dominio únicamente contiene reglas de negocio.
     * No conoce casos de uso, comandos ni puertos.
     */
    @ArchTest
    static final ArchRule domain_should_not_access_application = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..");

    /**
     * El dominio nunca puede acceder a Infrastructure.
     *
     * Motivo:
     * El dominio no sabe cómo se persisten los datos,
     * cómo se envían correos o cómo se almacenan imágenes.
     */
    @ArchTest
    static final ArchRule domain_should_not_access_infrastructure = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    /**
     * Application nunca puede acceder a adaptadores Web.
     *
     * Motivo:
     * Los casos de uso deben ser independientes del mecanismo
     * mediante el que son invocados (REST, GraphQL, CLI...).
     */
    @ArchTest
    static final ArchRule application_should_not_access_web = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure.web..");

    /**
     * Infrastructure nunca puede acceder a la implementación de otros
     * casos de uso.
     *
     * Motivo:
     * Infrastructure implementa Ports Out y expone adaptadores.
     * No debe ejecutar directamente lógica de aplicación.
     *
     * Debe comunicarse mediante las interfaces públicas (UseCases o Ports).
     */
    @ArchTest
    static final ArchRule infrastructure_should_not_access_application_services = noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..service..");
}
