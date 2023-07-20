rm -rf build
docker rmi -f green-bay-be-api-green_bay_api:latest
./gradlew build
docker compose build --remove-orphans
docker compose up