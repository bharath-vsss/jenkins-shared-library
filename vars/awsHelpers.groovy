// vars/orchestrateDeployment.groovy

def getConstants() {
    return [
        AWS_ACCOUNT_ID   : "510931056289",
        REGION           : "ap-southeast-2",
        IMAGE_REPO       : "devops_repo/app",
        CHART_REPO       : "ecommerce-app", 
        CHART_NAME       : "ecommerce",
        ECR_URL          : "510931056289.dkr.ecr.ap-southeast-2.amazonaws.com"
    ]
}

def call(Map config = [:]) {
    def envs = getConstants()
    def fullImageUri = "${envs.ECR_URL}/${envs.IMAGE_REPO}"
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
        
        sh "aws ecr get-login-password --region ${envs.REGION} | helm registry login --username AWS --password-stdin ${envs.ECR_URL}"
        
        sh "helm package ecommerce/ --version 0.1.0 --app-version ${cleanAppVersion}"
        
        def targetTgzFile = sh(script: "ls *.tgz", returnStdout: true).trim()
        echo "Found packaged chart archive: ${targetTgzFile}"
        
        // FIX: Explicitly points the target location to your named ECR repository 'ecommerce-app'
        sh "helm push ${targetTgzFile} oci://${envs.ECR_URL}"
        
        sh """
            helm upgrade --install ecommerce-release oci://${envs.ECR_URL}/${envs.CHART_REPO} \
              --version 0.1.0 \
              --set image.repository=${envs.ECR_URL}/${envs.IMAGE_REPO} \
              --set image.tag='${cleanAppVersion}' \
              --kubeconfig ${config.kubeconfigPath}
        """
    } else {
        error "Invalid action parameter."
    }
}
