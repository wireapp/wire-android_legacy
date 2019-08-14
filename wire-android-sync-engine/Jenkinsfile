pipeline {
  agent any
  stages {
    stage('Build') {
      steps {
        sh '''$HOME/Library/sbt/bin/sbt -J-Xmx4096M -J-XX:MaxPermSize=1024M -Ddebug=true -Dsbt.log.noformat=true clean test || echo "Tests failed"
$HOME/Library/sbt/bin/sbt -J-Xmx4096M -J-XX:MaxPermSize=1024M -Ddebug=true -Dsbt.log.noformat=true publish'''
      }
    }
  }
}