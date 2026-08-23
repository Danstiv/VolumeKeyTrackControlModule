// Keeps generated build output out of the bind-mounted repository: every
// project's build directory is redirected into the /out docker volume.
val outRoot = File(System.getenv("VKTC_BUILD_OUT") ?: "/out/build")

gradle.beforeProject {
    val name = path.trim(':').replace(':', '_').ifEmpty { "root" }
    layout.buildDirectory.set(File(outRoot, name))
}
