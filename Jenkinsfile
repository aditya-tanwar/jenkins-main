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
        STORAGE_DRIVER = 'vfs'
        REGISTRY_AUTH_FILE = "${WORKSPACE}/.docker/config.json"
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
                    buildah --version
                    syft version
                    grype version
                    cosign version
                    kubectl version --client
                '''
            }
        }

        stage('Build & Push Sample App (Buildah)') {
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
                        set +x

                        mkdir -p "$(dirname "$REGISTRY_AUTH_FILE")"
                        printf '{}' > "$REGISTRY_AUTH_FILE"

                        printf '%s' "$DOCKER_TOKEN" | buildah login \
                            --username "$DOCKER_USER" \
                            --password-stdin \
                            docker.io

                        buildah bud \
                            --network=host \
                            --storage-driver=vfs \
                            --layers \
                            -f "$WORKSPACE/sample-app/Dockerfile" \
                            -t "${APP_REPO}:${IMAGE_TAG}" \
                            -t "${APP_REPO}:latest" \
                            "$WORKSPACE/sample-app"

                        # Push immutable tag and capture its sha256 digest into DIGEST_FILE
                        buildah push \
                            --digestfile="$WORKSPACE/${DIGEST_FILE}" \
                            "${APP_REPO}:${IMAGE_TAG}"

                        buildah push "${APP_REPO}:latest"
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
                        export DOCKER_CONFIG="$(dirname "$REGISTRY_AUTH_FILE")"

                        cat <<EOF > "${SLSA_FILE}"
{
  "buildDefinition": {
    "buildType": "https://jenkins.io",
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
                ['.docker', 'rendered-sample-app.yaml', env.DIGEST_FILE].each { relPath ->
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
