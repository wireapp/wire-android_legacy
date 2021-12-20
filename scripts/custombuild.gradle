import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Credentials

buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath "org.ajoberstar.grgit:grgit-core:${Versions.GRGIT}"
    }
}

// Will check out custom repo, if any, and load its configuration, merging it on top of the default configuration
def prepareCustomizationEnvironment() {
    def properties = new Properties()
    def localProperties = project.rootProject.file("local.properties")

    if (localProperties.exists()) {
        properties.load(localProperties.newDataInputStream())
    }

    def jsonReader = new JsonSlurper()
    def wireConfigFile = new File("$rootDir/default.json")
    def defaultConfig = jsonReader.parseText(wireConfigFile.text)

    def customRepository = System.getenv("CUSTOM_REPOSITORY") ?: properties.getProperty("CUSTOM_REPOSITORY") ?: ''
    if (customRepository.isEmpty()) {
        throw new GradleException('No custom repo')
    }

    def customFolder = System.getenv("CUSTOM_FOLDER") ?: properties.getProperty("CUSTOM_FOLDER") ?: ''
    if (customFolder.isEmpty()) {
        throw new GradleException('Custom repo specified, but not custom folder')
    }

    def clientFolder = System.getenv("CLIENT_FOLDER") ?: properties.getProperty("CLIENT_FOLDER") ?: ''
    if (clientFolder.isEmpty()) {
        throw new GradleException('Custom repo specified, but not client folder')
    }

    def grGitUser = System.getenv("GRGIT_USER") ?: properties.getProperty("GRGIT_USER") ?: ''
    if (grGitUser.isEmpty()) {
        throw new GradleException('Custom repo specified, but no grgit user provided')
    }
    def grGitPassword = System.getenv("GRGIT_PASSWORD") ?: properties.getProperty("GRGIT_PASSWORD") ?: ''

    def customDirPath = customCheckoutDir + '/' + customFolder + '/' + clientFolder
    def customConfigFile = new File("$customDirPath/custom.json")

    // clean up
    if (file(customCheckoutDir).exists()) {
        delete file(customCheckoutDir)
    }

    def credentials = new Credentials(grGitUser, grGitPassword)
    Grgit.clone(dir: customCheckoutDir, uri: customRepository, credentials: credentials)
    project.logger.quiet("Using custom repository $customRepository -> folder $customFolder")

    def customConfig = jsonReader.parseText(customConfigFile.text)
    project.logger.quiet("Loaded custom build configuration for keys: ${customConfig.keySet()}")

    customConfig.keySet().forEach { key ->
        defaultConfig[key] = customConfig[key]
    }

    def buildtimeConfiguration = new BuildtimeConfiguration(defaultConfig, customDirPath)

    project.logger.quiet("Build time configuration is: ${JsonOutput.prettyPrint(JsonOutput.toJson(buildtimeConfiguration.configuration))}")

    return buildtimeConfiguration
}

class BuildtimeConfiguration {
    String customResourcesPath
    Object configuration

    BuildtimeConfiguration(Object configuration, String customResourcesPath) {
        this.configuration = configuration
        this.customResourcesPath = customResourcesPath
    }

    def isCustomBuild() {
        return customResourcesPath != null
    }
}

ext {
    buildtimeConfiguration = prepareCustomizationEnvironment()
}
