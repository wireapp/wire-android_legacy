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
        string(name: 'Flavor', defaultValue: '', description: 'Product Flavor to build (Experimental, Internal, Dev, Candidate, Prod, FDroid)')
        string(name: 'PatchVersion', defaultValue: '', description: 'PatchVersion for the build as a numeric value (e.g. 1337)')
        booleanParam(name: 'AppUnitTests', defaultValue: true, description: 'Run all app unit tests for this build')
        booleanParam(name: 'StorageUnitTests', defaultValue: true, description: 'Run all Storage unit tests for this build')
        booleanParam(name: 'ZMessageUnitTests', defaultValue: true, description: 'Run all zmessaging unit tests for this build')
        booleanParam(name: 'CompileFDroid', defaultValue: true, description: 'Defines if the fdroid flavor should be compiled in addition')
        booleanParam(name: 'UploadToAppCenter', defaultValue: true, description: 'Defines if a build should be uploaded to the appcenter project')
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
                    } else if(usedFlavor == 'Experimental') {
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
                sh "echo Version of the client: ${usedClientVersion}${PATCH_VERSION}"
            }
        }

        stage('repository setup') {
            steps {
                script {
                    last_stage = env.STAGE_NAME
                    currentBuild.displayName = "${usedFlavor}${usedBuildType}"
                    currentBuild.description = "Version [${usedClientVersion}] | Branch [${env.BRANCH_NAME}] | ASZ [${params.AppUnitTests},${params.StorageUnitTests},${params.ZMessageUnitTests}]"

                    //set the variable appCenterApiTokenForBranch
                    appCenterApiTokenForBranch = env.APPCENTER_API_TOKEN_${usedFlavor.toUpperCase()}
                    println("echo variable appCenterApiTokenForBranch has been set to [${appCenterApiTokenForBranch}]")
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

        stage('Generate version.txt') {
            steps {
                script {
                    last_stage = env.STAGE_NAME
                }
                sh './gradlew generateVersionFile'
                archiveArtifacts(artifacts: "app/version.txt", allowEmptyArchive: true, caseSensitive: true, onlyIfSuccessful: true)
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
                junit "app/build/test-results/**/*.xml"
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
                junit "storage/build/test-results/**/*.xml"
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
                sh "./gradlew :zmessaging:test${usedBuildType}UnitTest -PwireDeflakeTests=3"
                publishHTML(allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "zmessaging/build/reports/tests/test${usedBuildType}UnitTest/", reportFiles: 'index.html', reportName: 'ZMessaging Unit Test Report', reportTitles: 'ZMessaging Unit Test')
                junit "zmessaging/build/test-results/**/*.xml"
            }
        }

        stage('Assemble/Archive/Upload') {
            parallel {
                stage('Branch Client') {
                    stages {
                        stage('Assemble Branch') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                }
                                sh "./gradlew --profile assemble${usedFlavor}${usedBuildType}"
                                sh "ls -la app/build/outputs/apk"
                            }
                        }
                        stage('Archive Branch') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                }
                                archiveArtifacts(artifacts: "app/build/outputs/apk/wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.PATCH_VERSION}.apk", allowEmptyArchive: true, caseSensitive: true, onlyIfSuccessful: true)
                            }
                        }
                        stage('Upload Branch') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                    pathToUploadTo = "megazord/android/${usedFlavor.toLowerCase()}/${usedBuildType.toLowerCase()}/"
                                    fileNameForS3 = "wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${BRANCH_NAME.replaceAll('/','_')}-${usedClientVersion}${env.PATCH_VERSION}.apk"
                                    println("Uploading wire client with version [${usedClientVersion}${env.PATCH_VERSION}] to the the S3 Bucket [${env.S3_BUCKET_NAME}] to the folder [${pathToUploadTo}] under the name [${fileNameForS3}]")
                                }
                                s3Upload(acl: "${env.ACL_NAME}", file: "app/build/outputs/apk/wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.PATCH_VERSION}.apk", bucket: "${env.S3_BUCKET_NAME}", path: "${pathToUploadTo}${fileNameForS3}")
                            }
                        }

                        stage('Upload Branch to App Center') {
                            when {
                                expression { params.UploadToAppCenter}
                            }
                            steps {
                                script {
                                    last_started = env.STAGE_NAME
                                }
                                appCenter apiToken: appCenterApiTokenForBranch,
                                        ownerName: env.APPCENTER_API_ACCOUNT,
                                        appName: "wire-android-${usedFlavor.toLowerCase()}",
                                        pathToApp: "app/build/outputs/apk/wire-${usedFlavor.toLowerCase()}-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.BUILD_NUMBER}.apk",
                                        distributionGroups: env.APPCENTER_GROUP_NAME_LIST,
                                        branchName: env.BRANCH_NAME,
                                        commitHash: env.GIT_COMMIT
                            }
                        }

                        stage('Upload to PlayStore') {
                            when {
                                expression { env.PLAYSTORE_UPLOAD_ENABLED && env.BRANCH_NAME.equals("main") && usedBuildType.equals("Release") && usedFlavor.equals("Internal") }
                            }
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                    println("Uploading internal wire client with version [${usedClientVersion}${env.PATCH_VERSION}] to the Track [${env.WIRE_ANDROID_INTERNAL_TRACK_NAME}]")
                                }
                                androidApkUpload(googleCredentialsId: "${env.GOOGLE_PLAY_CREDS}", filesPattern: "app/build/outputs/apk/wire-internal-release-${usedClientVersion}${env.PATCH_VERSION}.apk", trackName: "${env.WIRE_ANDROID_INTERNAL_TRACK_NAME}", rolloutPercentage: '100', releaseName: "Internal Release ${usedClientVersion}${env.PATCH_VERSION}")
                            }
                        }
                    }
                }

                stage('Prod Client') {
                    when {
                        expression { usedFlavor != "Prod" && env.BRANCH_NAME == "release" }
                    }
                    stages {
                        stage('Assemble Prod') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                }
                                sh "./gradlew --profile assembleProd${usedBuildType}"
                                sh "ls -la app/build/outputs/apk"
                            }
                        }
                        stage('Archive Prod') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                }
                                archiveArtifacts(artifacts: "app/build/outputs/apk/wire-prod-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.PATCH_VERSION}.apk", allowEmptyArchive: true, caseSensitive: true, onlyIfSuccessful: true)
                            }
                        }
                        stage('Upload Prod to S3') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                    lastCommits = sh(
                                            script: "git log -5 --pretty=\"%h [%an] %s\" | sed \"s/^/    /\"",
                                            returnStdout: true
                                    )
                                    println("Uploading prod version of wire client with version [${usedClientVersion}${env.BUILD_NUMBER}] to the the S3 Bucket [${env.S3_BUCKET_NAME}] to the folder [megazord/android/prod/${usedBuildType.toLowerCase()}/]")
                                }
                                s3Upload(acl: "${env.ACL_NAME}", workingDir: "app/build/outputs/apk/", includePathPattern: "wire-prod-*.apk", bucket: "${env.S3_BUCKET_NAME}", path: "megazord/android/prod/${usedBuildType.toLowerCase()}/")
                                wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] Prod${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ✅ SUCCESS 🎉" +
                                                                    "\nLast 5 commits:\n```\n$lastCommits\n```"
                            }
                        }

                        stage('Upload Prod to App Center') {
                            when {
                                expression { params.UploadToAppCenter}
                            }
                            steps {
                                script {
                                    last_started = env.STAGE_NAME
                                }
                                appCenter apiToken: env.APPCENTER_API_TOKEN_PRODUCTION,
                                        ownerName: env.APPCENTER_API_ACCOUNT,
                                        appName: "wire-android-prod",
                                        pathToApp: "app/build/outputs/apk/wire-prod-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.BUILD_NUMBER}.apk",
                                        distributionGroups: env.APPCENTER_GROUP_NAME_LIST,
                                        branchName: env.BRANCH_NAME,
                                        commitHash: env.GIT_COMMIT
                            }
                        }

                        stage('Upload to PlayStore') {
                            when {
                                expression { env.PLAYSTORE_UPLOAD_ENABLED }
                            }
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                    println("Uploading prod wire client with version [${usedClientVersion}${env.BUILD_NUMBER}] to the Track [${env.WIRE_ANDROID_PROD_TRACK_NAME}]")
                                }
                                androidApkUpload(googleCredentialsId: "${env.GOOGLE_PLAY_CREDS}", filesPattern: "app/build/outputs/apk/wire-prod-release-${usedClientVersion}${env.BUILD_NUMBER}.apk", trackName: "${env.WIRE_ANDROID_PROD_TRACK_NAME}", rolloutPercentage: '100', releaseName: "Prod Release ${usedClientVersion}${env.BUILD_NUMBER}")
                            }
                        }
                    }
                }

                stage('FDroid') {
                    when {
                        expression { params.CompileFDroid }
                    }
                    stages {
                        stage('Assemble F-Droid') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                }
                                sh "./gradlew --profile assembleFDroid${usedBuildType}"
                                sh "ls -la app/build/outputs/apk"
                            }
                        }

                        stage('Archive F-Droid') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                }
                                archiveArtifacts(artifacts: "app/build/outputs/apk/wire-fdroid-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.PATCH_VERSION}.apk", allowEmptyArchive: true, caseSensitive: true, onlyIfSuccessful: true)
                            }
                        }

                        stage('Upload FDroid to S3') {
                            steps {
                                script {
                                    last_stage = env.STAGE_NAME
                                    lastCommits = sh(
                                            script: "git log -5 --pretty=\"%h [%an] %s\" | sed \"s/^/    /\"",
                                            returnStdout: true
                                    )
                                    println("Uploading fdroid version of wire client with version [${usedClientVersion}${env.BUILD_NUMBER}] to the the S3 Bucket [${env.S3_BUCKET_NAME}] to the folder [megazord/android/fdroid/${usedBuildType.toLowerCase()}/]")
                                }
                                s3Upload(acl: "${env.ACL_NAME}", workingDir: "app/build/outputs/apk/", includePathPattern: "wire-fdroid-*.apk", bucket: "${env.S3_BUCKET_NAME}", path: "megazord/android/fdroid/${usedBuildType.toLowerCase()}/")
                                wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] FDroid${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ✅ SUCCESS 🎉" +
                                                                    "\nLast 5 commits:\n```\n$lastCommits\n```"
                            }
                        }

                        stage('Upload F-Droid to App Center') {
                            when {
                                expression { params.UploadToAppCenter}
                            }
                            steps {
                                script {
                                    last_started = env.STAGE_NAME
                                }
                                appCenter apiToken: env.APPCENTER_API_TOKEN_FDROID,
                                        ownerName: env.APPCENTER_API_ACCOUNT,
                                        appName: "wire-android-fdroid",
                                        pathToApp: "app/build/outputs/apk/wire-fdroid-${usedBuildType.toLowerCase()}-${usedClientVersion}${env.BUILD_NUMBER}.apk",
                                        distributionGroups: env.APPCENTER_GROUP_NAME_LIST,
                                        branchName: env.BRANCH_NAME,
                                        commitHash: env.GIT_COMMIT
                            }
                        }
                    }
                }
            }
        }

        stage('Release to Github') {
            when {
                expression { env.BRANCH_NAME == "release" }
            }
            steps {
                script {
                    last_stage = env.STAGE_NAME
                    versionName = usedClientVersion =~ /(.*)\./
                    println("Releasing version to Github under Release Tag ${versionName} automatically")
                    println("THIS FEATURE IS NOT YET IMPLEMENTED")
                }
                sh 'echo NOT YET IMPLEMENTED'
            }
        }
    }

    post {
        failure {
            script {
                if(env.BRANCH_NAME.startsWith("PR-")) {
                    sh "curl -s -H \"Authorization: token ${env.GITHUB_API_TOKEN}\" -X POST -d \"{\\\"body\\\": \\\"Build Failure\\nID:${BUILD_NUMBER}\\nURL:[Link to Buildjob](${env.BUILD_URL})\\\"}\" \"https://api.github.com/repos/wireapp/wire-android/issues/${CHANGE_ID}/comments\""
                }
            }
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] ${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ❌ FAILED ($last_stage) 👎"
        }
        success {
            script {
                lastCommits = sh(
                        script: "git log -5 --pretty=\"%h [%an] %s\" | sed \"s/^/    /\"",
                        returnStdout: true
                )
                if(env.BRANCH_NAME.startsWith("PR-")) {
                    sh "curl -s -H \"Authorization: token ${env.GITHUB_API_TOKEN}\" -X POST -d \"{\\\"body\\\": \\\"Build Success\\nID:${BUILD_NUMBER}\\nURL:[Link to Buildjob](${env.BUILD_URL})\\\"}\" \"https://api.github.com/repos/wireapp/wire-android/issues/${CHANGE_ID}/comments\""
                }
            }
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] ${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ✅ SUCCESS 🎉" +
                    "\nLast 5 commits:\n```\n$lastCommits\n```"
        }
        aborted {
            script {
                if(env.BRANCH_NAME.startsWith("PR-")) {
                    sh "curl -s -H \"Authorization: token ${env.GITHUB_API_TOKEN}\" -X POST -d \"{\\\"body\\\": \\\"Build Aborted\\nID:${BUILD_NUMBER}\\nURL:[Link to Buildjob](${env.BUILD_URL})\\\"}\" \"https://api.github.com/repos/wireapp/wire-android/issues/${CHANGE_ID}/comments\""
                }
            }
            wireSend secret: env.WIRE_BOT_WIRE_ANDROID_SECRET, message: "[${env.BRANCH_NAME}] ${usedFlavor}${usedBuildType} **[${BUILD_NUMBER}](${BUILD_URL})** - ❌ ABORTED ($last_stage) "
        }
    }
}
