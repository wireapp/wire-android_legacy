import groovy.json.JsonSlurper

def defaultBuildtimeConfiguration() {
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
        project.logger.quiet("This is not a custom build (no custom repo)")
        return new BuildtimeConfiguration(defaultConfig, null)
    }

    return null
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
    // configuration and external config
    customCheckoutDir = "$rootDir/custom"
    buildtimeConfiguration = defaultBuildtimeConfiguration()
}
