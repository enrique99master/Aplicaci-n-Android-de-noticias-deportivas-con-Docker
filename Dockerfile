FROM ghcr.io/cirruslabs/android-sdk:36

WORKDIR /app

COPY . .

RUN chmod +x gradlew

RUN ./gradlew clean assembleDebug

CMD ["./gradlew", "assembleDebug"]