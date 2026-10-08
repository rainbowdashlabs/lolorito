plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
    java
    application
    jacoco
}

group = "de.chojo"
version = "1.0.0"

application {
    mainClass = "de.chojo.lolorito.Lolorito"
    // log4j2.xml is on the classpath under src/main/resources; log4j auto-discovers it.
    // Ocular reads config/config.yaml relative to the process CWD.
}

repositories {
    maven("https://eldonexus.de/repository/maven-central")
    maven("https://eldonexus.de/repository/maven-public")
    maven("https://eldonexus.de/repository/maven-proxies")
}

dependencies {
    // Discord — exclude opus-java, we don't use voice.
    implementation(libs.cjda.util) {
        exclude(group = "club.minnced", module = "opus-java")
    }

    implementation(libs.universalis)

    // Database
    implementation(libs.postgresql)
    implementation(libs.bundles.sadu)

    // Configuration (Ocular + Jackson 3)
    implementation(libs.bundles.config)
    annotationProcessor(libs.ocular)

    // Web
    implementation(libs.javalin)

    // Dependency injection — one Guice module wires the whole app.
    implementation(libs.guice)

    // In-process response cache in front of the value + planner engines.
    implementation(libs.caffeine)

    // Lodestone HTML scraping.
    implementation(libs.jsoup)

    // Logging — log-util pins an old log4j, exclude and let ours win.
    implementation(libs.bundles.logging)
    implementation(libs.log.util) {
        exclude(group = "org.apache.logging.log4j")
    }

    // Unit testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform)

    // Integration testing (one Postgres per test class, ported from Ember)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.postgresql)
    testImplementation(libs.javalin.testtools)
    testImplementation(libs.assertj)
    testImplementation("org.mockito:mockito-core:5.14.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

// -- Spotless -----------------------------------------------------------------
// License headers + palantir-format on Java; header + trim/newline on JS/TS/Vue.
// Ported from Ember; see HEADER.txt for the AGPL SPDX blurb.

spotless {
    java {
        target("src/**/*.java")
        licenseHeaderFile(rootProject.file("HEADER.txt"))
        trimTrailingWhitespace()
        endWithNewline()
        palantirJavaFormat("2.84.0")
            .formatJavadoc(false)
        removeUnusedImports()
        importOrder("", "java", "javax", "\\#")
        encoding("UTF-8")
    }

    format("javascript") {
        licenseHeaderFile(
            rootProject.file("HEADER.txt"),
            "(import|const|let|var|export|function|type|interface|enum|class|abstract|async|declare|//|/\\*\\*)",
        )
        target("frontend/src/**/*.js", "frontend/src/**/*.ts")
        targetExclude("frontend/node_modules/**", "frontend/dist/**")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("vue") {
        licenseHeaderFile(rootProject.file("HEADER.txt"), "(<template|<script|<style)")
        target("frontend/src/**/*.vue")
        targetExclude("frontend/node_modules/**", "frontend/dist/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val frontendDir = layout.projectDirectory.dir("frontend")
val frontendDist = frontendDir.dir("dist")

val frontendInstall by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Install frontend npm dependencies"
    workingDir = frontendDir.asFile
    commandLine("npm", "ci")
    inputs.file(frontendDir.file("package.json"))
    inputs.file(frontendDir.file("package-lock.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val frontendBuild by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Build the Vue SPA into frontend/dist"
    dependsOn(frontendInstall)
    workingDir = frontendDir.asFile
    commandLine("npm", "run", "build")
    inputs.dir(frontendDir.dir("src"))
    inputs.file(frontendDir.file("index.html"))
    inputs.file(frontendDir.file("vite.config.ts"))
    inputs.file(frontendDir.file("tsconfig.json"))
    inputs.file(frontendDir.file("package.json"))
    outputs.dir(frontendDist)
}

/**
 * Best-effort refresh of the bundled catalog + recipes + desynth JSONs.
 * Now runs on the JVM via `de.chojo.lolorito.catalog.CatalogRefreshCli`
 * — no node/npm required for the refresh path. Users invoke this task
 * explicitly (or CI does before `assemble`) so the shipped jar carries
 * fresh mappings. On network failure the CLI leaves the existing files
 * in place — a stale seed beats a broken build.
 */
val refreshCatalog by tasks.registering(JavaExec::class) {
    group = "catalog"
    description = "Refresh bundled catalog (icons, items, stack sizes, recipes, desynth results) from XIVAPI + Teamcraft"
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("de.chojo.lolorito.catalog.CatalogRefreshCli")
    // Positional arg = output root; default is src/main/resources so the
    // JSONs land where processResources will pick them up.
    args("src/main/resources")
    isIgnoreExitValue = true
}

tasks {
    processResources {
        dependsOn(frontendBuild)
        from(sourceSets.main.get().resources.srcDirs) {
            filesMatching("version") {
                expand(
                    "version" to project.version
                )
            }
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        from(frontendDist) {
            into("web")
        }
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    javadoc {
        options.encoding = "UTF-8"
    }

    // -- Testing --------------------------------------------------------------
    // Split by layer so `verify` can run repositories / services / everything-
    // else in parallel. Each task filters by package name.

    test {
        // Split tasks below own their layers — leave `test` for whatever isn't filtered.
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        filter {
            excludeTestsMatching("*.repository.*")
            excludeTestsMatching("*.service.*")
            excludeTestsMatching("*.value.*")
            excludeTestsMatching("*.planner.*")
            isFailOnNoMatchingTests = false
        }
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }

    register<Test>("testRepositories") {
        group = "verification"
        description = "Runs tests under de.chojo.lolorito.repository (spawns Postgres per class)"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
        filter {
            includeTestsMatching("*.repository.*")
            isFailOnNoMatchingTests = false
        }
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }

    register<Test>("testServices") {
        group = "verification"
        description = "Runs tests under de.chojo.lolorito.service + value + planner"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
        filter {
            includeTestsMatching("*.service.*")
            includeTestsMatching("*.value.*")
            includeTestsMatching("*.planner.*")
            isFailOnNoMatchingTests = false
        }
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }

    register<Test>("testOther") {
        group = "verification"
        description = "Runs the rest of the test suite (non-repo, non-service, non-value, non-planner)"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
        filter {
            excludeTestsMatching("*.repository.*")
            excludeTestsMatching("*.service.*")
            excludeTestsMatching("*.value.*")
            excludeTestsMatching("*.planner.*")
            isFailOnNoMatchingTests = false
        }
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }

    // -- Coverage -------------------------------------------------------------
    // Ported from Ember: one merged jacoco report across every Test task, plus
    // per-layer minimum thresholds. Test tasks emit .exec files that the
    // aggregate report + verification consume.

    withType<Test> {
        extensions.configure<JacocoTaskExtension> {
            destinationFile = file("${layout.buildDirectory.get()}/jacoco/${name}.exec")
        }
    }

    register<JacocoReport>("jacocoFullReport") {
        group = "verification"
        description = "Merged coverage report from every test task"
        dependsOn("testRepositories", "testServices", "testOther")
        executionData(fileTree("${layout.buildDirectory.get()}/jacoco") { include("*.exec") })
        sourceSets(sourceSets.main.get())
        reports {
            xml.required.set(true)
            csv.required.set(true)
            html.required.set(true)
        }
    }

    register<JacocoCoverageVerification>("jacocoCoverageCheck") {
        group = "verification"
        description = "Per-layer line-coverage floors (repositories 95 %, services 80 %)"
        dependsOn("testRepositories", "testServices", "testOther")
        executionData(fileTree("${layout.buildDirectory.get()}/jacoco") { include("*.exec") })
        sourceSets(sourceSets.main.get())
        violationRules {
            // Repositories are integration-tested through the container; hold them tight.
            rule {
                element = "CLASS"
                includes = listOf("*.repository.*")
                excludes = listOf(
                    // Empty shell until the users table grows real columns.
                    "*.repository.Users",
                )
                limit {
                    counter = "LINE"
                    minimum = "0.95".toBigDecimal()
                }
            }
            // Services carry the business logic; unit-test coverage should be strong.
            rule {
                element = "CLASS"
                includes = listOf("*.service.*")
                excludes = listOf(
                    // Startup-only workers and scheduled fan-outs, exercised in prod not tests.
                    "*.CraftDesynthLoader",
                    "*.DataRefreshWorker",
                )
                limit {
                    counter = "LINE"
                    minimum = "0.80".toBigDecimal()
                }
            }
            // Discord slash-handlers — glue against JDA; hard to unit-test.
            // Excluded from the coverage floor; the layering rule ensures they stay thin.

            // Javalin routes — driven by JavalinTest under de.chojo.lolorito.web.
            rule {
                element = "CLASS"
                includes = listOf(
                    "*.web.api.*",
                    "*.web.Web",
                    "*.web.auth.AuthRoutes",
                    "*.web.auth.SessionResolver",
                )
                limit {
                    counter = "LINE"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    register("checkLicenseBackend") {
        group = "verification"
        description = "Checks license headers for backend Java files"
        dependsOn("spotlessJavaCheck")
    }

    register("checkLicenseFrontend") {
        group = "verification"
        description = "Checks license headers for frontend TS/JS/Vue files"
        dependsOn("spotlessJavascriptCheck", "spotlessVueCheck")
    }

    register("verify") {
        group = "verification"
        description = "Runs the full verification suite"
        dependsOn(
            "testRepositories",
            "testServices",
            "testOther",
            "jacocoCoverageCheck",
            "checkLicenseBackend",
            "checkLicenseFrontend"
        )
    }

    shadowJar {
        mergeServiceFiles()
    }
}
