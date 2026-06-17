plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.freefair.lombok")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    // OTP (TOTP)
    implementation("dev.samstevens.totp:totp:1.7.1")

    // Monitoring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED"
    )
}

// plain jar 생성 비활성화 (bootJar만 생성)
tasks.named<Jar>("jar") {
    enabled = false
}

// --- FE 빌드 결과물을 static/으로 복사 ---

// FE 빌드 태스크
tasks.register<Exec>("buildFrontend") {
    workingDir = file("../..")
    if (System.getProperty("os.name").lowercase().contains("win")) {
        commandLine("cmd", "/c", "pnpm build:web")
    } else {
        commandLine("sh", "-c", "pnpm build:web")
    }
}

// Web 빌드 결과물을 static/으로 복사
tasks.register<Copy>("copyWeb") {
    dependsOn("buildFrontend")
    from("../web/dist")
    into("src/main/resources/static")
    onlyIf { file("../web/dist").exists() }
    doFirst {
        // FE 빌드 결과물만 삭제 (assets/, index.html 등)
        // static/ 루트의 수동 관리 파일(robots.txt 등)은 보존
        val staticDir = file("src/main/resources/static")
        if (staticDir.exists()) {
            staticDir.listFiles()?.filter { it.name != "robots.txt" }?.forEach {
                it.deleteRecursively()
            }
        }
    }
}

tasks.named("processResources") {
    dependsOn("copyWeb")
}
