import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Puhas Kotlini moodul: ELM327, ISO-TP, UDS ja PID-ide dekodeerimine.
// Androidist sõltumatu, et loogikat saaks testida ilma telefoni ja autota.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
