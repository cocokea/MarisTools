plugins {
    java
}

group = "com.maris"
version = "1.0"

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.15.7")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    jar {
        archiveBaseName.set("MarisTools")
        archiveVersion.set("")
        manifest {
            attributes["Implementation-Version"] = project.version
        }
    }
}

