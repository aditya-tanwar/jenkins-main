pipeline {
    agent {
        label 'devops-agent'
    }

    options {
        ansiColor('xterm')
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
        disableConcurrentBuilds()
        skipStagesAfterUnstable()
    }

    environment {
        APP_REPO = 'adityatanwar03/sample-devops-app'
        DOCKERHUB_CREDENTIALS = 'dockerhub-creds'
        APP_NAMESPACE = 'demo-app'
        DIGEST_FILE = 'image-digest.txt'
        SBOM_FILE = 'sbom.cdx.json'
        SLSA_FILE = 'slsa-provenance.json'
    }

    stages {
        stage('Checkout & Tool Check') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
                    env.GIT_COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.GIT_URL = sh(script: 'git config --get remote.origin.url || echo "local"', returnStdout: true).trim()
                }
                sh '''
                    whoami
                    hostname
                    kaniko version
                    syft version
                    grype version
                    cosign version
                    kubectl version --client
                '''
            }
        }

        stage('Build & Push Sample App (Kaniko)') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: "${DOCKERHUB_CREDENTIALS}",
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_TOKEN'
                    )
                ]) {
                    sh '''
                        set -euo pipefail
                        export DOCKER_CONFIG="$WORKSPACE/.docker"
                        mkdir -p "$DOCKER_CONFIG"
                        AUTH=$(printf '%s' "$DOCKER_USER:$DOCKER_TOKEN" | base64 | tr -d '\\n')
                        printf '{"auths":{"https://index.docker.io/v1/":{"username":"%s","password":"%s","auth":"%s"}}}' \
                            "$DOCKER_USER" "$DOCKER_TOKEN" "$AUTH" \
                            > "$DOCKER_CONFIG/config.json"

                        kaniko \
                            --context="$WORKSPACE/sample-app" \
                            --dockerfile="$WORKSPACE/sample-app/Dockerfile" \
                            --destination="${APP_REPO}:latest" \
                            --destination="${APP_REPO}:${IMAGE_TAG}" \
                            --digest-file="$WORKSPACE/${DIGEST_FILE}" \
                            --cache=false
                    '''
                }

                script {
                    def digest = readFile("${env.WORKSPACE}/${env.DIGEST_FILE}").trim()
                    if (!digest.startsWith('sha256:')) {
                        error "Invalid digest captured: ${digest}"
                    }
                    env.IMAGE_DIGEST = digest
                    env.IMAGE_REF = "${env.APP_REPO}@${env.IMAGE_DIGEST}"
                    echo "Immutable image: ${env.IMAGE_REF}"
                }
            }
        }

        stage('SBOM & Vulnerability Scan') {
            steps {
                sh '''
                    set -euo pipefail
                    syft "registry:${IMAGE_REF}" -o cyclonedx-json > "${SBOM_FILE}"
                    grype "sbom:${SBOM_FILE}" --fail-on high
                '''
            }
        }

        stage('SLSA Provenance & Cosign Sign') {
            steps {
                withCredentials([
                    file(credentialsId: 'cosign-private-key', variable: 'COSIGN_KEY'),
                    string(credentialsId: 'cosign-password', variable: 'COSIGN_PASSWORD')
                ]) {
                    sh '''
                        set -euo pipefail

                        cat <<EOF > "${SLSA_FILE}"
{
  "buildDefinition": {
    "buildType": "https://jenkins.io/kaniko-devops-agent/v1",
    "externalParameters": {
      "repository": "${GIT_URL}",
      "commit": "${GIT_COMMIT_SHA}",
      "dockerfile": "sample-app/Dockerfile"
    },
    "internalParameters": {
      "jenkinsJob": "${JOB_NAME}",
      "buildNumber": "${BUILD_NUMBER}",
      "nodeName": "${NODE_NAME}"
    },
    "resolvedDependencies": [
      {
        "uri": "${GIT_URL}",
        "digest": { "sha1": "${GIT_COMMIT_SHA}" }
      }
    ]
  },
  "runDetails": {
    "builder": {
      "id": "${JENKINS_URL:-jenkins-in-cluster}"
    },
    "metadata": {
      "invocationId": "${BUILD_TAG}"
    }
  }
}
EOF

                        cosign sign --yes --key "${COSIGN_KEY}" "${IMAGE_REF}"

                        cosign attest --yes \
                          --key "${COSIGN_KEY}" \
                          --predicate "${SBOM_FILE}" \
                          --type cyclonedx \
                          "${IMAGE_REF}"

                        cosign attest --yes \
                          --key "${COSIGN_KEY}" \
                          --predicate "${SLSA_FILE}" \
                          --type slsaprovenance \
                          "${IMAGE_REF}"
                    '''
                }
            }
        }

        stage('Deploy to Same Kubernetes Cluster') {
            steps {
                sh '''
                    set -euo pipefail
                    kubectl create namespace "${APP_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

                    sed "s|PLACEHOLDER_IMAGE_REF|${IMAGE_REF}|g" k8s/sample-app.yaml > rendered-sample-app.yaml
                    kubectl apply -f rendered-sample-app.yaml
                    kubectl rollout status deployment/sample-app -n "${APP_NAMESPACE}" --timeout=120s
                    kubectl get pods,svc -n "${APP_NAMESPACE}"
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: "${SBOM_FILE},${SLSA_FILE}", allowEmptyArchive: true
        }
        cleanup {
            script {
                ['.docker', 'rendered-sample-app.yaml'].each { relPath ->
                    def target = new File("${env.WORKSPACE}/${relPath}")
                    if (target.exists()) {
                        if (target.isDirectory()) {
                            target.deleteDir()
                        } else {
                            target.delete()
                        }
                    }
                }
            }
        }
    }
}