import java.security.MessageDigest

plugins {
    id("com.android.application") version "8.10.1"
}

layout.buildDirectory.set(file("../../build/model-benchmark"))

android {
    namespace = "dev.zeroinput.modelbenchmark"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.zeroinput.modelbenchmark"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "research"
        ndk { abiFilters += "x86_64" }
    }

    sourceSets["main"].assets.srcDir(file("../../build/model-evaluation/android-assets"))
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/notices"))
    androidResources { noCompress += "onnx" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.26.0")
}

val verifyRuntimeArtifact = tasks.register("verifyRuntimeArtifact") {
    doLast {
        val artifact = configurations["debugRuntimeClasspath"].resolvedConfiguration.resolvedArtifacts.single {
            it.moduleVersion.id.group == "com.microsoft.onnxruntime" && it.name == "onnxruntime-android"
        }.file
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(artifact.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == "09c0780ae8d734ef2774bdf498b624729a855e6f9a8e488a0e7398a4e7396032") {
            "Pinned research runtime checksum mismatch"
        }
    }
}

val prepareNotices = tasks.register<Sync>("prepareNotices") {
    from(file("../../LICENSES/onnxruntime-MIT.txt"))
    from(file("../../LICENSES/onnxruntime-ThirdPartyNotices.txt"))
    from(file("../../NOTICE"))
    into(layout.buildDirectory.dir("generated/notices/licenses"))
}

tasks.named("preBuild").configure { dependsOn(verifyRuntimeArtifact, prepareNotices) }

tasks.register("verifyBenchmarkPrivacy") {
    dependsOn("processDebugMainManifest", "processReleaseMainManifest")
    doLast {
        listOf("debug", "release").forEach { variant ->
            val root = layout.buildDirectory.dir("intermediates/merged_manifest/$variant").get().asFile
            val manifests = root.walkTopDown().filter { it.name == "AndroidManifest.xml" }.toList()
            check(manifests.size == 1) { "Missing benchmark manifest" }
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val document = factory.newDocumentBuilder().parse(manifests.single())
            listOf("uses-permission", "uses-permission-sdk-23", "activity", "activity-alias", "service", "receiver", "provider")
                .forEach { check(document.getElementsByTagName(it).length == 0) { "Unexpected benchmark capability" } }
            val app = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
            val ns = "http://schemas.android.com/apk/res/android"
            check(app.getAttributeNS(ns, "allowBackup") == "false")
            if (variant == "release") check(app.getAttributeNS(ns, "debuggable") != "true")
        }
    }
}
