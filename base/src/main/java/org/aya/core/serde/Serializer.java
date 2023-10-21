// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import kala.tuple.Tuple;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.generic.Shaped;
import org.aya.util.error.InternalException;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public record Serializer(@NotNull Serializer.State state) {
  public @NotNull SerDef serialize(@NotNull GenericDef def, @Nullable SerDef.SerShapeResult shapeResult) {
    return switch (def) {
      case FnDef fn -> new SerDef.Fn(
        state.def(fn.ref),
        serializeParams(fn.telescope),
        fn.body.map(this::serialize, matchings -> matchings.map(this::serialize)),
        fn.modifiers,
        serialize(fn.result),
        shapeResult
      );
      case MemberDef field -> new SerDef.Field(
        state.def(field.classRef),
        state.def(field.ref),
        serializeParams(field.telescope),
        serialize(field.result),
        field.coerce
      );
      case ClassDef clazz -> new SerDef.Clazz(
        state.def(clazz.ref()),
        clazz.members.map(field -> (SerDef.Field) serialize(field, null))
      );
      case DataDef data -> new SerDef.Data(
        state.def(data.ref),
        serializeParams(data.telescope),
        serialize(data.result),
        data.body.map(ctor -> (SerDef.Ctor) serialize(ctor, null))
      );
      case PrimDef prim -> {
        assert prim.ref.module != null;
        assert prim.ref.fileModule != null;
        yield new SerDef.Prim(prim.ref.module, prim.ref.fileModule, prim.id);
      }
      case CtorDef ctor -> new SerDef.Ctor(
        state.def(ctor.dataRef),
        state.def(ctor.ref),
        serializePats(ctor.pats),
        serializeParams(ctor.ownerTele),
        serializeParams(ctor.selfTele),
        ctor.clauses.fmap(this::serialize),
        serialize(ctor.result),
        ctor.coerce
      );
    };
  }

  private @NotNull SerTerm.Sort serialize(@NotNull SortTerm term) {
    return new SerTerm.Sort(term.kind(), term.lift());
  }

  private @NotNull SerTerm serialize(@NotNull Term term) {
    return switch (term) {
      case IntegerTerm lit -> new SerTerm.ShapedInt(lit.repr(),
        SerDef.SerShapeResult.serialize(state, lit.recognition()),
        (SerTerm.Data) serialize(lit.type()));
      case ListTerm lit -> new SerTerm.ShapedList(
        lit.repr().map(this::serialize),
        SerDef.SerShapeResult.serialize(state, lit.recognition()),
        (SerTerm.Data) serialize(lit.type()));
      case FormulaTerm end -> new SerTerm.Mula(end.asFormula().fmap(this::serialize));
      case StringTerm str -> new SerTerm.Str(str.string());
      case RefTerm ref -> new SerTerm.Ref(state.local(ref.var()));
      case RefTerm.Field ref -> new SerTerm.FieldRef(state.def(ref.ref()));
      case IntervalTerm interval -> SerTerm.Interval.INSTANCE;
      case PiTerm pi -> new SerTerm.Pi(serialize(pi.param()), serialize(pi.body()));
      case SigmaTerm sigma -> new SerTerm.Sigma(serializeParams(sigma.params()));
      case ConCall conCall -> new SerTerm.Con(
        state.def(conCall.head().dataRef()), state.def(conCall.head().ref()),
        serializeCall(conCall.head().ulift(), conCall.head().dataArgs()),
        serializeArgs(conCall.conArgs()));
      case ClassCall classCall -> serializeClassCall(classCall);
      case DataCall dataCall -> serializeDataCall(dataCall);
      case PrimCall prim -> new SerTerm.Prim(
        state.def(prim.ref()),
        prim.id(),
        serializeCall(prim.ulift(), prim.args()));
      case FieldTerm access -> new SerTerm.Access(
        serialize(access.of()), state.def(access.ref()),
        serializeArgs(access.args()));
      case FnCall fnCall -> new SerTerm.Fn(
        state.def(fnCall.ref()),
        serializeCall(fnCall.ulift(), fnCall.args()));
      case ReduceRule.Fn(var head, var ulift, var args) -> new SerTerm.FnReduceRule(
        serializeShapedApplicable(head), serializeCall(ulift, args)
      );
      case ReduceRule.Con(var head, var ulift, var dataArgs, var conArgs) -> new SerTerm.ConReduceRule(
        serializeShapedApplicable(head), serializeCall(ulift, dataArgs), conArgs.map(this::serialize)
      );
      case ProjTerm proj -> new SerTerm.Proj(serialize(proj.of()), proj.ix());
      case AppTerm app -> new SerTerm.App(serialize(app.of()), serialize(app.arg()));
      case MatchTerm(var disc, var clauses) ->
        new SerTerm.Match(disc.map(this::serialize), clauses.map(this::serialize));
      case TupTerm tuple -> new SerTerm.Tup(serializeArgs(tuple.items()));
      case LamTerm(var param, var body) -> new SerTerm.Lam(serialize(param.ref()), param.explicit(), serialize(body));
      case NewTerm newTerm -> new SerTerm.New(serializeClassCall(newTerm.inner()));

      case PartialTerm el -> new SerTerm.PartEl(partial(el.partial()), serialize(el.rhsType()));
      case PartialTyTerm ty -> new SerTerm.PartTy(serialize(ty.type()), ty.restr().fmap(this::serialize));
      case PathTerm path -> serialize(path);
      case PLamTerm(var params, var body) -> new SerTerm.PathLam(serializeIntervals(params), serialize(body));
      case PAppTerm(var of, var args, var cube) -> new SerTerm.PathApp(serialize(of),
        serializeArgs(args), serialize(cube));
      case CoeTerm(var ty, var r, var s) -> new SerTerm.Coe(serialize(ty), serialize(r), serialize(s));
      case SortTerm sort -> serialize(sort);

      case MetaTerm hole -> throw new InternalException("Shall not have holes serialized.");
      case MetaPatTerm metaPat -> throw new InternalException("Shall not have metaPats serialized.");
      case ErrorTerm err -> throw new InternalException("Shall not have error term serialized.");
      case MetaLitTerm err -> throw new InternalException("Shall not have metaLiterals serialized.");
      case HCompTerm hComp -> throw new InternalException("TODO");
      case InTerm(var phi, var u) -> new SerTerm.InS(serialize(phi), serialize(u));
      case OutTerm(var phi, var par, var u) -> new SerTerm.OutS(serialize(phi), serialize(par), serialize(u));
    };
  }

  private @NotNull Partial<SerTerm> partial(Partial<Term> el) {
    return el.fmap(this::serialize);
  }

  private @NotNull SerPat serialize(@NotNull Pat pat, boolean explicit) {
    return switch (pat) {
      case Pat.Absurd absurd -> new SerPat.Absurd(explicit);
      case Pat.Ctor(var ref, var params, var typeRecog, var dataCall) -> {
        var shapeResult = typeRecog == null ? null : SerDef.SerShapeResult.serialize(state, typeRecog);
        yield new SerPat.Ctor(
          explicit,
          state.def(ref),
          serializePats(params),
          shapeResult,
          serializeDataCall(dataCall));
      }
      case Pat.Tuple tuple -> new SerPat.Tuple(explicit, serializePats(tuple.pats()));
      case Pat.Bind bind -> new SerPat.Bind(explicit, state.local(bind.bind()), serialize(bind.type()));
      case Pat.Meta meta -> throw new InternalException(meta + " is illegal here");
      case Pat.ShapedInt lit -> new SerPat.ShapedInt(
        lit.repr(), explicit,
        SerDef.SerShapeResult.serialize(state, lit.recognition()),
        serializeDataCall(lit.type()));
    };
  }

  private @NotNull SerTerm.Path serialize(@NotNull PathTerm cube) {
    return new SerTerm.Path(
      serializeIntervals(cube.params()),
      serialize(cube.type()),
      partial(cube.partial()));
  }

  private @NotNull SerTerm.Data serializeDataCall(@NotNull DataCall dataCall) {
    return new SerTerm.Data(
      state.def(dataCall.ref()),
      serializeCall(dataCall.ulift(), dataCall.args()));
  }

  private @NotNull SerTerm.Clazz serializeClassCall(@NotNull ClassCall classCall) {
    return new SerTerm.Clazz(
      state.def(classCall.ref()), classCall.ulift(),
      ImmutableMap.from(classCall.args().view().map((k, v) -> Tuple.of(state.def(k), serialize(v)))));
  }

  private @NotNull SerTerm.SerShapedApplicable serializeShapedApplicable(@NotNull Shaped.Applicable<Term, ?, ?> shapedApplicable) {
    return switch (shapedApplicable) {
      case IntegerOpsTerm.ConRule conRule -> new SerTerm.IntegerOps(state.def(conRule.ref()), Either.left(Tuple.of(
        SerDef.SerShapeResult.serialize(state, conRule.paramRecognition()), (SerTerm.Data) serialize(conRule.paramType())
      )));
      case IntegerOpsTerm.FnRule fnRule -> new SerTerm.IntegerOps(state.def(fnRule.ref()), Either.right(fnRule.kind()));
      default -> throw new IllegalStateException("Unexpected value: " + shapedApplicable);
    };
  }

  private @NotNull SerPat.Clause serialize(@NotNull Term.Matching matchy) {
    return new SerPat.Clause(serializePats(matchy.patterns()), serialize(matchy.body()));
  }

  private SerTerm.SerArg serialize(@NotNull Arg<@NotNull Term> termArg) {
    return new SerTerm.SerArg(serialize(termArg.term()), termArg.explicit());
  }

  public record State(@NotNull MutableMap<LocalVar, Integer> localCache) {
    public State() {
      this(MutableMap.create());
    }

    public @NotNull SerTerm.SimpVar local(@NotNull LocalVar var) {
      return new SerTerm.SimpVar(localCache.getOrPut(var, localCache::size), var.name());
    }

    public @NotNull SerDef.QName def(@NotNull DefVar<?, ?> var) {
      assert var.module != null;
      assert var.fileModule != null;
      return new SerDef.QName(var.module, var.fileModule, var.name());
    }
  }

  @Contract("_ -> new") private SerTerm.SerParam serialize(Term.@NotNull Param param) {
    return new SerTerm.SerParam(param.explicit(), serialize(param.ref()), serialize(param.type()));
  }

  private @NotNull SerTerm.SimpVar serialize(@NotNull LocalVar localVar) {
    return state.local(localVar);
  }

  private @NotNull ImmutableSeq<SerTerm.SerParam> serializeParams(ImmutableSeq<Term.@NotNull Param> params) {
    return params.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerTerm.SimpVar> serializeIntervals(ImmutableSeq<LocalVar> params) {
    return params.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerTerm.SerArg> serializeArgs(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerPat> serializePats(@NotNull ImmutableSeq<Arg<Pat>> pats) {
    return pats.map(x -> serialize(x.term(), x.explicit()));
  }

  private @NotNull SerTerm.CallData serializeCall(
    int ulift,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
    return new SerTerm.CallData(ulift, serializeArgs(args));
  }
}
