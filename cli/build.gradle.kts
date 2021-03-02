// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation("com.beust", "jcommander", version = deps.getProperty("version.jcommander"))
  implementation("org.ice1000.jimgui","core", version = deps.getProperty("version.jimgui"))
  implementation(project(":base"))
  implementation(project(":parser"))
  implementation(project(":pretty"))
}

plugins {
  id("com.github.johnrengelman.shadow") version "6.1.0"
}

tasks.withType<Jar>().configureEach {
  manifest.attributes["Main-Class"] = "${project.group}.cli.Main"
}

task<Copy>("copyJarHere") {
  dependsOn("shadowJar")
  from(buildDir.resolve("libs").resolve("${project.name}-$version-all.jar"))
  into(rootProject.projectDir)
}