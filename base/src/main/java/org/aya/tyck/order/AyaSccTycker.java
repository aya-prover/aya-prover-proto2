// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableSet;
import kala.control.Option;
import org.aya.api.error.CollectingReporter;
import org.aya.api.util.InterruptException;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.term.Term;
import org.aya.terck.CallGraph;
import org.aya.terck.CallResolver;
import org.aya.terck.error.NonTerminating;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.CircularSignatureError;
import org.aya.tyck.trace.Trace;
import org.aya.util.MutableGraph;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Tyck statements in SCC.
 *
 * @author kiva
 */
public record AyaSccTycker(
  @NotNull StmtTycker tycker,
  @NotNull CollectingReporter reporter,
  @NotNull ResolveInfo resolveInfo,
  @NotNull DynamicSeq<@NotNull Def> wellTyped
) implements SCCTycker<TyckUnit, AyaSccTycker.SCCTyckingFailed> {
  public AyaSccTycker(ResolveInfo resolveInfo, @Nullable Trace.Builder builder, @NotNull CollectingReporter reporter) {
    this(new StmtTycker(reporter, builder), reporter, resolveInfo, DynamicSeq.create());
  }

  public @NotNull ImmutableSeq<TyckUnit> tyckSCC(@NotNull ImmutableSeq<TyckUnit> scc) {
    try {
      if (scc.sizeEquals(1)) {
        var unit = scc.first();
        if (unit instanceof Decl.FnDecl fn && fn.body.isLeft()) checkSimpleFn(fn);
        else checkUnit(unit);
      } else checkMutual(scc);
      return ImmutableSeq.empty();
    } catch (SCCTyckingFailed failed) {
      return failed.what;
    }
  }

  private void checkMutual(@NotNull ImmutableSeq<TyckUnit> scc) {
    var headerOrder = headerOrder(scc);
    headerOrder.forEach(this::checkHeader);
    headerOrder.forEach(this::checkBody);
    terck(headerOrder.view());
  }

  private void checkUnit(@NotNull TyckUnit unit) {
    checkBody(unit);
    terck(SeqView.of(unit));
  }

  private boolean isRecursive(@NotNull Decl unit) {
    return resolveInfo.depGraph().hasSuc(unit, unit);
  }

  private void checkHeader(@NotNull TyckUnit stmt) {
    switch (stmt) {
      case Decl decl -> tycker.tyckHeader(decl, tycker.newTycker());
      case Sample sample -> sample.tyckHeader(tycker);
      // TODO[ice]: tyck ctor/field
      default -> {}
    }
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(stmt));
  }

  private void checkSimpleFn(@NotNull Decl.FnDecl fn) {
    if (isRecursive(fn)) {
      reporter.report(new NonTerminating(fn.sourcePos, fn.ref.name(), null));
      throw new SCCTyckingFailed(ImmutableSeq.of(fn));
    }
    wellTyped.append(tycker.simpleFn(tycker.newTycker(), fn));
  }

  private void checkBody(@NotNull TyckUnit stmt) {
    switch (stmt) {
      case Decl decl -> {
        var tyck = tycker.tyck(decl, tycker.newTycker());
        wellTyped.append(tyck);
      }
      case Sample sample -> {
        var tyck = sample.tyck(tycker);
        if (tyck != null) wellTyped.append(tyck);
      }
      case Remark remark -> Option.of(remark.literate).forEach(l -> l.tyck(tycker.newTycker()));
      default -> {}
    }
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(stmt));
  }

  private void terck(@NotNull SeqView<TyckUnit> units) {
    // TODO: terck other definitions
    var fn = units.filterIsInstance(Decl.FnDecl.class)
      .map(decl -> decl.ref.core)
      .filter(def -> isRecursive(def.ref().concrete));
    if (fn.isEmpty()) return;
    terckRecursiveFn(fn);
  }

  private void terckRecursiveFn(@NotNull SeqView<FnDef> fn) {
    var targets = MutableSet.<Def>from(fn);
    var graph = CallGraph.<Def, Term.Param>create();
    fn.forEach(def -> def.accept(new CallResolver(def, targets), graph));
    var failed = graph.findNonTerminating();
    if (failed != null) failed.forEach(f -> {
      var ref = f.matrix().domain().ref();
      reporter.report(new NonTerminating(ref.concrete.sourcePos, ref.name(), f));
    });
  }

  /**
   * Generate the order of dependency of headers, fail if a cycle occurs.
   *
   * @author re-xyr, kiva
   */
  public @NotNull ImmutableSeq<TyckUnit> headerOrder(@NotNull ImmutableSeq<TyckUnit> stmts) {
    var graph = MutableGraph.<TyckUnit>create();
    stmts.forEach(stmt -> {
      var reference = DynamicSeq.<TyckUnit>create();
      SigRefFinder.HEADER_ONLY.visit(stmt, reference);
      graph.sucMut(stmt).appendAll(reference.view().filter(TyckUnit::needTyck));
    });
    var order = graph.topologicalOrder();
    var cycle = order.view().filter(s -> s.sizeGreaterThan(1));
    if (cycle.isNotEmpty()) {
      cycle.forEach(c -> reporter.report(new CircularSignatureError(c)));
      throw new SCCTyckingFailed(stmts);
    }
    return order.flatMap(Function.identity());
  }

  public static class SCCTyckingFailed extends InterruptException {
    public @NotNull ImmutableSeq<TyckUnit> what;

    public SCCTyckingFailed(@NotNull ImmutableSeq<TyckUnit> what) {
      this.what = what;
    }

    @Override public InterruptStage stage() {
      return InterruptStage.Tycking;
    }
  }
}
