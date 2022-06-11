// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.ref.DefVar;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 * @see DefVar
 */
public sealed interface Decl extends OpDecl, SourceNode, TyckUnit permits TopLevelDecl, Signatured {
  @Contract(pure = true) @NotNull DefVar<?, ?> ref();
  @NotNull BindBlock bindBlock();

  @NotNull SourcePos entireSourcePos();

  @Override default boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return ref().isInModule(currentMod) && ref().core == null;
  }
}
