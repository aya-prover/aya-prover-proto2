// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Generalize;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface StmtConsumer extends Consumer<Stmt>, EndoExpr {
  default void accept(@NotNull Stmt stmt) {
    switch (stmt) {
      case Decl decl -> {
        if (decl instanceof TeleDecl<?> telescopic) visitTelescopic(telescopic);
        switch (decl) {
          case TeleDecl.DataDecl data -> data.body.forEach(this);
          case ClassDecl clazz -> clazz.members.forEach(this);
          case TeleDecl.FnDecl fn -> fn.body = fn.body.map(this, cl -> cl.descent(this, this::apply));
          case TeleDecl.DataCtor ctor -> {
            ctor.patterns = ctor.patterns.map(cl -> cl.descent(this::apply));
            ctor.clauses = ctor.clauses.descent(this);
          }
          case TeleDecl.ClassMember field -> field.body = field.body.map(this);
          case TeleDecl.PrimDecl ignored -> {}
        }
      }
      case Command command -> {
        switch (command) {
          case Command.Module module -> module.contents().forEach(this);
          case Command.Import ignored -> {}
          case Command.Open ignored -> {}
        }
      }
      case Generalize generalize -> generalize.type = apply(generalize.type);
    }
  }

  default void visitTelescopic(@NotNull TeleDecl<?> telescopic) {
    telescopic.modifyTelescope(t -> t.map(param -> param.descent(this)));
    telescopic.modifyResult(this);
  }
}
