// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author kiva
 */
public sealed interface CodeShape {
  /** A capture group, see {@link CodeShape.CtorShape} and {@link ShapeMatcher#captures()} */
  sealed interface Moment permits CtorShape, DataShape, FnShape, ParamShape.Licit, PatShape.Bind, TermShape.ShapeCall {
    @NotNull MomentId name();
  }

  /** Typed capture name, rather than plain strings */
  sealed interface MomentId {
  }

  enum GlobalId implements MomentId, Serializable {
    ZERO, SUC, NIL, CONS,
  }

  record LocalId(@NotNull String name) implements MomentId {
    public static final @NotNull LocalId IGNORED = new LocalId("_");
    public static final @NotNull LocalId LHS = new LocalId("lhs");
    public static final @NotNull LocalId RHS = new LocalId("rhs");
    public static final @NotNull LocalId DATA = new LocalId("Data");
    public static final @NotNull LocalId FUNC = new LocalId("Func");
    public static final @NotNull LocalId TYPE = new LocalId("Type0");
  }

  record FnShape(
    @NotNull MomentId name,
    @NotNull ImmutableSeq<ParamShape> tele,
    @NotNull TermShape result,
    @NotNull Either<TermShape, ImmutableSeq<ClauseShape>> body
  ) implements CodeShape, Moment {}

  record ClauseShape(@NotNull ImmutableSeq<PatShape> pats, @NotNull TermShape body) implements CodeShape {
  }

  record DataShape(
    @NotNull MomentId name,
    @NotNull ImmutableSeq<ParamShape> tele,
    @NotNull ImmutableSeq<CtorShape> ctors
  ) implements CodeShape, Moment {}

  record CtorShape(
    @NotNull GlobalId name,
    @NotNull ImmutableSeq<ParamShape> tele
  ) implements CodeShape, Moment {}
}
