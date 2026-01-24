# Docker deployment script for careSync Backend (PowerShell)
# Make sure you're logged in to Docker Hub: docker login

# Set your Docker Hub username
$DOCKER_USERNAME = "vikrant48"
$IMAGE_NAME = "caresync-backend"
$TAG = "v1.0.1"

$FULL_IMAGE_NAME = "${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG}"

# Build the Docker image directly with the target tag
Write-Host "Building Docker image: $FULL_IMAGE_NAME..." -ForegroundColor Green
docker build -t $FULL_IMAGE_NAME .

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker build failed!" -ForegroundColor Red
    exit 1
}

# Push to Docker Hub
Write-Host "Pushing to Docker Hub..." -ForegroundColor Green
docker push $FULL_IMAGE_NAME

if ($LASTEXITCODE -eq 0) {
    Write-Host "Deployment complete!" -ForegroundColor Green
    Write-Host "Your image is available at: $FULL_IMAGE_NAME" -ForegroundColor Cyan
    
    # Cleanup dangling images (prevents <none>:<none> buildup)
    Write-Host "Cleaning up dangling images..." -ForegroundColor Yellow
    docker image prune -f
} else {
    Write-Host "Docker push failed!" -ForegroundColor Red
    exit 1
}

# Optional: Run the container locally for testing
# Write-Host "Starting container locally for testing..." -ForegroundColor Yellow
# docker run -d -p 8080:8080 --name caresync-backend-test "${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG}"

# Optional: Start with docker-compose for full stack testing
# Write-Host "Starting full stack with docker-compose..." -ForegroundColor Yellow
# docker-compose up -d