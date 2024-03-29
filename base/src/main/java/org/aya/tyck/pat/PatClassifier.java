// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.core.visitor.EndoTerm;
import org.aya.core.visitor.Subst;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.tyck.error.TyckOrderError;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.tycker.StatedTycker;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.Arg;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.pat.ClassifierUtil;
import org.aya.util.tyck.pat.Indexed;
import org.aya.util.tyck.pat.PatClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.stream.Collectors;

/**
 * @author ice1000
 */
public final class PatClassifier extends StatedTycker
  implements ClassifierUtil<Subst, Term, Term.Param, Pat, AnyVar> {
  public final @NotNull SourcePos pos;

  public PatClassifier(
    @NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder,
    @NotNull TyckState state, @NotNull SourcePos pos
  ) {
    super(reporter, traceBuilder, state);
    this.pos = pos;
  }

  public static @NotNull ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>> classify(
    @NotNull SeqLike<? extends Pat.@NotNull Preclause<?>> clauses,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull StatedTycker tycker,
    @NotNull SourcePos pos
  ) {
    return classify(clauses, telescope, tycker.state, tycker.reporter, pos, tycker.traceBuilder);
  }

  @VisibleForTesting public static @NotNull ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>>
  classify(
    @NotNull SeqLike<? extends Pat.@NotNull Preclause<?>> clauses,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull TyckState state,
    @NotNull Reporter reporter, @NotNull SourcePos pos, Trace.@Nullable Builder builder
  ) {
    var classifier = new PatClassifier(reporter, builder, state, pos);
    var cl = classifier.classifyN(new Subst(), telescope.view(), clauses.view()
      .mapIndexed((i, clause) -> new Indexed<>(clause.patterns().view().map(Arg::term), i))
      .toImmutableSeq(), 2);
    var p = cl.partition(c -> c.cls().isEmpty());
    var missing = p.component1();
    if (missing.isNotEmpty()) reporter.report(new ClausesProblem.MissingCase(pos, missing));
    return p.component2();
  }

  @Override public Term.Param subst(Subst subst, Term.Param param) {
    return param.subst(subst);
  }

  @Override public Pat normalize(Pat pat) {
    return pat.inline(null);
  }

  @Override public Subst add(Subst subst, AnyVar anyVar, Term term) {
    return subst.add(anyVar, term);
  }

  @Override public AnyVar ref(Term.Param param) {
    return param.ref();
  }

  /**
   * @return Possibilities
   */
  @Override public @NotNull ImmutableSeq<PatClass<Arg<Term>>> classify1(
    @NotNull Subst subst, @NotNull Term.Param param,
    @NotNull ImmutableSeq<Indexed<Pat>> clauses, int fuel
  ) {
    var whnfTy = whnf(param.type());
    final var explicit = param.explicit();
    switch (whnfTy) {
      // Note that we cannot have ill-typed patterns such as constructor patterns,
      // since patterns here are already well-typed
      case SigmaTerm(var params) -> {
        // The type is sigma type, and do we have any non-catchall patterns?
        // In case we do,
        if (clauses.anyMatch(i -> i.pat() instanceof Pat.Tuple)) {
          var params1 = new EndoTerm.Renamer().params(params.view());
          var matches = clauses.mapIndexedNotNull((i, subPat) ->
            switch (subPat.pat()) {
              case Pat.Tuple tuple -> new Indexed<>(tuple.pats().view().map(Arg::term), i);
              case Pat.Bind bind -> new Indexed<>(params1.view().map(p -> p.toPat().term()), i);
              default -> null;
            });
          var classes = classifyN(subst.derive(), params1.view(), matches, fuel);
          return classes.map(args -> new PatClass<>(
            new Arg<>(new TupTerm(args.term()), explicit),
            args.cls()));
        }
      }
      // THE BIG GAME
      case DataCall dataCall -> {
        // In case clauses are empty, we're just making sure that the type is uninhabited,
        // so proceed as if we have valid patterns
        if (clauses.isNotEmpty() &&
          // there are no clauses starting with a constructor pattern -- we don't need a split!
          clauses.noneMatch(subPat -> subPat.pat() instanceof Pat.Ctor || subPat.pat() instanceof Pat.ShapedInt)
        ) break;
        var data = dataCall.ref();
        var body = Def.dataBody(data);
        if (data.core == null) reporter.report(new TyckOrderError.NotYetTyckedError(pos, data));

        // Special optimization for literals
        var lits = clauses.mapNotNull(cl -> cl.pat() instanceof Pat.ShapedInt i ?
          new Indexed<>(i, cl.ix()) : null);
        var binds = Indexed.indices(clauses.filter(cl -> cl.pat() instanceof Pat.Bind));
        if (clauses.isNotEmpty() && lits.size() + binds.size() == clauses.size()) {
          // There is only literals and bind patterns, no constructor patterns
          var classes = Seq.from(lits.collect(
              Collectors.groupingBy(i -> i.pat().repr())).values())
            .map(i -> new PatClass<>(new Arg<>(i.get(0).pat().toTerm(), explicit),
              Indexed.indices(Seq.wrapJava(i)).concat(binds)));
          var ml = MutableArrayList.<PatClass<Arg<Term>>>create(classes.size() + 1);
          ml.appendAll(classes);
          ml.append(new PatClass<>(new Arg<>(new RefTerm(param.ref()), explicit), binds));
          return ml.toImmutableSeq();
        }

        var buffer = MutableList.<PatClass<Arg<Term>>>create();
        // For all constructors,
        for (var ctor : body) {
          var fuel1 = fuel;
          var conTeleView = conTele(clauses, dataCall, ctor);
          if (conTeleView == null) continue;
          var conTele = new EndoTerm.Renamer().params(conTeleView);
          // Find all patterns that are either catchall or splitting on this constructor,
          // e.g. for `suc`, `suc (suc a)` will be picked
          var matches = clauses.mapIndexedNotNull((ix, subPat) ->
            // Convert to constructor form
            matches(conTele, ctor, ix, subPat));
          var conHead = dataCall.conHead(ctor.ref);
          // The only matching cases are catch-all cases, and we skip these
          if (matches.isEmpty()) {
            fuel1--;
            // In this case we give up and do not split on this constructor
            if (conTele.isEmpty() || fuel1 <= 0) {
              var err = new ErrorTerm(Doc.plain("..."), false);
              buffer.append(new PatClass<>(new Arg<>(new ConCall(conHead,
                conTele.isEmpty() ? ImmutableSeq.empty() : ImmutableSeq.of(new Arg<>(err, true))),
                explicit), ImmutableIntSeq.empty()));
              continue;
            }
          }
          var classes = classifyN(subst.derive(), conTele.view(), matches, fuel1);
          buffer.appendAll(classes.map(args -> new PatClass<>(
            new Arg<>(new ConCall(conHead, args.term()), explicit),
            args.cls())));
        }
        return buffer.toImmutableSeq();
      }
      default -> {
      }
    }
    return ImmutableSeq.of(new PatClass<>(param.toArg(), Indexed.indices(clauses)));
  }

  private static @Nullable Indexed<SeqView<Pat>> matches(
    ImmutableSeq<Term.Param> conTele, CtorDef ctor, int ix, Indexed<Pat> subPat
  ) {
    return switch (subPat.pat() instanceof Pat.ShapedInt i
      ? i.constructorForm()
      : subPat.pat()) {
      case Pat.Ctor c when c.ref() == ctor.ref() -> new Indexed<>(c.params().view().map(Arg::term), ix);
      case Pat.Bind b -> new Indexed<>(conTele.view().map(p -> p.toPat().term()), ix);
      default -> null;
    };
  }

  public static int[] firstMatchDomination(
    @NotNull ImmutableSeq<? extends SourceNode> clauses,
    @NotNull Reporter reporter, @NotNull ImmutableSeq<? extends PatClass<?>> classes
  ) {
    return ClassifierUtil.firstMatchDomination(clauses, (pos, i) -> reporter.report(
      new ClausesProblem.FMDomination(i, pos)), classes);
  }

  private @Nullable SeqView<Term.Param>
  conTele(@NotNull ImmutableSeq<? extends Indexed<?>> clauses, DataCall dataCall, CtorDef ctor) {
    var conTele = ctor.selfTele.view();
    // Check if this constructor is available by doing the obvious thing
    var matchy = PatternTycker.mischa(dataCall, ctor, state);
    // If not, check the reason why: it may fail negatively or positively
    if (matchy.isErr()) {
      // Index unification fails negatively
      if (matchy.getErr()) {
        // If clauses is empty, we continue splitting to see
        // if we can ensure that the other cases are impossible, it would be fine.
        if (clauses.isNotEmpty() &&
          // If clauses has catch-all pattern(s), it would also be fine.
          clauses.noneMatch(seq -> seq.pat() instanceof Pat.Bind)
        ) {
          reporter.report(new ClausesProblem.UnsureCase(pos, ctor, dataCall));
          return null;
        }
      } else return null;
      // ^ If fails positively, this would be an impossible case
    } else conTele = conTele.map(param -> param.subst(matchy.get()));
    // Java wants a final local variable, let's alias it
    return conTele;
  }
}
