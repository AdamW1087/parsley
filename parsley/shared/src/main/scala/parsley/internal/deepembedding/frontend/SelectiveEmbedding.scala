/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend

import parsley.internal.deepembedding.backend, backend.StrictParsley

private [parsley] final class Branch[A, B, C](b: LazyParsley[Either[A, B]], p: =>LazyParsley[A => C], q: =>LazyParsley[B => C])
    extends Ternary[Either[A, B], A => C, B => C, C](b, p, q) {
    override def make(b: StrictParsley[Either[A, B]], p: StrictParsley[A => C], q: StrictParsley[B => C]): StrictParsley[C] = new backend.Branch(b, p, q)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[C] = visitor.visit(this, context)(b, p, q)
}

private [parsley] final class If[A](b: LazyParsley[Boolean], p: =>LazyParsley[A], q: =>LazyParsley[A]) extends Ternary[Boolean, A, A, A](b, p, q) {
    override def make(b: StrictParsley[Boolean], p: StrictParsley[A], q: StrictParsley[A]): StrictParsley[A] = new backend.If(b, p, q)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(b, p, q)
}

private [parsley] final class Filter[A](p: LazyParsley[A], pred: A => Boolean) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Filter(p, pred)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, pred)
}
private [parsley] final class MapFilter[A, B](p: LazyParsley[A], f: A => Option[B]) extends Unary[A, B](p) {
    override def make(p: StrictParsley[A]): StrictParsley[B] = new backend.MapFilter(p, f)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[B] = visitor.visit(this, context)(p, f)
}
private [parsley] final class UnexpectedWhen[A](p: LazyParsley[A], pred: PartialFunction[A, (String, Option[String])]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.UnexpectedWhen(p, pred)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, pred)
}
