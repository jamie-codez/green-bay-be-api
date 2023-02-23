plugins {
    kotlin("jvm") version "1.8.0"
    application
    id("io.vertx.vertx-plugin") version "1.3.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "com.greenbay.core"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("io.vertx:vertx-core:4.3.7"))
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-mongo-client")
    implementation("io.vertx:vertx-mail-client")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")
    implementation("com.auth0:java-jwt:4.2.2")
    implementation("org.springframework.security:spring-security-crypto:5.5.1")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.2")
    testImplementation("io.vertx:vertx-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.2")
}


val mainVerticleName = "com.greenbay.core.GreenBayService"
val watchForChange = "src/**/"
val doChange = "${projectDir}/gradlew classes"

vertx{
    mainVerticle = mainVerticleName
}

application{
    mainClassName = "io.vertx.core.Launcher"
    mainClass.set(mainVerticleName)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}