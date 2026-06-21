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
    val lwjglVersion = "3.2.3"
    val lwjglNatives = "natives-windows"
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
