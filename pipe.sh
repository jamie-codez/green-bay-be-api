rm -rf build
docker rmi -f green-bay-be-api-greenbay_api
./gradlew build
docker compose build
docker compose up