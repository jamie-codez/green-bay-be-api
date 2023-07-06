rm -rf build
docker rmi -f green-bay-be-api
./gradlew build
docker compose build
docker compose up