plugins {
    java
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("io.freefair.lombok") version "8.14.4" apply false
}

subprojects {
    apply(plugin = "java")

    group = "com.aitestforge"
    version = "0.0.1-SNAPSHOT"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
