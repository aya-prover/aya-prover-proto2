// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import org.aya.cli.repl.render.Color;
import org.aya.cli.repl.render.RenderOptions;
import org.aya.generic.util.AyaHome;
import org.aya.generic.util.NormalizeMode;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.reporter.IgnoringReporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplConfig implements AutoCloseable {
  public transient final Path configFile;
  public @NotNull String prompt = "> ";
  public @NotNull NormalizeMode normalizeMode = NormalizeMode.NF;
  public @NotNull DistillerOptions distillerOptions = DistillerOptions.pretty();
  public @NotNull RenderOptions renderOptions = RenderOptions.CLI_DEFAULT;
  public boolean enableUnicode = true;
  /** Disables welcome message, echoing info, etc. */
  public boolean silent = false;

  public ReplConfig(@NotNull Path file) {
    this.configFile = file;
  }

  private void checkInitialization() {
    if (distillerOptions.map.isEmpty()) distillerOptions.reset();
  }

  public static @NotNull ReplConfig loadFromDefault() throws IOException {
    return ReplConfig.loadFrom(AyaHome.ayaHome().resolve("repl_config.json"));
  }

  public static @NotNull ReplConfig loadFrom(@NotNull Path file) throws IOException {
    if (Files.notExists(file)) return new ReplConfig(file);
    var config = newGsonBuilder()
      .registerTypeAdapter(ReplConfig.class, (InstanceCreator<ReplConfig>) type -> new ReplConfig(file))
      .create()
      .fromJson(Files.newBufferedReader(file), ReplConfig.class);
    if (config == null) return new ReplConfig(file);
    config.checkInitialization();
    return config;
  }

  @Override public void close() throws IOException {
    var json = newGsonBuilder()
      .create()
      .toJson(this);
    Files.writeString(configFile, json);
  }

  private static GsonBuilder newGsonBuilder() {
    return new GsonBuilder()
      .registerTypeAdapter(Color.class, new Color.Adapter())
      .registerTypeAdapter(RenderOptions.class,
        new RenderOptions.Adapter(IgnoringReporter.INSTANCE, RenderOptions.CLI_DEFAULT));
  }
}
