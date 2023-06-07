plugins {
    `kotlin-dsl`
    idea
}
group = "at.asitplus.gradle"

idea {
    project {
        jdkName="11" //TODO share
    }
}

dependencies {
    api("at.asitplus.gradle:conventions")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11/*TODO share*/))
    }
}

gradlePlugin {
    // Add fake plugin, if you don't have any
    plugins.register("vclib-conventions") {
        id = "at.asitplus.gradle.vclib-conventions"
        implementationClass = "at.asitplus.gradle.VcLibConventions"
    }
    // Or provide your implemented plugins
}