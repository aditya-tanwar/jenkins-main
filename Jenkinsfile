@Library('jenkins-sharedlib') _

pipeline {
    agent { label 'devops-agent' }

    options {
        ansiColor('xterm')
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_REPO = 'adityatanwar03/sample-devops-app'
        SBOM_FILE = 'sbom.cdx.json'
        SLSA_FILE = 'slsa-provenance.json'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
                    env.GIT_COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.GIT_URL = sh(script: 'git config --get remote.origin.url || echo "local"', returnStdout: true).trim()
                }
            }
        }

        stage("Load groovy configuration"){
            steps {
                script {
                    k8sdeploy = load 'vars/k8sDeploy.groovy'
                }
            }

        }

        stage('Build & Security Scan') {
            steps {
                podmanBuildAndScan(
                    appRepo: env.APP_REPO,
                    imageTag: env.IMAGE_TAG,
                    dockerfile: 'sample-app/Dockerfile',
                    contextDir: 'sample-app',
                    sbomFile: env.SBOM_FILE
                )
            }
        }

        stage('Push, Sign & Attest') {
            steps {
                script {
                    env.IMAGE_REF = pushAndSignImage(
                        appRepo: env.APP_REPO,
                        imageTag: env.IMAGE_TAG,
                        dockerfile: 'sample-app/Dockerfile',
                        gitUrl: env.GIT_URL,
                        gitCommitSha: env.GIT_COMMIT_SHA,
                        sbomFile: env.SBOM_FILE,
                        slsaFile: env.SLSA_FILE
                    )
                }
            }
        }

        stage('Deploy to K8s') {
            steps {
                k8sdeploy.deployToSameK8s(
                    namespace: 'demo-app',
                    manifestPath: 'k8s/sample-app.yaml',
                    imageRef: env.IMAGE_REF,
                    deployment: 'sample-app'
                )
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: "${SBOM_FILE},${SLSA_FILE}", allowEmptyArchive: true
        }
        cleanup {
            sh 'rm -rf .docker rendered-manifest.yaml image-digest.txt image.tar'
        }
    }
}
