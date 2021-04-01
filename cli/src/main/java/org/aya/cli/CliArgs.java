// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import com.beust.jcommander.Parameter;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CliArgs {
  @Parameter(names = {"--version"}, description = "Display the current version")
  public boolean version = false;
  @Parameter(names = {"--help", "-h"}, description = "Show this message", help = true)
  public boolean help = false;
  @Parameter(names = {"--interrupted-trace"}, hidden = true)
  public boolean interruptedTrace = false;
  @Parameter(names = {"--pretty-stage"}, description = "Pretty print the code in a certain stage")
  public DistillStage prettyStage;
  @Parameter(names = {"--pretty-format"}, description = "Pretty print format")
  public DistillFormat prettyFormat = DistillFormat.HTML;
  @Parameter(names = {"--pretty-dir"}, description = "Output directory of pretty printing")
  public @Nullable String prettyDir;
  @Parameter(names = {"--trace"}, description = "Print type checking traces")
  public @Nullable TraceFormat traceFormat;
  @Parameter(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message")
  public boolean asciiOnly;
  @Parameter(names = {"--module-path"}, description = "Search for module under this path")
  public List<String> modulePaths;
  @Parameter(description = "<input-file>")
  public String inputFile;

  public ImmutableSeq<String> modulePaths() {
    return modulePaths == null ? ImmutableSeq.empty() : ImmutableSeq.from(modulePaths);
  }

  public enum DistillStage {
    Raw,
    Scoped,
    Typed
  }

  public enum DistillFormat {
    HTML,
    LaTeX
  }

  public enum TraceFormat {
    ImGui,
    Markdown
  }
}
