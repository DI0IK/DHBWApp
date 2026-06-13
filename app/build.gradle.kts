plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.fabrikt)
    alias(libs.plugins.compose.compiler)
}

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.json:json:20231013") }
}

import java.net.URI
import org.json.JSONObject
import com.android.build.api.variant.AndroidComponentsExtension

val dhbwSpecFile = layout.buildDirectory.file("tmp/dhbw-openapi.json").get().asFile
val enrichedSpecFile = layout.buildDirectory.file("tmp/dhbw-enriched-openapi.json").get().asFile
val schemasFile = file("$projectDir/openapi.schemas.json")

val downloadDhbwSpec by tasks.registering {
    group = "openapi"
    description = "Downloads the latest DHBW OpenAPI specification."
    outputs.file(dhbwSpecFile)

    doLast {
        val specUrl = "https://api.dhbw.dev/openapi.json"
        logger.lifecycle("Fetching DHBW OpenAPI spec from: $specUrl")

        try {
            dhbwSpecFile.parentFile.mkdirs()
            URI(specUrl).toURL().openStream().use { input ->
                dhbwSpecFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            logger.lifecycle("Downloaded successfully.")
        } catch (e: Exception) {
            throw GradleException("Failed to download DHBW API schema.", e)
        }
    }
}

val enrichDhbwSpec by tasks.registering {
    group = "openapi"
    description = "Merges local response schemas into the downloaded OpenAPI spec."
    dependsOn(downloadDhbwSpec)
    inputs.file(dhbwSpecFile)
    inputs.file(schemasFile)
    outputs.file(enrichedSpecFile)

    doLast {
        val remoteSpec = JSONObject(dhbwSpecFile.readText())
        val patch = JSONObject(schemasFile.readText())

        fun JSONObject.deepMerge(source: JSONObject) {
            source.keys().forEach { key ->
                when (val sourceVal = source[key]) {
                    is JSONObject -> {
                        if (has(key) && this[key] is JSONObject) {
                            this.getJSONObject(key).deepMerge(sourceVal)
                        } else {
                            put(key, sourceVal)
                        }
                    }
                    else -> put(key, sourceVal)
                }
            }
        }

        // Merge schemas
        patch.optJSONObject("components")?.optJSONObject("schemas")?.let { schemas ->
            val components = remoteSpec.optJSONObject("components")
                ?: JSONObject().also { remoteSpec.put("components", it) }
            val remoteSchemas = components.optJSONObject("schemas")
                ?: JSONObject().also { components.put("schemas", it) }
            schemas.keys().forEach { key ->
                remoteSchemas.put(key, schemas.get(key))
            }
        }

        // Merge path responses
        patch.optJSONObject("paths")?.let { pathPatches ->
            val remotePaths = remoteSpec.optJSONObject("paths")
                ?: JSONObject().also { remoteSpec.put("paths", it) }
            pathPatches.keys().forEach { path ->
                val pathPatch = pathPatches.getJSONObject(path)
                val remotePath = remotePaths.optJSONObject(path)
                    ?: JSONObject().also { remotePaths.put(path, it) }
                pathPatch.keys().forEach { method ->
                    val methodPatch = pathPatch.getJSONObject(method)
                    val remoteMethod = remotePath.optJSONObject(method)
                        ?: JSONObject().also { remotePath.put(method, it) }
                    methodPatch.optJSONObject("responses")?.let { responsePatch ->
                        val remoteResponses = remoteMethod.optJSONObject("responses")
                            ?: JSONObject().also { remoteMethod.put("responses", it) }
                        responsePatch.keys().forEach { statusCode ->
                            val patchResponse = responsePatch.getJSONObject(statusCode)
                            val remoteResponse = remoteResponses.optJSONObject(statusCode)
                                ?: JSONObject().also { remoteResponses.put(statusCode, it) }
                            remoteResponse.deepMerge(patchResponse)
                        }
                    }
                }
            }
        }

        enrichedSpecFile.parentFile.mkdirs()
        enrichedSpecFile.writeText(remoteSpec.toString(2))
        logger.lifecycle("Enriched spec written to ${enrichedSpecFile.path}")
    }
}

fabrikt {
    generate("dhwApi") {
        apiFile = enrichedSpecFile
        basePackage = "dev.dominikstahl.dhbwapp.remote"
        validationLibrary = NoValidation

        client {
            generate = enabled
            target = Ktor
        }
        model {
            generate = enabled
            serializationLibrary = Kotlinx
        }
    }
}

tasks.configureEach {
    if (name == "fabriktGenerateDhwApi") {
        dependsOn(enrichDhbwSpec)
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addStaticSourceDirectory("build/generated/sources/fabrikt/src/main/kotlin")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("fabriktGenerateDhwApi")
}

android {
    namespace = "dev.dominikstahl.dhbwapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.dominikstahl.dhbwapp"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.jsoup)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}