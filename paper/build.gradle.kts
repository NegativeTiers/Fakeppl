plugins { java }

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveBaseName.set("MineTopFakePlayers-Paper")
}
