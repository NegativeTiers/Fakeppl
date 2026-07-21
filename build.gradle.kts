plugins { java }

allprojects {
    group = "in.minetop"
    version = "2.0.0"
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.citizensnpcs.co/repo")
        maven("https://jitpack.io")
    }
}

subprojects {
    apply(plugin = "java")
    java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
