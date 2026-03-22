import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
    alias(libs.plugins.kover)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = """
            <p><strong>HttpMate</strong> is a powerful IntelliJ IDEA plugin designed to help developers quickly search and navigate to REST APIs within their projects. It supports Spring Boot and JAX-RS frameworks, offering a seamless experience similar to "Search Everywhere".</p>
            <br/>
            <h2>Features</h2>
            <ul>
                <li><strong>REST API Search</strong>: <code>Ctrl + \</code> (or <code>Ctrl + Alt + H</code>) to search Spring Boot &amp; JAX-RS APIs.</li>
                <li><strong>JSON Generation</strong>: Right-click on a class to generate JSON data.</li>
                <li><strong>API Documentation</strong>: Right-click on a method to generate Markdown docs.</li>
                <li><strong>Smart Navigation</strong>: Press <code>Enter</code> to jump to the declaration.</li>
            </ul>
            <br/>
            <p><strong>Source Code</strong>: <a href="https://github.com/EachFly/HttpMate">https://github.com/EachFly/HttpMate</a></p>
        """.trimIndent()

        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    org.jetbrains.changelog.Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "-Djava.util.concurrent.ForkJoinPool.common.threadFactory=java.util.concurrent.Executors\$DefaultThreadFactory"
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}
