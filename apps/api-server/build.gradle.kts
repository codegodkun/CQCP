plugins {
    java
    id("org.springframework.boot") version "3.3.12"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.cqcp"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4")
    implementation("org.flywaydb:flyway-core")
    implementation("org.postgresql:postgresql:42.7.5")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.processResources {
    from(file("../../packages/review-assets")) {
        into("cqcp/review-assets")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
