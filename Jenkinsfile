pipeline {
    agent {
        label 'devops-agent'

        //or 

        // kubernetes {
        //     cloud 'kubernetes'
        //     inheritFrom 'base-agent'
        //     //namespace 'jenkins-workers'
        //     //yamlFile 'jenkins-agents/agent-1.yaml' // calling the jenkins agent pod yaml 
        // }
    }
    //tools {}
    options {
        ansiColor ('xterm') // for color formatting
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
        // stage('Stage 1') {
        //     steps{
        //         echo 'Docker Version'
        //         sh 'docker --version'
        //         }
        //     }
        
        stage('Stage 2') {
            steps{
                script {
                sh 'whoami'
                sh 'hostname'
                sh 'terraform --version'
            }
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

