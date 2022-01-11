# Wire™

[![Wire logo](https://github.com/wireapp/wire/blob/master/assets/header-small.png?raw=true)](https://wire.com/jobs/)

This repository is part of the source code of Wire. You can find more information at [wire.com](https://wire.com) or by contacting opensource@wire.com.

You can find the published source code at [github.com/wireapp/wire](https://github.com/wireapp/wire), and the apk of the latest release at [https://wire.com/en/download/](https://wire.com/en/download/).

For licensing information, see the attached LICENSE file and the list of third-party licenses at [wire.com/legal/licenses/](https://wire.com/legal/licenses/).

If you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and cause that application to connect to our servers for any purposes, we refer to that resulting application as an “Open Source App”.  All Open Source Apps are subject to, and may only be used and/or commercialized in accordance with, the Terms of Use applicable to the Wire Application, which can be found at https://wire.com/legal/#terms.  Additionally, if you choose to build an Open Source App, certain restrictions apply, as follows:

a. You agree not to change the way the Open Source App connects and interacts with our servers; b. You agree not to weaken any of the security features of the Open Source App; c. You agree not to use our servers to store data for purposes other than the intended and original functionality of the Open Source App; d. You acknowledge that you are solely responsible for any and all updates to your Open Source App.

For clarity, if you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and do not cause that application to connect to our servers for any purposes, then that application will not be deemed an Open Source App and the foregoing will not apply to that application.

No license is granted to the Wire trademark and its associated logos, all of which will continue to be owned exclusively by Wire Swiss GmbH. Any use of the Wire trademark and/or its associated logos is expressly prohibited without the express prior written consent of Wire Swiss GmbH.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.wire/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.wire)

# Wire for Android

## What is included in the open source client

The project in this repository contains the Wire for Android client project. You can build the project yourself. However, there are some differences with the binary Wire client available on the Play Store.
These differences are:

- the open source project does not include the API keys of 3rd party services.
- the open source project links against the open source Wire audio-video-signaling (AVS) library. The binary Play Store client links against an AVS version that contains proprietary improvements for the call quality.

## Prerequisites

In order to build Wire for Android locally, it is necessary to install the following tools on the local machine:

- JDK 8
- Android SDK
- Android NDK

## How to build locally

1. Check out the `wire-android` repository.
2. Switch to latest release branch `release`
3. From the checkout folder, run `./gradlew assembleProdRelease`. This will pull in all the necessary dependencies from Maven.

These steps will build only the Wire client UI, pulling in all other Wire frameworks from Maven. If you want to modify the source/debug other Wire frameworks, you can check project dependencies and build other wire projects separately. The most interesting projects to check are:

- [Audio Video Signaling](https://github.com/wireapp/avs)
- [generic-message-proto](https://github.com/wireapp/generic-message-proto)


## How to build using docker and docker compose

We have added a docker compose file and a docker agent file + a configuration script, to make it possible to compile wire android with just one line of code.
There are 2 possible ways to build a client with docker compose

Option 1: Configuring the `docker-compose.yml` file:

The docker compose YML file contains some flags which you can change and modify to change what type of client will be build

1. `BUILD_TYPE`: This value defines what build type you want to build, it can either be Release or Debug
2. `FLAVOR_TYPE`: This value defines the flavor type of build. It can be one of the following: Dev, Prod, Experimental, FDroid, Internal, Candidate
3. `PATCH_VERSION`: Here you can define the value which is supposed to be used for the PATCH_LEVEL version of your client. EG XX.XX.1337
4. `CLEAN_PROJECT_BEFORE_BUILD`: Define if the project branch should be cleaned by .`/gradlew clean`  before anything else [options: true or false]
5. `RUN_APP_UNIT_TESTS`: Define if the app unit tests should be executed before compilation  [options: true or false]
6. `RUN_STORAGE_UNIT_TESTS`: Define if the storage unit tests should be executed before compilation  [options: true or false]
7. `RUN_ZMESSAGE_UNIT_TEST`: Define if the zmessage unit tests should be executed before compilation [options: true or false]
8. `BUILD_CLIENT`: Define if the compilation/build should be executed, disable this if you wanna just sign an apk build in a previous run [options: true or false]
9. `SIGN_APK`: Define if an APK should be signed with the following given information  [options: true or false]
10. `KEYSTORE_PATH`: The path to your keystore (root folder is wire-android inside the docker)
11. `KSTOREPWD`: The keystore password for the keystore file
12. `KEYPWD`: The key password
13. `KEYSTORE_KEY_NAME`: The key name
14. `BUILD_WITH_STACKTRACE`: Define if you want to compile the build with the option `--stacktrace` (do this if you have compilation issues and the general error message is not helpful)

Configure these values and use the following command to compile a client OOO (Out of the Box)

`docker-compose up --build [-d]`

Or if you use a newer version of docker compose

`docker compose up --build [-d]`

`-d` means to spawn the docker agent detached, so you can continue using your terminal while the agent is building the client

Option 2: Use ENV Flags

The flags, which exists inside the docker file, can also be overwritten by directly writing them into the terminal line. 

See the example below:

`export BUILD_TYPE=Release && export FLAVOR=FDroid && docker compose up --build -d`


## Custom Builds

wire-android allows it to compile a client with custom configurations without having to modify the default.json
for this all you need todo is to add configure the following variables

1. CUSTOM_REPOSITORY: the repository uri on github for the custom content
2. CUSTOM_FOLDER: a parental folder
3. CUSTOM_CLIENT: the main folder for the custom client containing the custom.json and the icons folder (optional)
4. GRGIT_USER: either the github api token or the user name for the github account, which will be used by the build script system to fetch the custom repo on build
5. GRGIT_PASSWORD: the github password if you use the user name instead of a github api token

see the outcommented example inside the docker-compose.yml file as a reference

## Android Studio

When importing project in Android Studio **do not allow** gradle plugin update. Our build setup requires Android Plugin for Gradle version 3.2.1.

### Translations
Translation: https://crowdin.com/projects/wire
