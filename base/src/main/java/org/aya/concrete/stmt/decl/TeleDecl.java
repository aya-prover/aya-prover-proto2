// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.BindBlock;
import org.aya.core.def.*;
import org.aya.core.term.DataCall;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.generic.Modifier;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.UnaryOperator;

/**
 * Concrete telescopic definition, corresponding to {@link Def}.
 *
 * @author re-xyr
 * @see Decl
 */
public sealed abstract class TeleDecl<RetTy extends Term> extends CommonDecl {
  public @Nullable Expr result;
  // will change after resolve
  public @NotNull ImmutableSeq<Expr.Param> telescope;
  public @Nullable Def.Signature<RetTy> signature;

  public sealed abstract static class TopLevel<RetTy extends Term> extends TeleDecl<RetTy> implements Decl.TopLevel {
    private final @NotNull DeclInfo.Personality personality;
    public @Nullable Context ctx = null;

    protected TopLevel(
      @NotNull DeclInfo info, @NotNull ImmutableSeq<Expr.Param> telescope,
      @Nullable Expr result, @NotNull DeclInfo.Personality personality
    ) {
      super(info, telescope, result);
      this.personality = personality;
    }

    @Override public @NotNull DeclInfo.Personality personality() {
      return personality;
    }

    @Override public @Nullable Context getCtx() {
      return ctx;
    }

    @Override public void setCtx(@NotNull Context ctx) {
      this.ctx = ctx;
    }
  }

  public void modifyResult(@NotNull UnaryOperator<Expr> f) {
    if (result != null) result = f.apply(result);
  }

  public void modifyTelescope(@NotNull UnaryOperator<ImmutableSeq<Expr.Param>> f) {
    telescope = f.apply(telescope);
  }

  protected TeleDecl(@NotNull DeclInfo info, @NotNull ImmutableSeq<Expr.Param> telescope, @Nullable Expr result) {
    super(info);
    this.result = result;
    this.telescope = telescope;
  }

  @Contract(pure = true) public abstract @NotNull DefVar<? extends Def, ? extends TeleDecl<RetTy>> ref();

  /**
   * @author ice1000
   * @implSpec the result field of {@link PrimDecl} might be {@link Expr.Error},
   * which means it's unspecified in the concrete syntax.
   * @see PrimDef
   */
  public static final class PrimDecl extends TopLevel<Term> {
    public final @NotNull DefVar<PrimDef, PrimDecl> ref;

    public PrimDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @Nullable Expr result
    ) {
      super(new DeclInfo(Accessibility.Public, sourcePos, entireSourcePos, null, BindBlock.EMPTY), telescope, result, DeclInfo.Personality.NORMAL);
      this.ref = DefVar.concrete(this, name);
    }

    @Override public boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
      return ref.isInModule(currentMod) && signature == null;
    }

    @Override public @NotNull DefVar<PrimDef, PrimDecl> ref() {
      return ref;
    }
  }

  /**
   * @implNote {@link TeleDecl#signature} is always null.
   */
  public static final class DataCtor extends TeleDecl<DataCall> {
    public final @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref;
    public DefVar<DataDef, DataDecl> dataRef;
    public @NotNull Expr.PartEl clauses;
    public @NotNull ImmutableSeq<Arg<Pattern>> patterns;
    public final boolean coerce;

    public DataCtor(
      @NotNull DeclInfo info,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr.PartEl clauses,
      @NotNull ImmutableSeq<Arg<Pattern>> patterns,
      boolean coerce, @Nullable Expr result
    ) {
      super(info, telescope, result);
      this.clauses = clauses;
      this.coerce = coerce;
      this.patterns = patterns;
      this.ref = DefVar.concrete(this, name);
      this.telescope = telescope;
    }

    @Override public @NotNull DefVar<CtorDef, DataCtor> ref() {
      return ref;
    }
  }

  /**
   * Concrete data definition
   *
   * @author kiva
   * @see DataDef
   */
  public static final class DataDecl extends TopLevel<SortTerm> {
    public final @NotNull DefVar<DataDef, DataDecl> ref;
    public final @NotNull ImmutableSeq<DataCtor> body;
    /** Yet type-checked constructors */
    public final @NotNull MutableList<@NotNull CtorDef> checkedBody = MutableList.create();

    public DataDecl(
      @NotNull DeclInfo info,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @Nullable Expr result,
      @NotNull ImmutableSeq<DataCtor> body,
      @NotNull DeclInfo.Personality personality
    ) {
      super(info, telescope, result, personality);
      this.body = body;
      this.ref = DefVar.concrete(this, name);
      body.forEach(ctors -> ctors.dataRef = ref);
    }

    @Override public @NotNull DefVar<DataDef, DataDecl> ref() {
      return this.ref;
    }
  }

  public static final class ClassMember extends TeleDecl<Term> {
    public final @NotNull DefVar<MemberDef, ClassMember> ref;
    public DefVar<ClassDef, ClassDecl> classDef;
    public @NotNull Option<Expr> body;
    public final boolean coerce;

    public ClassMember(
      @NotNull DeclInfo info, @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Option<Expr> body,
      boolean coerce
    ) {
      super(info, telescope, result);
      this.coerce = coerce;
      this.body = body;
      this.ref = DefVar.concrete(this, name);
    }

    @Override public @NotNull DefVar<MemberDef, ClassMember> ref() {
      return ref;
    }
  }

  public sealed interface FnBody {
    FnBody map(@NotNull UnaryOperator<Expr> f, @NotNull UnaryOperator<Pattern.Clause> g);
  }

  public record ExprBody(Expr expr) implements FnBody {
    @Override public ExprBody map(@NotNull UnaryOperator<Expr> f, @NotNull UnaryOperator<Pattern.Clause> g) {
      return new ExprBody(f.apply(expr));
    }
  }

  public record BlockBody(ImmutableSeq<Pattern.Clause> clauses) implements FnBody {
    @Override public BlockBody map(@NotNull UnaryOperator<Expr> f, @NotNull UnaryOperator<Pattern.Clause> g) {
      return new BlockBody(clauses.map(g));
    }
  }

  /**
   * Concrete function definition
   *
   * @author re-xyr
   * @see FnDef
   */
  public static final class FnDecl extends TopLevel<Term> {
    public final @NotNull EnumSet<Modifier> modifiers;
    public final @NotNull DefVar<FnDef, FnDecl> ref;

    /**
     * If a function is anonymous. It only occurs when the personality is example/counterexample, and the {@code ref.name} is generated.
     */
    public final boolean isAnonymous;
    public @NotNull FnBody body;

    public FnDecl(
      @NotNull DeclInfo info,
      @NotNull EnumSet<Modifier> modifiers,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @Nullable Expr result,
      @NotNull FnBody body,
      @NotNull DeclInfo.Personality personality,
      boolean isAnonymous
    ) {
      super(info, telescope, result, personality);

      assert (!isAnonymous) || personality != DeclInfo.Personality.NORMAL;

      this.modifiers = modifiers;
      this.ref = DefVar.concrete(this, name);
      this.isAnonymous = isAnonymous;
      this.body = body;
    }

    @Override public @NotNull DefVar<FnDef, FnDecl> ref() {
      return ref;
    }
  }
}
