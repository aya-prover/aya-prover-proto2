// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableVector;
import kala.collection.mutable.Buffer;
import kala.tuple.Tuple2;
import kala.value.Ref;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.HoleVar;
import org.aya.api.util.Arg;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000, re-xyr
 */
public final class Meta {
  public final @NotNull ImmutableSeq<Term.Param> contextTele;
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull Term result;
  public final @NotNull SourcePos sourcePos;
  public @Nullable Term body;
  public final @NotNull Ref<ImmutableSeq<Tuple2<Substituter.TermSubst, Term>>> conditions;

  public SeqView<Term.Param> fullTelescope() {
    return contextTele.view().concat(telescope);
  }

  public boolean solve(@NotNull HoleVar<Meta> v, @NotNull Term t) {
    if (t.findUsages(v) > 0) return false;
    body = t;
    return true;
  }

  private Meta(
    @NotNull ImmutableSeq<Term.Param> contextTele,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result, @NotNull SourcePos sourcePos
  ) {
    this.contextTele = contextTele;
    this.telescope = telescope;
    this.result = result;
    this.sourcePos = sourcePos;
    this.conditions = new Ref<>(ImmutableVector.empty());
  }

  public static @NotNull Meta from(
    @NotNull ImmutableSeq<Term.Param> contextTele,
    @NotNull Term result, @NotNull SourcePos sourcePos
  ) {
    if (result instanceof FormTerm.Pi pi) {
      var buf = Buffer.<Term.Param>create();
      var r = pi.parameters(buf);
      return new Meta(contextTele, buf.toImmutableSeq(), r, sourcePos);
    } else return new Meta(contextTele, ImmutableSeq.empty(), result, sourcePos);
  }

  public @NotNull FormTerm.Pi asPi(
    @NotNull String domName, @NotNull String codName, boolean explicit,
    @NotNull ImmutableSeq<Arg<Term>> contextArgs
  ) {
    assert telescope.isEmpty();
    var domVar = new HoleVar<>(domName, Meta.from(contextTele, result, sourcePos));
    var codVar = new HoleVar<>(codName, Meta.from(contextTele, result, sourcePos));
    var dom = new CallTerm.Hole(domVar, contextArgs, ImmutableSeq.empty());
    var cod = new CallTerm.Hole(codVar, contextArgs, ImmutableSeq.empty());
    var domParam = new Term.Param(Constants.randomlyNamed(sourcePos), dom, explicit);
    return new FormTerm.Pi(domParam, cod);
  }
}
