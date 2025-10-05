# Docker deployment script for careSync Backend (PowerShell)
# Make sure you're logged in to Docker Hub: docker login

# Set your Docker Hub username
$DOCKER_USERNAME = "vikrant48"
$IMAGE_NAME = "caresync-backend"
$TAG = "latest"

# Build the Docker image
Write-Host "Building Docker image..." -ForegroundColor Green
docker build -t "${IMAGE_NAME}:${TAG}" .

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker build failed!" -ForegroundColor Red
    exit 1
}

# Tag the image for Docker Hub
Write-Host "Tagging image for Docker Hub..." -ForegroundColor Green
docker tag "${IMAGE_NAME}:${TAG}" "${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG}"

# Push to Docker Hub
Write-Host "Pushing to Docker Hub..." -ForegroundColor Green
docker push "${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG}"

if ($LASTEXITCODE -eq 0) {
    Write-Host "Deployment complete!" -ForegroundColor Green
    Write-Host "Your image is available at: ${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG}" -ForegroundColor Cyan
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