// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.meta;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple2;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.PiTerm;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.generic.Constants;
import org.aya.ref.AnyVar;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, re-xyr
 * @apiNote The object identity of this class is used at runtime
 * @implNote Do not override equals or hashCode
 */
public final class Meta implements AnyVar {
  public final @NotNull ImmutableSeq<Term.Param> contextTele;
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull String name;
  public final @NotNull MetaInfo info;
  public final @NotNull SourcePos sourcePos;
  public final @NotNull MutableList<Tuple2<Subst, Term>> conditions = MutableList.create();

  public SeqView<Term.Param> fullTelescope() {
    return contextTele.view().concat(telescope);
  }

  private Meta(
    @NotNull ImmutableSeq<Term.Param> contextTele,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull String name, @NotNull MetaInfo info,
    @NotNull SourcePos sourcePos
  ) {
    this.contextTele = contextTele;
    this.telescope = telescope;
    this.name = name;
    this.info = info;
    this.sourcePos = sourcePos;
  }

  public static @NotNull Meta from(
    @NotNull ImmutableSeq<Term.Param> contextTele, @NotNull String name,
    @NotNull SourcePos sourcePos
  ) {
    return new Meta(contextTele, ImmutableSeq.empty(), name, MetaInfo.AnyType.INSTANCE, sourcePos);
  }

  public static @NotNull Meta from(
    @NotNull ImmutableSeq<Term.Param> contextTele, @NotNull String name,
    @NotNull Term result, @NotNull SourcePos sourcePos
  ) {
    if (result instanceof PiTerm pi) {
      var buf = MutableList.<Term.Param>create();
      var r = new MetaInfo.Result(pi.parameters(buf));
      return new Meta(contextTele, buf.toImmutableSeq(), name, r, sourcePos);
    } else return new Meta(contextTele, ImmutableSeq.empty(), name, new MetaInfo.Result(result), sourcePos);
  }

  public @NotNull PiTerm asPi(
    @NotNull String domName, @NotNull String codName, boolean explicit,
    @NotNull ImmutableSeq<Arg<Term>> contextArgs
  ) {
    assert telescope.isEmpty();
    var knownDomInfo = info;
    // TODO[isType]: deal with piCod
    var knownCodInfo = info;
    if (knownDomInfo instanceof MetaInfo.Result(var result)) {
      if (!(result instanceof SortTerm sort)) throw new AssertionError("This should be unreachable");
      knownDomInfo = new MetaInfo.PiDom(sort);
    }
    var domVar = new Meta(contextTele, ImmutableSeq.empty(), domName, knownDomInfo, sourcePos);
    var codVar = new Meta(contextTele, ImmutableSeq.empty(), codName, knownCodInfo, sourcePos);
    var dom = new MetaTerm(domVar, contextArgs, ImmutableSeq.empty());
    var cod = new MetaTerm(codVar, contextArgs, ImmutableSeq.empty());
    var domParam = new Term.Param(Constants.randomlyNamed(sourcePos), dom, explicit);
    return new PiTerm(domParam, cod);
  }

  public @NotNull Meta clone(@NotNull MetaInfo newInfo) {
    var typed = new Meta(contextTele, telescope, name, newInfo, sourcePos);
    typed.conditions.appendAll(conditions);
    return typed;
  }

  public @NotNull MetaTerm asPiDom(@NotNull SortTerm sort, @NotNull ImmutableSeq<Arg<Term>> contextArgs) {
    assert telescope.isEmpty();
    assert info instanceof MetaInfo.AnyType;
    var typed = new Meta(contextTele, ImmutableSeq.empty(), name, new MetaInfo.PiDom(sort), sourcePos);
    return new MetaTerm(typed, contextArgs, ImmutableSeq.empty());
  }

  @Override public @NotNull String name() {
    return name;
  }
}
