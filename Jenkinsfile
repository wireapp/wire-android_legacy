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
        string(name: 'BuildType', defaultValue: '', description: 'Build Type for the Client (Release or Debug)')
        string(name: 'Flavor', defaultValue: '', description: 'Product Flavor to build (Experimental, Internal, Dev, Candidate, Release)')
        string(name: 'PatchVersion', defaultValue: '', description: 'PatchVersion for the build as a numeric value (e.g. 1337)')
        booleanParam(name: 'AppUnitTests', defaultValue: true, description: 'Run all app unit tests for this build')
        booleanParam(name: 'StorageUnitTests', defaultValue: true, description: 'Run all Storage unit tests for this build')
        booleanParam(name: 'ZMessageUnitTests', defaultValue: false, description: 'Run all zmessaging unit tests for this build')
    }

    stages {
        stage('pipeline preconditions') {
            steps {
                script {
                    last_stage = env.STAGE_NAME

                    //define the flavor
                    if(params.Flavor != '') {
                        usedFlavor = params.Flavor
                        println("Using params.Flavor for usedFlavor")
                    } else {
                        def branchName = env.BRANCH_NAME
                        if (branchName == "main") {
                            usedFlavor = 'Internal'
                        } else if(branchName == "develop") {
                            usedFlavor = 'Dev'
                        } else if(branchName == "release") {
                            usedFlavor = 'Candidate' //for release we build both in the same moment
                        } else {
                            usedFlavor = 'Experimental'
                        }
                    }
                    println("Flavor is:" + usedFlavor)

                    //define the build type
                    if(params.BuildType != '') {
                        usedBuildType = params.BuildType
                        println("using params.BuildType for usedBuildType")
                    } else if(usedFlavor != 'Experimental') {
                        usedBuildType =  "Debug"
                    } else {
                        usedBuildType =  "Release"
                    }
                    println("Build Type is:" + usedBuildType)

                    //fetch the clientVesion
                    def data = readFile(file: 'buildSrc/src/main/kotlin/Dependencies.kt')
                    foundClientVersion = ( data =~ /const val ANDROID_CLIENT_MAJOR_VERSION = "(.*)"/)[0][1]
                    println("Fetched ClientVersion from Dependencies.kt:" + foundClientVersion)
                    usedClientVersion = foundClientVersion

                    //define the patch version
                    if(params.PatchVersion != '') {
                        env.PATCH_VERSION = params.PatchVersion
                        println("using params.PatchVersion for env.PATCH_VERSION")
                    } else {
                        env.PATCH_VERSION = env.BUILD_NUMBER
                        println("using env.BUILD_NUMBER for env.PATCH_VERSION")
                    }
                    println("env.PATCH_VERSION has been set to: " + env.PATCH_VERSION)
                }

                //load the config file from jenkins which contains all necessary env variables
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

        stage('repository setup') {
            steps {
                script {
                    last_stage = env.STAGE_NAME
                    currentBuild.displayName = "${usedFlavor}${usedBuildType}"
                    currentBuild.description = "Version [${usedClientVersion}] | Branch [${env.BRANCH_NAME}] | ASZ [${params.AppUnitTests},${params.StorageUnitTests},${params.ZMessageUnitTests}]"
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
                            last_stage = env.STAGE_NAME
                            println(env.ANDROID_HOME)
                            println(env.ANDROID_NDK_HOME)
                        }
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
                    last_stage = env.STAGE_NAME
                }
                sh "./gradlew :app:test${usedFlavor}${usedBuildType}UnitTest"
                publishHTML(allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "app/build/reports/tests/test${usedFlavor}${usedBuildType}UnitTest/", reportFiles: 'index.html', reportName: 'Unit Test Report', reportTitles: 'Unit Test')
            }
        }

        stage('Storage Unit Testing') {
            when {
                expression { params.StorageUnitTests }
            }
            steps {
                script {
                    last_stage = env.STAGE_NAME
                }
                sh "./gradlew :storage:test${usedBuildType}UnitTest"
                publishHTML(allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "storage/build/reports/tests/test${usedBuildType}UnitTest/", reportFiles: 'index.html', reportName: 'Storage Unit Test Report', reportTitles: 'Storage Unit Test')
            }
        }

        stage('ZMessage Unit Testing') {
            when {
                expression { params.ZMessageUnitTests }
            }
            steps {
                script {
                    last_stage = env.STAGE_NAME
                }
                sh "./gradlew :zmessaging:test${usedBuildType}UnitTest -PwireDeflakeTests=1"
                publishHTML(allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "zmessaging/build/reports/tests/test${usedBuildType}UnitTest/", reportFiles: 'index.html', reportName: 'ZMessaging Unit Test Report', reportTitles: 'ZMessaging Unit Test')
            }
        }


        stage('Client Assembly') {
            parallel {
                stage('Branch Client') {
                    steps {
                        script {
                            last_stage = env.STAGE_NAME
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
                            last_stage = env.STAGE_NAME
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
                            last_stage = env.STAGE_NAME
                        }
                        archiveArtifacts(artifacts: "app/build/outputs/apk/wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.PATCH_VERSION}.apk", allowEmptyArchive: true, caseSensitive: true, onlyIfSuccessful: true)
                    }
                }
                stage('Prod Client') {
                    when {
                        expression { usedFlavor != "Prod" && env.BRANCH_NAME == "release" }
                    }
                    steps {
                        script {
                            last_stage = env.STAGE_NAME
                        }
                        archiveArtifacts(artifacts: "app/build/outputs/apk/wire-prod-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.PATCH_VERSION}.apk", allowEmptyArchive: true, caseSensitive: true, onlyIfSuccessful: true)
                    }
                }
            }
        }


        stage('Upload to S3') {
            parallel {
                stage('Default Client') {
                    steps {
                        script {
                            last_stage = env.STAGE_NAME
                            if(env.BRANCH_NAME.startsWith("PR-")) {
                                pathToUploadTo = "megazord/android/pr/${usedFlavor.toLowerCase()}/${usedBuildType.toLowerCase()}"
                                 fileNameForS3 = "wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${env.BRANCH_NAME}-${usedClientVersion}${env.PATCH_VERSION}.apk"
                        } else {
                                pathToUploadTo = "megazord/android/${usedFlavor.toLowerCase()}/${usedBuildType.toLowerCase()}/"
                                fileNameForS3 = "wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${BRANCH_NAME.replaceAll('/','_')}-${usedClientVersion}${env.PATCH_VERSION}.apk"
                            }
                        }
                        s3Upload(acl: "${env.ACL_NAME}", workingDir: "app/build/outputs/apk/", includePathPattern: "wire-*.apk", bucket: "${env.S3_BUCKET_NAME}", path: "${pathToUploadTo}${fileNameForS3}")
                    }
                }
                stage('Prod Client') {
                    when {
                        expression { usedFlavor != "Prod" && env.BRANCH_NAME == "release" }
                    }
                    steps {
                        script {
                            last_stage = env.STAGE_NAME
                        }
                        s3Upload(acl: "${env.ACL_NAME}", workingDir: "app/build/outputs/apk/", file: "wire-*.apk", bucket: "${env.S3_BUCKET_NAME}", path: "megazord/android/prod/${usedBuildType.toLowerCase()}/")
                        wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] Prod${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ✅ SUCCESS 🎉" +
                                                            "\nLast 5 commits:\n```\n$lastCommits\n```"
                    }
                }
            }
        }
    }

    post {
        failure {
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] ${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ❌ FAILED ($last_stage) 👎"
        }
        success {
            script {
                lastCommits = sh(
                        script: "git log -5 --pretty=\"%h [%an] %s\" | sed \"s/^/    /\"",
                        returnStdout: true
                )
            }
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] ${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ✅ SUCCESS 🎉" +
                    "\nLast 5 commits:\n```\n$lastCommits\n```"
        }
        aborted {
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] ${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ❌ ABORTED ($last_stage) "
        }
    }
}
