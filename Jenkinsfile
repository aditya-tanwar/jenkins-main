pipeline {
    agent {
        label 'built-in'
    }
    //tools {}
    options {
        ansi('xterm') // for color formatting
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30')) // only keeps the latest 30 builds and allow jenkins to discard older ones 
        disableConcurrentBuilds()
        skipStagesAfterUnstable ()
        // lock(resource: defineLockKey()) // NEED TO LEARN THIS PROPERLY
    }
    //environment {}

    // most important 
    stages {
        stage('Stage 1') {
            steps{
                echo 'Docker Version'
                sh 'docker --version'
                }
            }
        }
        // stage('Parallel Tests'){
        //     failFast True // Aborts remaining parallel branches if one fails 
        //     parallel{
        //         stage('parallel-1') {
        //             steps {}
        //         }
        //         stage('parallel-2'){
        //             steps {}
        //         }
        //     }
        // }
    // post {
    //     always {}
    //     success {}
    //     failure {}
    //     unstable {}
    //     changed {}
    // }

}

