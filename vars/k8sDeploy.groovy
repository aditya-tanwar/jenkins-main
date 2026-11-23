def deployToSameK8s(Map config = [:]) {
    String namespace    = config.namespace
    String manifestPath = config.manifestPath
    String imageRef     = config.imageRef

    sh """
        set -euo pipefail
        kubectl create namespace "${namespace}" --dry-run=client -o yaml | kubectl apply -f -
        sed "s|PLACEHOLDER_IMAGE_REF|${imageRef}|g" "${manifestPath}" > rendered-manifest.yaml
        kubectl apply -f rendered-manifest.yaml
    """
}

def checkSameK8sStatus(Map config = [:]) {
    String namespace  = config.namespace
    String deployment = config.deployment
    String timeout    = config.timeout ?: '120s'

    sh """
        set -euo pipefail
        kubectl rollout status deployment/${deployment} -n "${namespace}" --timeout=${timeout}
        kubectl get pods,svc -n "${namespace}"
    """
}

return this
