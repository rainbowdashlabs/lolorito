plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    java
}

group = "de.chojo"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://eldonexus.de/repository/maven-public")
    maven("https://eldonexus.de/repository/maven-proxies")
}

dependencies {
    //discord
    implementation("de.chojo", "cjda-util", "2.8.6+beta.11") {
        exclude(group = "club.minnced", module = "opus-java")
    }

    implementation("de.chojo.universalis", "universalis", "1.4.2")

    // database
    implementation("org.postgresql", "postgresql", "42.7.5")
    implementation("de.chojo.sadu", "sadu-queries", "1.4.1")
    implementation("de.chojo.sadu", "sadu-updater", "1.4.1")
    implementation("de.chojo.sadu", "sadu-postgresql", "1.4.1")
    implementation("de.chojo.sadu", "sadu-datasource", "1.4.1")

    // Logging
    implementation("org.slf4j", "slf4j-api", "2.0.11")
    implementation("org.apache.logging.log4j", "log4j-core", "2.23.1")
    implementation("org.apache.logging.log4j", "log4j-slf4j2-impl", "2.23.1")
    implementation("de.chojo", "log-util", "1.0.1"){
        exclude("org.apache.logging.log4j")
    }

    // unit testing
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter", "junit-jupiter")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks {
    processResources {
        from(sourceSets.main.get().resources.srcDirs) {
            filesMatching("version") {
                expand(
                    "version" to project.version
                )
            }
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    javadoc {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    shadowJar {
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "de.chojo.lolorito.Lolorito"))
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
