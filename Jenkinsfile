def defineFlavor() {
    //check if the pipeline has the custom flavor env variable set
    if(params.Flavor != '') {
        return params.Flavor
    }

    def branchName = env.BRANCH_NAME
    if (branchName == "main") {
        return 'Internal'
    } else if(branchName == "develop") {
        return 'Dev'
    } else if(branchName == "release") {
        return 'Candidate' //for release we build both in the same moment
    }
    return 'Experimental'
}

def defineBuildType() {
    if(params.BuildType != '') {
        return params.BuildType
    }

    if(env.BRANCH_NAME == "release") {
        return "Release"
    }

    return "Debug"
}

def definePatchVersion() {
    if(params.PatchVersion != '') {
        return params.PatchVersion
    }

    return env.BUILD_NUMBER
}

pipeline {
    agent {
        docker {
            image 'wire-android-agent:latest'
            label 'wire-android-builder'
            args '-u 1000:133 --network build-machine -v /var/run/docker.sock:/var/run/docker.sock -e DOCKER_HOST=unix:///var/run/docker.sock'
        }
    }

    parameters {
        string(name: 'ConfigFileId', defaultValue: 'wire-android-config', description: 'Name or ID of the Groovy file (under Jenkins -> Managed Files) that sets environment variables')
        string(name: 'BuildType', defaultValue: '', description: 'Build Type for the Client')
        string(name: 'Flavor', defaultValue: '', description: 'Product Flavor to build')
        string(name: 'PatchVersion', defaultValue: '', description: 'PatchVersion for the build E.G. X.Y.99999')
        booleanParam(name: 'AppUnitTests', defaultValue: true, description: 'Run all app unit tests for this build')
        booleanParam(name: 'StorageUnitTests', defaultValue: true, description: 'Run all Storage unit tests for this build')
        booleanParam(name: 'ZMessageUnitTests', defaultValue: true, description: 'Run all zmessaging unit tests for this build')
    }

    stages {
        stage('Setup environment preconditions') {
            steps {
                script {
                    last_started = env.STAGE_NAME
                    usedBuildType = defineBuildType()
                    usedFlavor = defineFlavor()

                    //get the usedClientVersion
                    def data = readFile(file: 'buildSrc/src/main/kotlin/Dependencies.kt')
                    usedClientVersion = ( data =~ /const val ANDROID_CLIENT_MAJOR_VERSION = "(.*)"/)[0][1]
                    println("Fetched ClientVersion from Dependencies.kt:"+usedClientVersion)

                    //used patchVersion
                    usedPatchVersion = definePatchVersion()
                }
                sh "echo Loading config file: ${params.ConfigFileId}"
                configFileProvider([
                        configFile( fileId: "${params.ConfigFileId}", variable: 'GROOVY_FILE_THAT_SETS_VARIABLES')
                ]) {
                    load env.GROOVY_FILE_THAT_SETS_VARIABLES
                }
                sh "echo ConfigFile ${params.ConfigFileId} loaded successfully"
                sh "echo Version of the client: ${usedClientVersion}${BUILD_NUMBER}"
            }
        }

        stage('prepare repository') {
            steps {
                script {
                    last_started = env.STAGE_NAME
                    currentBuild.displayName = "${usedFlavor}${usedBuildType}"
                    currentBuild.description = "Version [${usedClientVersion}] | Branch [${env.BRANCH_NAME}] | ASZ [${AppUnitTests},${StorageUnitTests},${ZMessageUnitTests}]"
                }
                configFileProvider([
                        configFile(fileId: "${env.SIGNING_GRADLE_FILE}", targetLocation: 'app/signing.gradle'),
                        configFile(fileId: "${env.Z_CLIENT_DEBUG_KEY_FILE}", targetLocation: 'app/zclient-debug-key.keystore.asc'),
                        configFile(fileId: "${env.Z_CLIENT_RELEASE_KEY_FILE}", targetLocation: 'app/zclient-release-key.keystore.asc'),
                        configFile(fileId: "${env.Z_CLIENT_TEST_KEY_FILE}", targetLocation: 'app/zclient-test-key.keystore.asc')
                ]) {
                    sh '''
					base64 --decode app/zclient-debug-key.keystore.asc > app/zclient-debug-key.keystore
                    base64 --decode app/zclient-release-key.keystore.asc > app/zclient-release-key.keystore
                    base64 --decode app/zclient-test-key.keystore.asc > app/zclient-test-key.keystore
				'''
                }
            }
        }

        stage('Preconditions') {
            parallel {
                stage('Check SDK/NDK') {
                    steps {
                        script {
                            last_started = env.STAGE_NAME
                        }
                        sh '''echo $ANDROID_HOME
echo $ANDROID_NDK_HOME'''
                    }
                }

                stage('Create local.properties') {
                    steps {
                        sh '''FILE=/local.properties
                                if test -f "$FILE"; then
                                    echo "local.properties exists already"
                                else
                                    echo "sdk.dir="$ANDROID_HOME >> local.properties
                                    echo "ndk.dir="$ANDROID_NDK_HOME >> local.properties
                                fi
                        '''
                    }
                }
            }
        }

        stage('App Unit Testing') {
            when {
                expression { params.AppUnitTests }
            }
            steps {
                script {
                    last_started = env.STAGE_NAME
                }
                sh "./gradlew :app:test${usedFlavor}${usedBuildType}UnitTest"
                publishHTML(allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "app/build/reports/tests/test${flavor}${BuildType}UnitTest/", reportFiles: 'index.html', reportName: 'Unit Test Report', reportTitles: 'Unit Test')
            }
        }

        stage('Storage Unit Testing') {
            when {
                expression { params.StorageUnitTests }
            }
            steps {
                script {
                    last_started = env.STAGE_NAME
                }
                sh "./gradlew :storage:test${usedBuildType}UnitTest"
                publishHTML(allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "storage/build/reports/tests/test${BuildType}UnitTest/", reportFiles: 'index.html', reportName: 'Storage Unit Test Report', reportTitles: 'Storage Unit Test')
            }
        }

        stage('ZMessage Unit Testing') {
            when {
                expression { params.ZMessageUnitTests }
            }
            steps {
                script {
                    last_started = env.STAGE_NAME
                }
                sh "./gradlew :zmessaging:test${usedBuildType}UnitTest -PwireDeflakeTests=1"
                publishHTML(allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "zmessaging/build/reports/tests/test${BuildType}UnitTest/", reportFiles: 'index.html', reportName: 'ZMessaging Unit Test Report', reportTitles: 'ZMessaging Unit Test')
            }
        }


        stage('Client Assembly') {
            parallel {
                stage('Branch Client') {
                    steps {
                        script {
                            last_started = env.STAGE_NAME
                        }
                        sh "./gradlew --profile assemble${usedFlavor}${usedBuildType}"
                        sh "ls -la app/build/outputs/apk"
                    }
                }
                stage('Prod Client') {
                    when {
                        expression { usedFlavor != "Prod" && env.BRANCH_NAME == "release" }
                    }
                    steps {
                        script {
                            last_started = env.STAGE_NAME
                        }
                        sh "./gradlew --profile assembleProd${usedBuildType}"
                        sh "ls -la app/build/outputs/apk"
                    }
                }
            }
        }

        stage('Save Artifact') {
            parallel {
                stage('Default Client') {
                    steps {
                        script {
                            last_started = env.STAGE_NAME
                        }
                        archiveArtifacts(artifacts: "app/build/outputs/apk/wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${usedClientVersion}${usedPatchVersion}.apk", allowEmptyArchive: true, caseSensitive: true, onlyIfSuccessful: true)
                    }
                }
                stage('Prod Client') {
                    when {
                        expression { usedFlavor != "Prod" && env.BRANCH_NAME == "release" }
                    }
                    steps {
                        script {
                            last_started = env.STAGE_NAME
                        }
                        archiveArtifacts(artifacts: "app/build/outputs/apk/wire-prod-${usedBuildType.toLowerCase()}-${usedClientVersion}${usedPatchVersion}.apk", allowEmptyArchive: true, caseSensitive: true, onlyIfSuccessful: true)
                    }
                }
            }
        }


        stage('Upload to S3') {
            parallel {
                stage('Default Client') {
                    steps {
                        script {
                            last_started = env.STAGE_NAME
                        }
                        s3Upload(acl: "${env.ACL_NAME}", file: "app/build/outputs/apk/wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${usedClientVersion}${usedPatchVersion}.apk", bucket: "${env.S3_BUCKET_NAME}", path: "megazord/android/${usedFlavor.toLowerCase()}/${usedBuildType.toLowerCase()}/wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${usedClientVersion}${usedPatchVersion}.apk")
                    }
                }
                stage('Prod Client') {
                    when {
                        expression { usedFlavor != "Prod" && env.BRANCH_NAME == "release" }
                    }
                    steps {
                        script {
                            last_started = env.STAGE_NAME
                        }
                        s3Upload(acl: "${env.ACL_NAME}", file: "app/build/outputs/apk/wire-prod-${usedBuildType.toLowerCase()}-${usedClientVersion}${usedPatchVersion}.apk", bucket: "${env.S3_BUCKET_NAME}", path: "megazord/android/prod/${usedBuildType.toLowerCase()}/wire-prod-${usedBuildType.toLowerCase()}-${usedClientVersion}${usedPatchVersion}.apk")
                        wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "Prod${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ✅ SUCCESS 🎉" +
                                                            "\nLast 5 commits:\n```\n$lastCommits\n```"
                    }
                }
            }
        }
    }

    post {
        failure {
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ❌ FAILED ($last_started) 👎"
        }
        success {
            script {
                lastCommits = sh(
                        script: "git log -5 --pretty=\"%h [%an] %s\" | sed \"s/^/    /\"",
                        returnStdout: true
                )
            }
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ✅ SUCCESS 🎉" +
                    "\nLast 5 commits:\n```\n$lastCommits\n```"
        }
        aborted {
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ❌ ABORTED ($last_started) "
        }
    }
}
