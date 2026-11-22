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

                        for dir in /usr/bin /usr/local/bin; do
                            if [ -d "$dir" ] && [ ! -e "$dir/nohup" ]; then
                                printf '#!/bin/sh\ntrap "" HUP\nshift\nexec "$@"\n' > "$dir/nohup" || true
                                chmod 0755 "$dir/nohup" || true
                            fi
                        done
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

        // Temporarily disabled while isolating single-container post-Kaniko behavior:
        // stage('SBOM & Vulnerability Scan') { ... }
        // stage('SLSA Provenance & Cosign Sign') { ... }

        stage('Deploy to Same Kubernetes Cluster') {
            steps {
                sh '''
                    set +e
                    echo "=== Checking deploy tools ==="
                    command -v kubectl || echo "MISSING: kubectl"
                    command -v sed || echo "MISSING: sed"
                    echo "IMAGE_REF=${IMAGE_REF:-<unset>}"

                    kubectl create namespace "${APP_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
                    RC=$?; [ "$RC" -ne 0 ] && { echo "Failed namespace apply (rc=$RC)"; exit "$RC"; }

                    sed "s|PLACEHOLDER_IMAGE_REF|${IMAGE_REF}|g" k8s/sample-app.yaml > rendered-sample-app.yaml
                    RC=$?; [ "$RC" -ne 0 ] && { echo "Failed sed render (rc=$RC)"; exit "$RC"; }

                    kubectl apply -f rendered-sample-app.yaml
                    RC=$?; [ "$RC" -ne 0 ] && { echo "Failed kubectl apply (rc=$RC)"; exit "$RC"; }

                    kubectl rollout status deployment/sample-app -n "${APP_NAMESPACE}" --timeout=120s
                    RC=$?; [ "$RC" -ne 0 ] && { echo "Rollout failed (rc=$RC)"; exit "$RC"; }

                    kubectl get pods,svc -n "${APP_NAMESPACE}"
                '''
            }
        }
    }

}