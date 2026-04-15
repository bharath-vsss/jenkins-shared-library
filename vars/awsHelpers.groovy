def call(Map config = [:]) {
    // Centralized configuration - Move these to a 'consts' file or keep here
    def awsAccountId = "123456789012" // Replace with your actual ID
    def region       = "ap-southeast-2"
    def repoName     = "devops_repo/app"
    def ecrUrl       = "${awsAccountId}.dkr.ecr.${region}.amazonaws.com"
    def fullImageUri = "${ecrUrl}/${repoName}"

    // Login to ECR
    sh "aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${ecrUrl}"

    // Tag and Push
    sh """
        docker tag studentapp:${config.appVersion} ${fullImageUri}:${config.appVersion}
        docker tag studentapp:${config.appVersion} ${fullImageUri}:latest
        docker push ${fullImageUri}:${config.appVersion}
        docker push ${fullImageUri}:latest
    """
}
