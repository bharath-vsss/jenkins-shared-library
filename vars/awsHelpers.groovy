
// vars/orchestrateDeployment.groovy

// 1. Centralized Configuration Map (Internal Private Helper)
def getConstants() {
    return [
        AWS_ACCOUNT_ID : "510931056289",
        REGION         : "ap-southeast-2",
        IMAGE_REPO     : "devops_repo/app",
        CHART_NAME     : "ecommerce-app",
        ECR_URL        : "510931056289.dkr.ecr.ap-southeast-2.amazonaws.com"
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
        
        // Package the chart using your 'helm/' directory
        sh "helm package helm/ --version 0.1.0 --app-version ${config.appVersion}"
        
        // Push the packaged chart to ECR
        sh "helm push ${envs.CHART_NAME}-0.1.0.tgz oci://${envs.ECR_URL}/"
        
        // Pull helm chart down to verify the OCI artifact download
        sh "helm pull oci://${envs.ECR_URL}/${envs.CHART_NAME} --version 0.1.0 --untar"
        
        // Deploy directly to the remote self-managed cluster via the injected Kubeconfig
        sh """
            helm upgrade --install ecommerce-release ./${envs.CHART_NAME} \
              --set image.repository=${envs.ECR_URL}/${envs.IMAGE_REPO} \
              --set image.tag=${config.appVersion} \
              --kubeconfig ${config.kubeconfigPath}
        """
    } else {
        error "Invalid action '${config.action}' passed to orchestrateDeployment step. Please pass action: 'pushImage' or action: 'deployHelm'."
    }
}
