import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/* The budget engine: plain Kotlin with no Android code, so it can be tested on any computer
   against the web version's results (see parity/). */
plugins { alias(libs.plugins.kotlin.jvm) }

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

tasks.test {
    systemProperty("fixtures", rootProject.file("parity/fixtures.json").absolutePath)
    testLogging { events("passed", "failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
