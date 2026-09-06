plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("privacyCheck") {
    group = "verification"
    description = "Rejects unexpected packaged permissions and Android system clipboard capabilities."
    dependsOn(":app:processDebugMainManifest", ":app:processReleaseMainManifest")

    doLast {
        val forbidden = listOf(
            Regex("android\\.permission\\.INTERNET") to "Runtime networking is forbidden",
            Regex("android\\.content\\.ClipboardManager") to "Use the encrypted in-app vault instead",
            Regex("\\bCLIPBOARD_SERVICE\\b") to "Use the encrypted in-app vault instead",
            Regex("\\bgetPrimaryClip\\s*\\(") to "Reading the Android clipboard is forbidden",
            Regex("\\bsetPrimaryClip\\s*\\(") to "Writing the Android clipboard is forbidden",
        )
        val sourceRoots = subprojects.map { it.file("src") }
        val violations = mutableListOf<String>()

        sourceRoots.filter(File::exists).forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java", "xml") }
                .forEach { file ->
                    val text = file.readText()
                    forbidden.forEach { (pattern, reason) ->
                        if (pattern.containsMatchIn(text)) {
                            violations += "${file.relativeTo(rootProject.projectDir)}: $reason"
                        }
                    }
                }
        }

        val androidNamespace = "http://schemas.android.com/apk/res/android"
        listOf("debug", "release").forEach { variant ->
            val manifestRoot = rootProject.file("app/build/intermediates/merged_manifest/$variant")
            val manifests = manifestRoot.walkTopDown()
                .filter { it.isFile && it.name == "AndroidManifest.xml" }
                .toList()
            if (manifests.isEmpty()) {
                violations += "Missing merged $variant manifest; packaged permissions were not verified"
            }
            manifests.forEach { manifest ->
                val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isExpandEntityReferences = false
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }
                val document = manifest.inputStream().use { factory.newDocumentBuilder().parse(it) }
                val packageName = document.documentElement.getAttribute("package")
                val allowedPermissions = setOf(
                    "android.permission.USE_BIOMETRIC",
                    "android.permission.USE_FINGERPRINT",
                    "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                )
                val permissionNames = buildSet {
                    val elements = document.getElementsByTagName("*")
                    for (index in 0 until elements.length) {
                        val element = elements.item(index) as? org.w3c.dom.Element ?: continue
                        if (!element.tagName.startsWith("uses-permission")) continue
                        element.getAttributeNS(androidNamespace, "name")
                            .takeIf(String::isNotBlank)
                            ?.let(::add)
                    }
                }
                val unexpected = permissionNames - allowedPermissions
                if (unexpected.isNotEmpty()) {
                    violations += "${manifest.relativeTo(rootProject.projectDir)}: " +
                        "Unexpected packaged permissions: ${unexpected.sorted().joinToString()}"
                }
            }
        }
        check(violations.isEmpty()) { violations.joinToString("\n") }
    }
}
