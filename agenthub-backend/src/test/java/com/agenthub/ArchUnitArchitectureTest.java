package com.agenthub;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchUnitArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.agenthub");
    }

    @Test
    @DisplayName("架构守护 1：所有 Controller 控制器类严禁直接注入或依赖 Repository 数据仓储层")
    void controllersShouldNotDependOnRepositories() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().resideInAnyPackage("..repository..")
                .because("Controllers must only call Application interfaces and must never access Repositories directly")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 2：所有 RestController 控制器必须位于 api 或 adapter.web 接入层包内")
    void controllersShouldResideInApiOrWebPackages() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAnyPackage("..api..", "..adapter.web..")
                .because("Web Controllers must reside within api or adapter.web package layers")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 3：领域应用服务实现类必须位于 application 包内")
    void applicationServicesShouldResideInApplicationPackages() {
        classes().that().haveSimpleNameEndingWith("ApplicationService")
                .should().resideInAPackage("..application..")
                .because("Application Services orchestrating use cases must reside within application packages")
                .check(importedClasses);
    }
}
