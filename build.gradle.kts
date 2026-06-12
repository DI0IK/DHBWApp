buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.json:json:20231013")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}