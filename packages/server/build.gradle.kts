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
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
