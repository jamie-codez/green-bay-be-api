FROM adoptopenjdk/openjdk14:ubi
ENV VERTICLE_HOME /app/greenbay
ENV VERTICLE_NAME greenbay-api-v1.0.0-all.jar
EXPOSE 8000
WORKDIR ${VERTICLE_HOME}
COPY build/libs/${VERTICLE_NAME} ${VERTICLE_HOME}

ENTRYPOINT ["sh","-c"]
CMD ["exec java -jar ${VERTICLE_NAME}"]