plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "ktlint_code_style" to "ktlint_official",
                // Compose functions are PascalCase.
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                // M0 placeholder files are comment-only. Re-enable once they are all filled in.
                "ktlint_standard_no-empty-file" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}
