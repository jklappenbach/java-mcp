allprojects {
    group = "io.javamcp"
    version = "0.5.0"
}

// Used by the release workflow to assert the git tag matches the built version.
tasks.register("printVersion") {
    doLast { println(version) }
}
