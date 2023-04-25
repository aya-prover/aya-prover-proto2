// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.remark.code.CodeAttrProcessor;
import org.aya.concrete.remark.code.CodeOptions;
import org.aya.generic.util.InternalException;
import org.aya.literate.Literate;
import org.aya.literate.LiterateConsumer;
import org.aya.literate.frontmatter.YamlFrontMatter;
import org.aya.literate.math.InlineMath;
import org.aya.literate.math.MathBlock;
import org.aya.literate.parser.BaseMdParser;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.Reporter;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static org.aya.concrete.remark.AyaLiterate.AyaCodeBlock;

public class AyaMdParser extends BaseMdParser {
  public AyaMdParser(@NotNull SourceFile file, @NotNull Reporter reporter) {
    super(file, reporter);
  }

  @Override
  protected @NotNull Parser.Builder parserBuilder() {
    return super.parserBuilder()
      .customDelimiterProcessor(CodeAttrProcessor.INSTANCE)
      .customDelimiterProcessor(InlineMath.Processor.INSTANCE)
      .customBlockParserFactory(MathBlock.FACTORY)
      .customBlockParserFactory(YamlFrontMatter.FACTORY);
  }

  /**
   * Extract all aya code blocks, keep source poses.
   * Replacing non-code content with whitespaces.
   * <p>
   * Another strategy: create a lexer that can tokenize some pieces of source code
   */
  public @NotNull String extractAya(@NotNull Literate literate) {
    return etching(new LiterateConsumer.InstanceExtractinator<>(AyaCodeBlock.class)
      .extract(literate).view()
      .map(Function.identity())
    );
  }

  protected @NotNull Literate mapNode(@NotNull Node node) {
    return switch (node) {
      case InlineMath math -> new Literate.Math(true, mapChildren(math));
      case MathBlock math -> {
        var formula = stripTrailingNewline(math.literal, math).component2();
        yield new Literate.Math(false, ImmutableSeq.of(new Literate.Raw(Doc.plain(formula))));
      }
      case YamlFrontMatter yaml -> {
        var mark = Doc.plain(String.valueOf(yaml.fenceChar).repeat(yaml.fenceLength));
        var matter = Doc.vcat(mark, Doc.escaped(yaml.literal), mark, Doc.line());
        var doc = yaml.fenceIndent > 0 ? Doc.hang(yaml.fenceIndent, matter) : matter;
        yield new Literate.Raw(doc);
      }
      case FencedCodeBlock codeBlock when AyaCodeBlock.isAya(codeBlock.getInfo()) -> {
        var code = stripTrailingNewline(codeBlock.getLiteral(), codeBlock);
        yield new AyaCodeBlock(code.component1().get(), code.component2(),
          codeBlock.getInfo().equalsIgnoreCase(AyaCodeBlock.LANGUAGE_AYA_HIDDEN));
      }
      case Code inlineCode -> {
        var spans = inlineCode.getSourceSpans();
        if (spans != null && spans.size() == 1) {
          var sourceSpan = spans.get(0);
          var lineIndex = linesIndex.get(sourceSpan.getLineIndex());
          var startFrom = lineIndex + sourceSpan.getColumnIndex();
          var sourcePos = fromSourceSpans(file, startFrom, Seq.of(sourceSpan));
          assert sourcePos != null;
          // FIXME[hoshino]: The sourcePos here contains the beginning and trailing '`'
          yield CodeOptions.analyze(inlineCode, sourcePos);
        }
        throw new InternalException("SourceSpans");
      }
      default -> super.mapNode(node);
    };
  }
}
