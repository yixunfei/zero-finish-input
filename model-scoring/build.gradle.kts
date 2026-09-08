import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val modelAssets = layout.buildDirectory.dir("generated/modelAssets")
val modelSource = rootProject.file("build/model-evaluation/android-assets/mini-int8/model.onnx")
val vocabSource = rootProject.file("build/model-evaluation/mini/vocab.txt")
val prepareModelAssets = tasks.register("prepareModelAssets") {
    inputs.files(modelSource, vocabSource)
    outputs.dir(modelAssets)
    doLast {
        val files = listOf(
            Triple(modelSource, 14_898_764L, "5fb4dbe2c618e8757258253e10481ea9181e8a7b9a8efea03ee70c3a5ca19446"),
            Triple(vocabSource, 109_540L, "45bbac6b341c319adc98a532532882e91a9cefc0329aa57bac9ae761c27b291c"),
        )
        files.forEach { (file, size, hash) ->
            check(file.isFile && file.length() == size) { "Prepare pinned Mini INT8 assets: see docs/model-integration.md" }
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == hash) { "Mini asset checksum mismatch" }
        }
        val target = modelAssets.get().dir("mini-int8").asFile.apply { mkdirs() }
        files.forEach { (source, _, _) -> source.copyTo(target.resolve(source.name), overwrite = true) }
    }
}

val verifyRuntimeArtifact = tasks.register("verifyRuntimeArtifact") {
    doLast {
        val artifact = configurations["debugRuntimeClasspath"].resolvedConfiguration.resolvedArtifacts.single {
            it.moduleVersion.id.group == "com.microsoft.onnxruntime" && it.name == "onnxruntime-android"
        }.file
        val hash = MessageDigest.getInstance("SHA-256").digest(artifact.readBytes()).joinToString("") { "%02x".format(it) }
        check(hash == "09c0780ae8d734ef2774bdf498b624729a855e6f9a8e488a0e7398a4e7396032") { "Runtime checksum mismatch" }
    }
}

android {
    namespace = "dev.zeroinput.model"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    sourceSets["main"].assets.srcDir(modelAssets)
    androidResources { noCompress += "onnx" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { warningsAsErrors = true; abortOnError = true; disable += "GradleDependency" }
}

tasks.named("preBuild").configure { dependsOn(prepareModelAssets, verifyRuntimeArtifact) }
dependencies {
    implementation(project(":engine-api"))
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
}
