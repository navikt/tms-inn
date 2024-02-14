import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm").version(Kotlin.version)
    kotlin("plugin.allopen").version(Kotlin.version)

    id(Flyway.pluginId) version (Flyway.version)
    id(Shadow.pluginId) version (Shadow.version)

    // Apply the application plugin to add support for building a CLI application.
    application
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}


repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    mavenLocal()
}

dependencies {
    implementation(Flyway.core)
    implementation(Hikari.cp)
    implementation(Kafka.clients)
    implementation(Logstash.logbackEncoder)
    implementation(JacksonDatatype.datatypeJsr310)
    implementation(JacksonDatatype.moduleKotlin)
    implementation(KotliQuery.kotliquery)
    implementation(KotlinLogging.logging)
    implementation(Ktor.Server.core)
    implementation(Ktor.Server.netty)
    implementation(Ktor.Server.htmlDsl)
    implementation(Ktor.Server.statusPages)
    implementation(Ktor.Server.authJwt)
    implementation(Ktor.Server.auth)
    implementation(Ktor.Client.contentNegotiation)
    implementation(Ktor.Client.apache)
    implementation(Ktor.Serialization.jackson)
    implementation(Postgresql.postgresql)
    implementation(Prometheus.common)
    implementation(Prometheus.hotspot)
    implementation(TmsCommonLib.utils)
    implementation(TmsCommonLib.observability)
    implementation(TmsCommonLib.kubernetes)
    implementation(TmsKtorTokenSupport.azureValidation)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation(TmsVarselBuilder.kotlinBuilder)
    implementation(TmsKtorTokenSupport.azureValidation)
    implementation(RapidsAndRivers.rapidsAndRivers)


    testImplementation(Junit.api)
    testImplementation(Junit.engine)
    testImplementation(Junit.params)
    testImplementation(Mockk.mockk)
    testImplementation(TestContainers.postgresql)
    testImplementation(Kotest.runnerJunit5)
    testImplementation(Kotest.assertionsCore)
    testImplementation(TmsKtorTokenSupport.azureValidationMock)
}

application {
    mainClass.set("no.nav.tms.brannslukning.ApplicationKt")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events("passed", "skipped", "failed")
        }
    }
}
apply(plugin = Shadow.pluginId)
