// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.ClassDef;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Concrete classable definitions, corresponding to {@link ClassDef}.
 *
 * @author zaoqi
 * @see Decl
 */
public final class ClassDecl extends CommonDecl implements Decl.TopLevel {
  private final @NotNull DeclInfo.Personality personality;
  public @Nullable Context ctx = null;
  public @NotNull ImmutableSeq<TeleDecl.StructField> fields;
  public final @NotNull DefVar<?, ClassDecl> ref;

  @Override public @NotNull DeclInfo.Personality personality() {
    return personality;
  }

  @Override public @Nullable Context getCtx() {
    return ctx;
  }

  @Override public void setCtx(@NotNull Context ctx) {
    this.ctx = ctx;
  }

  public ClassDecl(
    @NotNull DeclInfo info, @NotNull String name,
    @NotNull DeclInfo.Personality personality,
    @NotNull ImmutableSeq<TeleDecl.StructField> fields
  ) {
    super(info);
    this.personality = personality;
    this.fields = fields;
    this.ref = DefVar.concrete(this, name);
  }

  @Override public @NotNull DefVar<?, ClassDecl> ref() {
    return ref;
  }
}
