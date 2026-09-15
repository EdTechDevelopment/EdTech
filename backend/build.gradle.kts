import org.jooq.meta.jaxb.Logging

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jooq.jooq-codegen-gradle") version "3.21.7"
}

group = "io.github.edtechdevelopment"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    runtimeOnly("org.postgresql:postgresql")

    jooqCodegen("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        java.srcDir("src/generated/java")
    }
}

jooq {
    configuration {
        logging = Logging.WARN

        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://localhost:5432/edtech"
            user = "edtech"
            password = "edtech"
        }

        generator {
            name = "org.jooq.codegen.JavaGenerator"

            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                includes = "identity_.*"
                excludes = "flyway_schema_history"
            }

            generate {
                isDeprecated = false
                isRelations = true
                isRecords = true
                isPojos = false
                isDaos = false
                isJavaTimeTypes = true
            }

            target {
                packageName = "io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated"
                directory = "src/generated/java"
                encoding = "UTF-8"
                locale = "en"
                clean = true
            }
        }
    }
}

dependencyManagement {
    imports {
        mavenBom(
            "org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
