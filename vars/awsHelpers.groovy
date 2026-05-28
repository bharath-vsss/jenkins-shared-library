// vars/orchestrateDeployment.groovy

// 1. Centralized Configuration Map (Internal Private Helper)
def getConstants() {
    return [
        AWS_ACCOUNT_ID   : "510931056289",
        REGION           : "ap-southeast-2",
        IMAGE_REPO       : "devops_repo/app",
        CHART_REPO       : "ecommerce-app", // 👈 Added dedicated Helm repository path
        CHART_NAME       : "ecommerce",
        ECR_URL          : "510931056289.dkr.ecr.ap-southeast-2.amazonaws.com"
    ]
}

// 2. Main Entry Point
def call(Map config = [:]) {
    def envs = getConstants()
    def fullImageUri = "${envs.ECR_URL}/${envs.IMAGE_REPO}"

    if (config.action == 'pushImage') {
        echo "Starting Docker push sequence for version: ${config.appVersion}"
        
        sh "aws ecr get-login-password --region ${envs.REGION} | docker login --username AWS --password-stdin ${envs.ECR_URL}"
        sh """
            docker tag studentapp:${config.appVersion} ${fullImageUri}:${config.appVersion}
            docker push ${fullImageUri}:${config.appVersion}
        """
        
    } else if (config.action == 'deployHelm') {
        echo "Starting Helm package and remote deploy sequence for version: ${config.appVersion}"
        
        // Log helm into ECR OCI registry
        sh "aws ecr get-login-password --region ${envs.REGION} | helm registry login --username AWS --password-stdin ${envs.ECR_URL}"
        
        // Package the chart using your local directory
        sh "helm package ecommerce/ --version 0.1.0 --app-version ${config.appVersion}"
        
        // UPDATE: Pushing directly to the new dedicated 'ecommerce-app' repository
        sh "helm push ecommerce-app-0.1.0.tgz oci://${envs.ECR_URL}"
        
        // UPDATE: Pull and upgrade from the dedicated 'ecommerce-app' OCI repository path
        sh """
            helm upgrade --install ecommerce-release oci://${envs.ECR_URL}/${envs.CHART_REPO} \
              --version 0.1.0 \
              --set image.repository=${envs.ECR_URL}/${envs.IMAGE_REPO} \
              --set image.tag=${config.appVersion} \
              --set imagePullSecrets[0].name=ecr-registry-secret \
              --kubeconfig ${config.kubeconfigPath}
        """
    } else {
        error "Invalid action '${config.action}' passed to orchestrateDeployment step. Please pass action: 'pushImage' or action: 'deployHelm'."
    }
}
