plugins {
    java
    id("com.gradleup.shadow") version "8.3.8"
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
}

tasks.shadowJar {
    archiveBaseName.set("MineTopFakePlayers-Velocity")
    archiveClassifier.set("")
    relocate("org.spongepowered.configurate", "in.minetop.fakeplayers.libs.configurate")
    relocate("io.leangen.geantyref", "in.minetop.fakeplayers.libs.geantyref")
}

tasks.build { dependsOn(tasks.shadowJar) }
