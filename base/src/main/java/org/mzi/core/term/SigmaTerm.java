// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.Param;
import org.mzi.util.Decision;

/**
 * @author re-xyr
 */
public record SigmaTerm(@NotNull boolean co, @NotNull ImmutableSeq<@NotNull Param> params, @NotNull Term body) implements Term {
  @Override @Contract(pure = true) public @NotNull Decision whnf() {
    return Decision.YES;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitSigma(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitSigma(this, p, q);
  }
}
