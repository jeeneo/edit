plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)
}

kotlin {
    jvmToolchain(17)
}
