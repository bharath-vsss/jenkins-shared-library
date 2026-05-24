def call(Map config = [:]) {
    def awsAccountId = "510931056289" 
    def region       = "ap-southeast-2"
    def repoName     = "devops_repo/app"
    def ecrUrl       = "${awsAccountId}.dkr.ecr.${region}.amazonaws.com"
    def fullImageUri = "${ecrUrl}/${repoName}"
    def appVersion   = config.appVersion

    // 1. Authenticate both Docker and Helm to your ECR OCI Registry
    sh "aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${ecrUrl}"
    sh "aws ecr get-login-password --region ${region} | helm registry login --username AWS --password-stdin ${ecrUrl}"

    // 2. Tag and Push the Docker Image
    sh """
       docker tag studentapp:${appVersion} ${fullImageUri}:${appVersion}
       docker push ${fullImageUri}:${appVersion}
    """

    // 3. Package and Push Helm Chart as an OCI artifact to ECR
    // Modifies the values.yaml to explicitly use the new image URI and version tag dynamically
    sh """
        sed -i 's|repository: .*|repository: ${fullImageUri}|g' helm-chart/values.yaml
        sed -i 's|tag: .*|tag: "${appVersion}"|g' helm-chart/values.yaml
        helm package helm-chart --version ${appVersion} --app-version ${appVersion}
        helm push student-app-${appVersion}.tgz oci://${ecrUrl}/devops_repo
    """

    // 4. Securely pull chart and Deploy to the Self-Managed Cluster
    // Uses the KUBECONFIG file injected safely from your Jenkins pipeline scope
    sh """
        mkdir -p extracted-chart
        helm pull oci://${ecrUrl}/devops_repo/student-app --version ${appVersion} --untar --untardir ./extracted-chart
        
        helm upgrade --install ecommerce-student-app ./extracted-chart/student-app \
            --kubeconfig ${config.kubeconfigFile} \
            --namespace default
            
        kubectl rollout status deployment/ecommerce-student-app --kubeconfig ${config.kubeconfigFile} --namespace default
    """
}
