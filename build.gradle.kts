// Plugins are declared here (apply false) so every module shares one version, defined in libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
