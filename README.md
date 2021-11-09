# Wire™

[![Wire logo](https://github.com/wireapp/wire/blob/master/assets/header-small.png?raw=true)](https://wire.com/jobs/)

This repository is part of the source code of Wire. You can find more information at [wire.com](https://wire.com) or by contacting opensource@wire.com.

You can find the published source code at [github.com/wireapp/wire](https://github.com/wireapp/wire), and the apk of the latest release at [https://wire.com/en/download/](https://wire.com/en/download/).

For licensing information, see the attached LICENSE file and the list of third-party licenses at [wire.com/legal/licenses/](https://wire.com/legal/licenses/).

If you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and cause that application to connect to our servers for any purposes, we refer to that resulting application as an “Open Source App”.  All Open Source Apps are subject to, and may only be used and/or commercialized in accordance with, the Terms of Use applicable to the Wire Application, which can be found at https://wire.com/legal/#terms.  Additionally, if you choose to build an Open Source App, certain restrictions apply, as follows:

a. You agree not to change the way the Open Source App connects and interacts with our servers; b. You agree not to weaken any of the security features of the Open Source App; c. You agree not to use our servers to store data for purposes other than the intended and original functionality of the Open Source App; d. You acknowledge that you are solely responsible for any and all updates to your Open Source App.

For clarity, if you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and do not cause that application to connect to our servers for any purposes, then that application will not be deemed an Open Source App and the foregoing will not apply to that application.

No license is granted to the Wire trademark and its associated logos, all of which will continue to be owned exclusively by Wire Swiss GmbH. Any use of the Wire trademark and/or its associated logos is expressly prohibited without the express prior written consent of Wire Swiss GmbH.

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

## Android Studio

When importing project in Android Studio **do not allow** gradle plugin update. Our build setup requires Android Plugin for Gradle version 3.2.1.

### Translations
Translation: https://crowdin.com/projects/wire
