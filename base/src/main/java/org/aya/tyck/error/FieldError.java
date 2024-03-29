// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.def.MemberDef;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface FieldError extends TyckError {
  record MissingField(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<AnyVar> missing
  ) implements FieldError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Missing field(s):"), Doc.commaList(missing.view()
        .map(BasePrettier::varDoc)
        .map(Doc::code)));
    }
  }

  record NoSuchField(
    @NotNull DefVar<?, ?> classRef,
    @NotNull Expr.Field<Expr> member
  ) implements FieldError {
    @Override public @NotNull SourcePos sourcePos() {
      return member.name().sourcePos();
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("The member"),
        Doc.code(member.name().data()),
        Doc.english("does not exist in class"),
        Doc.code(classRef.name()));
    }
  }

  record UnknownField(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements FieldError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Unknown field"),
        Doc.code(name),
        Doc.english("projected")
      );
    }
  }

  record ArgMismatch(
    @Override @NotNull SourcePos sourcePos,
    @NotNull MemberDef memberDef,
    int supplied
  ) implements FieldError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Expected"),
        Doc.plain(String.valueOf(memberDef.ref.core.telescope.size())),
        Doc.english("arguments, but found"),
        Doc.plain(String.valueOf(supplied)),
        Doc.english("arguments for field"),
        BasePrettier.linkRef(memberDef.ref, BasePrettier.MEMBER));
    }
  }
}
