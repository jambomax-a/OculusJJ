plugins {
    java
    application
}

group = "com.oculus"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// HOROS-20240407??: weasis-core-img:4.12.2????????????opencv??????????
// sourceSets?????????opencv.disabled?opencv???????

repositories {
    // ????Maven????????????????????
    mavenLocal()
    mavenCentral()
    // dcm4che???????
    maven {
        url = uri("https://www.dcm4che.org/maven2/")
    }
    // Weasis????????GitHub - master?????
    maven {
        url = uri("https://raw.githubusercontent.com/nroduit/mvn-repo/master/")
        metadataSources {
            artifact()
        }
    }
    // Weasis????????releases???? - ????????
    maven {
        url = uri("https://raw.githubusercontent.com/nroduit/mvn-repo/master/releases")
        metadataSources {
            artifact()
        }
    }
    // JitPack??????????
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // DICOM processing
    implementation("org.dcm4che:dcm4che-core:5.34.1")
    implementation("org.dcm4che:dcm4che-image:5.34.1")
    implementation("org.dcm4che:dcm4che-imageio:5.34.1")
    // HOROS-20240407??: RLE Lossless??????????????
    implementation("org.dcm4che:dcm4che-imageio-rle:5.34.1")
    // JPEG??DICOM?????????????weasis-core-img????
    implementation("org.weasis.core:weasis-core-img:4.12.2")
    // dcm4che-net is required for network DICOM features (PACS query/retrieve, C-STORE SCP)
    implementation("org.dcm4che:dcm4che-net:5.34.1")
// JmDNS Bonjour service discovery
implementation("org.jmdns:jmdns:3.6.1")
    // SwingX (TreeTable support for HOROS-like outline view)
    implementation("org.swinglabs.swingx:swingx-all:1.6.5-1")
    // Database
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    // JSON/YAML processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    // GPU Acceleration (OpenGL via LWJGL 3)
    // natives classifierはビルドホストのOSで自動選択（-PlwjglNatives=natives-linux-arm64等の上書き可）
    val lwjglVersion = "3.2.3"
    fun osName(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("windows") -> "windows"
            os.contains("mac") -> "macos"
            os.contains("linux") -> "linux"
            else -> "linux"
        }
    }
    fun archName(): String {
        val arch = System.getProperty("os.arch")
        return when (arch) {
            "aarch64" -> "arm64"
            "arm" -> "arm32"
            else -> ""
        }
    }
    val os = osName()
    val arch = archName()
    val lwjglDefault = if (arch.isEmpty()) "natives-$os" else "natives-$os-$arch"
    val lwjglNatives = providers.gradleProperty("lwjglNatives").orElse(lwjglDefault).get()
    implementation("org.lwjgl", "lwjgl", lwjglVersion)
    implementation("org.lwjgl", "lwjgl-opengl", lwjglVersion)
    implementation("org.lwjgl", "lwjgl-glfw", lwjglVersion)
    implementation("org.lwjgl", "lwjgl-jawt", lwjglVersion)
    runtimeOnly("org.lwjgl", "lwjgl", lwjglVersion, classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-opengl", lwjglVersion, classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", lwjglVersion, classifier = lwjglNatives)
    implementation("org.lwjglx:lwjgl3-awt:0.1.8")
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

application {
    mainClass.set("com.oculus.jj.OculusJJApp")
    applicationDefaultJvmArgs = listOf(
        "-Xmx24g", // 3D MPR???????????????????????24GB?????
        "-Dsun.java2d.d3d=false",
        // NOTE: -Dsun.java2d.opengl=true ????Java2D??GL???????LWJGL GL?????????????
        // GDI??????(d3d=false)?OpenGL???????????
        // Java 9+?????????: NativeImageReader?FileImageInputStream.raf????????????
        "--add-opens=java.desktop/javax.imageio.stream=ALL-UNNAMED",
        // Java 9+?????????: NativeImageReader?RandomAccessFile.path????????????
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        // ??????????????Java 9+?
        "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

// ============================================================
// Linux 自己完結配布物（JRE バンドル、Java インストール不要）
// ============================================================
tasks.register<Copy>("distLinuxCopyJARs") {
    dependsOn("jar")
    from(configurations.runtimeClasspath)
    destinationDir = file("build/linux-dist/OculusJJ/lib")
}

tasks.register<Exec>("distLinux") {
    dependsOn("distLinuxCopyJARs")
    executable = "./scripts/build-linux-dist.sh"
    args = listOf("build/linux-dist")
}

tasks.register<Tar>("distLinuxTar") {
    dependsOn("distLinux")
    archiveBaseName = "OculusJJ-1.0.0-linux-x64"
    archiveVersion = ""
    compression = Compression.GZIP
    from("build/linux-dist")
    destinationDirectory.set(file("build/distributions"))
    // jlink 済みなので不要な除外は最小限に
    exclude("**/docs")
    exclude("**/man")
    exclude("**/include")
}
