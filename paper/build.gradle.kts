plugins { java }

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("net.citizensnpcs:citizensapi:2.0.39-SNAPSHOT")
}

tasks.jar { archiveBaseName.set("MineTopFakePlayers-Paper") }
