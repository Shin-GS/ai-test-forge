plugins {
    java
    id("io.spring.dependency-management")
    id("io.freefair.lombok")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-web")
    implementation("org.slf4j:slf4j-api")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.getByName<Jar>("jar") {
    enabled = true
}
