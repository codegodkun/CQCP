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
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.apache.httpcomponents.client5:httpclient5")
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
    // A full test run creates several cached Spring contexts against one
    // PostgreSQL database. Keep every scheduled worker disabled so one context
    // cannot claim another test class's QUEUED fixture.
    systemProperty("cqcp.review.worker.enabled", "false")
}

tasks.register<JavaExec>("generateBlindEvaluation") {
    group = "verification"
    description = "Generate TASK-EVAL-002 deterministic blind evaluation packages"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.cqcp.apiserver.evaluation.BlindEvaluationProjectionGenerator")
    val repoRoot = projectDir.parentFile.parentFile
    args(
        repoRoot.absolutePath,
        repoRoot.resolve("outputs/task-eval-002").absolutePath
    )
}
