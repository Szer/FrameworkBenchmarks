plugins {
    application
    kotlin("jvm") version "1.5.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    java
    kotlin("kapt") version "1.5.10"
//    kotlin("plugin.serialization") version "1.5.10"
}

val entryPointName = "MainKt"
group = "com.techempower"

object V {
    const val logback = "1.2.3"
    const val ktor = "1.6.1"
    const val kotlin = "1.5.10"
    const val kotlinx = "1.5.1"
    const val postgres = "42.2.23"
    const val vertx = "4.1.1"
    const val dsljson = "1.9.8"
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    tasks {
        compileKotlin {
            kotlinOptions {
                apiVersion = "1.5"
                languageVersion = "1.5"
                jvmTarget = "16"
                sourceCompatibility = "16"
                targetCompatibility = "16"
            }
        }

        shadowJar {
            destinationDirectory.set(file(rootDir))
            archiveFileName.set("app.jar")
            isZip64 = true
            manifest {
                attributes(
                    "Main-Class" to entryPointName
                )
            }
        }
    }

    sourceSets.all {
        languageSettings.languageVersion = "1.5"
        languageSettings.apiVersion = "1.5"
        languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${V.kotlinx}")
    implementation("io.ktor:ktor-server-netty:${V.ktor}")
    implementation("io.ktor:ktor-server-core:${V.ktor}")
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    implementation("com.dslplatform:dsl-json-java8:${V.dsljson}")
    kapt("com.dslplatform:dsl-json-java8:${V.dsljson}")
//    implementation("ch.qos.logback:logback-classic:${V.logback}")

    implementation("io.ktor:ktor-pebble:${V.ktor}")

    implementation("org.postgresql:postgresql:${V.postgres}")
    implementation("io.vertx:vertx-pg-client:${V.vertx}")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:${V.vertx}")
}

application {
    // ShadowJar still looks at the old name convention
    @Suppress("DEPRECATION")
    mainClassName = entryPointName
    mainClass.set(entryPointName)
}
