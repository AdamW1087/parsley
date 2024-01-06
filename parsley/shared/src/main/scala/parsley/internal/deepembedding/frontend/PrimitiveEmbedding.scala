/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend

import parsley.debug.{Breakpoint, Profiler}
import parsley.errors.ErrorBuilder
import parsley.state.Ref

import parsley.internal.deepembedding.backend, backend.StrictParsley

import org.typelevel.scalaccompat.annotation.nowarn3

private [parsley] final class Attempt[A](p: LazyParsley[A]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Attempt(p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p)

    override private[parsley] def prettyName = "atomic"
    // $COVERAGE-ON$
}
private [parsley] final class Look[A](p: LazyParsley[A]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Look(p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p)

    override private[parsley] def prettyName = "lookAhead"
    // $COVERAGE-ON$
}
private [parsley] final class NotFollowedBy[A](p: LazyParsley[A]) extends Unary[A, Unit](p) {
    override def make(p: StrictParsley[A]): StrictParsley[Unit] = new backend.NotFollowedBy(p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)(p)

    override private[parsley] def prettyName = "notFollowedBy"
    // $COVERAGE-ON$
}
private [parsley] final class Put[S](val ref: Ref[S], _p: LazyParsley[S]) extends Unary[S, Unit](_p) with UsesRef {
    override def make(p: StrictParsley[S]): StrictParsley[Unit] = new backend.Put(ref, p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)(ref, _p)

    override private[parsley] def prettyName = "Reg.put"
    // $COVERAGE-ON$
}
private [parsley] final class NewReg[S, A](val ref: Ref[S], init: LazyParsley[S], body: =>LazyParsley[A]) extends Binary[S, A, A](init, body) with UsesRef {
    override def make(init: StrictParsley[S], body: StrictParsley[A]): StrictParsley[A] = new backend.NewReg(ref, init, body)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(ref, init, body)

    override private[parsley] def prettyName = "fillReg"
    // $COVERAGE-ON$
}
private [parsley] final class Span(p: LazyParsley[_]) extends Unary[Any, String](p) {
    override def make(p: StrictParsley[Any]): StrictParsley[String] = new backend.Span(p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[String] = visitor.visit(this, context)(p)

    override private[parsley] def prettyName: String = "span"
    // $COVERAGE-ON$
}

// $COVERAGE-OFF$
private [parsley] final class Debug[A](p: LazyParsley[A], name: String, ascii: Boolean, break: Breakpoint, watchedRefs: Seq[(Ref[_], String)] @nowarn3)
    extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Debug(p, name, ascii, break, watchedRefs)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, name, ascii, break, watchedRefs)

    override private[parsley] def prettyName = "debug"
}
private [parsley] final class DebugError[A](p: LazyParsley[A], name: String, ascii: Boolean, errBuilder: ErrorBuilder[_]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.DebugError(p, name, ascii, errBuilder)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, name, ascii, errBuilder)

    override private[parsley] def prettyName = "debugError"
}

private [parsley] final class Profile[A](p: LazyParsley[A], name: String, profiler: Profiler) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Profile(p, name, profiler)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, name, profiler)

    override private[parsley] def prettyName: String = "profile"
}

private [parsley] final class Opaque[A](p: LazyParsley[A]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Opaque(p)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = p.visit(visitor, context)

    override private[parsley] def prettyName: String = "impure"
}
// $COVERAGE-ON$
