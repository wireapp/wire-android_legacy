#!/bin/bash


if [ "$CLEAN_PROJECT_BEFORE_BUILD" = true ] ; then
    echo "Cleaning the Project"
    ./gradlew clean
else
    echo "Cleaning the project will be skipped"
fi

if [ "$RUN_APP_UNIT_TESTS" = true ] ; then
   echo "Running App Unit Tests" 
   ./gradlew :app:test${FLAVOR_TYPE}${BUILD_TYPE}UnitTest
else
   echo "App Unit Tests will be skipped"
fi

if [ "$RUN_STORAGE_UNIT_TESTS" = true ] ; then
    echo "Running Storage Unit Tests"
    ./gradlew :storage:test${FLAVOR_TYPE}${BUILD_TYPE}UnitTest
else
    echo "Storage Unit Tests will be skipped"
fi

if [ "$RUN_ZMESSAGE_UNIT_TESTS" = true ] ; then
   echo "Running ZMessaging Unit Tests" 
   ./gradlew :zmessage:test${BUILD_TYPE}UnitTest
else
   echo "ZMessaging Unit Tests will be skipped"
fi

if [ "$BUILD_CLIENT" = true ] ; then
    echo "Compiling the client with Flavor:${FLAVOR_TYPE} and BuildType: ${BUILD_TYPE}"
    ./gradlew assemble${FLAVOR_TYPE}${BUILD_TYPE}
else
    echo "Building the client will be skipped"
fi
