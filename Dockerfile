FROM amazoncorretto:21.0.4-alpine3.18
COPY build/libs/announcement-0.0.1-SNAPSHOT.jar announcement.jar
EXPOSE 8087
ENTRYPOINT ["java","-jar","/announcement.jar"]
