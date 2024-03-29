// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import org.aya.core.pat.Pat;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.core.visitor.Subst;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Partial.Const;
import org.aya.guest0x0.cubical.Partial.Split;
import org.aya.guest0x0.cubical.Restr;
import org.aya.tyck.tycker.TyckState;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * Partial elements.
 *
 * @implNote I am unsure if this is stable as of our assumptions. Surely
 * a split partial may become a const partial, is that stable?
 */
public record PartialTerm(@NotNull Partial<Term> partial, @NotNull Term rhsType) implements Term {
  public @NotNull PartialTerm update(@NotNull Partial<Term> partial, @NotNull Term rhsType) {
    return partial == partial() && rhsType == rhsType() ? this : new PartialTerm(partial, rhsType);
  }

  @Override public @NotNull PartialTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(partial.map(f), f.apply(rhsType));
  }

  public static @NotNull Partial<Term> merge(@NotNull Seq<Partial<Term>> partials) {
    // Just a mild guess without scientific rationale
    var list = MutableArrayList.<Restr.Side<Term>>create(partials.size() * 2);
    for (var partial : partials) {
      switch (partial) {
        case Split<Term> s -> list.appendAll(s.clauses());
        case Const<Term> c -> {
          return c;
        }
      }
    }
    return AyaRestrSimplifier.INSTANCE.mapPartial(
      new Split<>(list.toImmutableArray()),
      UnaryOperator.identity());
  }

  public static final @NotNull Partial.Split<Term> DUMMY_SPLIT = new Split<>(ImmutableSeq.empty());

  public static Partial<Term> amendTerms(Partial<Term> p, UnaryOperator<Term> applyDimsTo) {
    return switch (p) {
      case Partial.Split<Term> s -> new Split<>(s.clauses().map(c ->
        new Restr.Side<>(c.cof(), applyDimsTo.apply(c.u()))));
      case Partial.Const(var c) -> new Const<>(applyDimsTo.apply(c));
    };
  }

  public static @NotNull PartialTerm from(Term phi, Term u, @NotNull Term rhsType) {
    // This `embed` is equivalent to `isOne` without normalization.
    // It's obvious that `phi` is a var term that cannot be normalized for now.
    //noinspection UnstableApiUsage
    var restr = AyaRestrSimplifier.INSTANCE.embed(phi);
    var inner = AyaRestrSimplifier.INSTANCE.mapPartial(new Split<>(restr.orz().map(
      or -> new Restr.Side<>(or, u))), UnaryOperator.identity());
    return new PartialTerm(inner, rhsType);
  }

  public static boolean impliesCof(Restr<Term> needed, Restr<Term> defined, TyckState state) {
    var restr = AyaRestrSimplifier.INSTANCE.normalizeRestr(needed.map(term -> term.freezeHoles(state)));
    return CofThy.conv(restr, new Subst(), subst -> CofThy.satisfied(subst.restr(state, defined)));
  }
}
