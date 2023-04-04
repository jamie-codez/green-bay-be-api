rm -rf build/*
sudo docker rmi greenbay_api
./gradlew build
docker compose up