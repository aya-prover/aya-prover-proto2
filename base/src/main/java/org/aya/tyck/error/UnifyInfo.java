// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.mutable.MutableList;
import org.aya.core.term.Term;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.tycker.TyckState;
import org.aya.tyck.unify.TermComparator;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record UnifyInfo(@NotNull TyckState state) {
  private @NotNull Term nf(Term failureTermL) {
    return failureTermL.normalize(state, NormalizeMode.NF);
  }

  private static void compareExprs(@NotNull Doc mid, Doc actualDoc, Doc expectedDoc, Doc actualNFDoc, Doc expectedNFDoc, MutableList<@NotNull Doc> buf) {
    exprInfo(actualDoc, actualNFDoc, buf);
    buf.append(mid);
    exprInfo(expectedDoc, expectedNFDoc, buf);
  }

  public static void exprInfo(Doc actualDoc, Doc actualNFDoc, MutableList<@NotNull Doc> buf) {
    buf.append(Doc.par(1, actualDoc));
    if (!actualNFDoc.equals(actualDoc))
      buf.append(Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actualNFDoc))));
  }

  public static void exprInfo(Term term, PrettierOptions options, TyckState state, MutableList<@NotNull Doc> buf) {
    exprInfo(term.toDoc(options), term.normalize(state, NormalizeMode.NF).toDoc(options), buf);
  }

  public record Comparison(
    @NotNull Term actual,
    @NotNull Term expected,
    @NotNull TermComparator.FailureData failureData
  ) {}

  public @NotNull Doc describeUnify(
    @NotNull PrettierOptions options,
    @NotNull Comparison comparison,
    @NotNull Doc prologue,
    @NotNull Doc epilogue
  ) {
    var actualDoc = comparison.actual.toDoc(options);
    var expectedDoc = comparison.expected.toDoc(options);
    var actualNFDoc = nf(comparison.actual).toDoc(options);
    var expectedNFDoc = nf(comparison.expected).toDoc(options);
    var buf = MutableList.of(prologue);
    compareExprs(epilogue, actualDoc, expectedDoc, actualNFDoc, expectedNFDoc, buf);
    var failureTermL = comparison.failureData.lhs();
    var failureTermR = comparison.failureData.rhs();
    var failureLhs = failureTermL.toDoc(options);
    if (!Objects.equals(failureLhs, actualDoc)
      && !Objects.equals(failureLhs, expectedDoc)
      && !Objects.equals(failureLhs, actualNFDoc)
      && !Objects.equals(failureLhs, expectedNFDoc)
    ) {
      buf.append(Doc.english("In particular, we failed to unify"));
      compareExprs(Doc.plain("with"),
        failureLhs, failureTermR.toDoc(options),
        nf(failureTermL).toDoc(options),
        nf(failureTermR).toDoc(options),
        buf);
    }
    return Doc.vcat(buf);
  }
}
