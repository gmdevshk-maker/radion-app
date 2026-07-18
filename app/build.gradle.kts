import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifact
import java.io.File
import java.io.Serializable
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// keystore.properties가 없으면 release도 debug 키로 서명한다(키스토어 없이도 빌드가 되도록)
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.app.radion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.app.radion"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// APK 파일명을 RadiOn_<versionName>.apk 로 리네임한다.
// AGP 9는 레거시 applicationVariants.outputFileName API가 없으므로, 새 Variant API의
// Artifact Transform으로 APK 산출물 자체를 교체한다. 복사가 아니라 교체이므로
// output-metadata.json도 새 이름으로 갱신되어, 안드로이드 스튜디오의 빌드 알림·Run·설치와
// CLI(gradlew)가 모두 RadiOn_*.apk 하나만 바라본다(app-release.apk는 더 이상 생성되지 않음).
interface RenameApkWorkParameters : WorkParameters, Serializable {
    val inputApk: RegularFileProperty
    val outputApk: RegularFileProperty
}

abstract class RenameApkWorkAction @Inject constructor(
    private val params: RenameApkWorkParameters,
) : WorkAction<RenameApkWorkParameters> {
    override fun execute() {
        val out = params.outputApk.get().asFile
        out.delete()
        params.inputApk.get().asFile.copyTo(out, overwrite = true)
    }
}

abstract class RenameApkTask @Inject constructor(
    private val workers: WorkerExecutor,
) : DefaultTask() {
    @get:InputFiles
    abstract val apkFolder: DirectoryProperty

    @get:OutputDirectory
    abstract val outFolder: DirectoryProperty

    @get:Input
    abstract val apkName: Property<String>

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<RenameApkTask>>

    @TaskAction
    fun taskAction() {
        transformationRequest.get().submit(
            this,
            workers.noIsolation(),
            RenameApkWorkAction::class.java,
        ) { builtArtifact: BuiltArtifact, outputLocation: Directory, params: RenameApkWorkParameters ->
            val output = File(outputLocation.asFile, apkName.get())
            params.inputApk.set(File(builtArtifact.outputFile))
            params.outputApk.set(output)
            output
        }
    }
}

val appVersionName = android.defaultConfig.versionName
androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val renameTask = tasks.register<RenameApkTask>("renameApk$cap") {
            apkName.set("RadiOn_$appVersionName.apk")
        }
        val request = variant.artifacts.use(renameTask)
            .wiredWithDirectories(RenameApkTask::apkFolder, RenameApkTask::outFolder)
            .toTransformMany(SingleArtifact.APK)
        renameTask.configure { transformationRequest.set(request) }
    }
}

// jvmTarget은 내장 Kotlin이 위 compileOptions.targetCompatibility(17)에서 자동으로 가져간다

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
