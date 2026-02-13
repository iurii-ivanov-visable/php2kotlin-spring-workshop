# Gradle

Gradle — build tool and dependencies orchestrator.

### build.gradle.kts

Kotlin DSL

```kotlin
plugins {
    kotlin("jvm") version "2.2.21"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

NOTE: you can use `gradle init` CLI command in an empty project and then add all the dependencies manually.

### implementation vs testImplementation

**implementation** is used in production code

- available at compile time and runtime

**testImplementation** only used in tests

- not added to the build of production code

Others:
**runtimeOnly**

- for example, JDBC Drivers

### Where to find dependencies and how to search

https://mvnrepository.com/

### Spring Boot Starters

Starter - dependency bundle.

- Gradle resolves everything for you, transitive dependencies and version conflicts.

For example:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-web")
```

Has everything you need to build REST APIs:

- Spring MVC
- Embedded Tomcat
- Jackson (JSON)
- ...

Or:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
```

Includes:

- Spring Data JDBC (https://docs.spring.io/spring-data/relational/reference/3.5/jdbc.html)
- Transaction support
- ...

### How to run tests and build with Gradle

```bash
./gradlew clean test
```

Has multiple tasks:

- clean - deletes previous build output
- it compiles code and run tests
- generates a report (build/reports/tests/test/index.html)

Other core tasks:

- build
- bootRun: runs Spring Boot app
- dependencies: ```./gradlew dependencies```

### Where are dependencies saved

Gradle saves them globally (unlike Node projects, for example).
Location: ~/.gradle/caches/
It contains downloaded JARs and metadata.

If you want to delete all the dependencies, execute: ```rm -rf ~/.gradle/caches/```
Then re-run to re-upload: ```./gradlew build```

### Override versions

#### Explicit versions

```kotlin
dependencies {
    implementation("org.postgresql:postgresql:42.7.3")
}
```

### Exclude dependencies

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
}
```

For example when replacing Tomcat with Netty.

## Sources

https://www.baeldung.com/spring-maven-bom
