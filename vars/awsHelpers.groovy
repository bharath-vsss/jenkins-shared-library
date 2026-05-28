// vars/orchestrateDeployment.groovy

// 1. Centralized Configuration Map (Internal Private Helper)
def getConstants() {
    return [
        AWS_ACCOUNT_ID   : "510931056289",
        REGION           : "ap-southeast-2",
        IMAGE_REPO       : "devops_repo/app",
        CHART_REPO       : "ecommerce-app", // Dedicated Helm repository path in ECR
        CHART_NAME       : "ecommerce",
        ECR_URL          : "510931056289.dkr.ecr.ap-southeast-2.amazonaws.com"
    ]
}

// 2. Main Entry Point
def call(Map config = [:]) {
    def envs = getConstants()
    def fullImageUri = "${envs.ECR_URL}/${envs.IMAGE_REPO}"
    
    // Sanitize the version string to strip out hidden newlines or terminal prompt junk
    def cleanAppVersion = config.appVersion ? config.appVersion.toString().trim() : null

    if (config.action == 'pushImage') {
        if (!cleanAppVersion) { error "appVersion parameter is missing for pushImage action!" }
        echo "Starting Docker push sequence for version: ${cleanAppVersion}"
        
        sh "aws ecr get-login-password --region ${envs.REGION} | docker login --username AWS --password-stdin ${envs.ECR_URL}"
        sh """
            docker tag studentapp:${cleanAppVersion} ${fullImageUri}:${cleanAppVersion}
            docker push ${fullImageUri}:${cleanAppVersion}
        """
        
    } else if (config.action == 'deployHelm') {
        if (!cleanAppVersion) { error "appVersion parameter is missing for deployHelm action!" }
        echo "Starting Helm package and remote deploy sequence for version: ${cleanAppVersion}"
        
        // Log helm into ECR OCI registry
        sh "aws ecr get-login-password --region ${envs.REGION} | helm registry login --username AWS --password-stdin ${envs.ECR_URL}"
        
        // Package the chart using your local directory
        sh "helm package ecommerce/ --version 0.1.0 --app-version ${cleanAppVersion}"
        
        // Target the specific ECR chart repository path for OCI push
        sh "helm push ecommerce-0.1.0.tgz oci://${envs.ECR_URL}/${envs.CHART_REPO}"
        
        // Added single quotes around '${cleanAppVersion}' so Kubernetes parses the tag safely
        sh """
            helm upgrade --install ecommerce-release oci://${envs.ECR_URL}/${envs.CHART_REPO} \
              --version 0.1.0 \
              --set image.repository=${envs.ECR_URL}/${envs.IMAGE_REPO} \
              --set image.tag='${cleanAppVersion}' \
              --kubeconfig ${config.kubeconfigPath}
        """
    } else {
        error "Invalid action '${config.action}' passed to orchestrateDeployment step. Please pass action: 'pushImage' or action: 'deployHelm'."
    }
}
