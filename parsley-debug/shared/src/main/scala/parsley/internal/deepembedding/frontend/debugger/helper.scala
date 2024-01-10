/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend.debugger

import scala.collection.{Factory, mutable}

import org.typelevel.scalaccompat.annotation.unused
import parsley.debugger.internal.DebugContext
import parsley.internal.deepembedding.{singletons, Cont, ContOps, Id}
import parsley.internal.deepembedding.ContOps.{perform, result, suspend, zipWith, zipWith3, ContAdapter}
import parsley.internal.deepembedding.backend.StrictParsley
import parsley.internal.deepembedding.frontend._ // scalastyle:ignore underscore.import

private [parsley] object helper {
    // This map tracks seen parsers to prevent infinitely recursive parsers from overflowing the stack (and ties
    // the knot for these recursive parsers).
    // Use maps with weak keys or don't pass this into a >>= parser.
    private [parsley] final class ParserTracker(val map: mutable.Map[LazyParsley[_], Debugged[_]]) {
        def put(par: LazyParsley[_], dbg: Debugged[_]): Unit = map(par) = dbg

        def get(par: LazyParsley[_]): Debugged[_] = map(par)

        def contains(par: LazyParsley[_]): Boolean = map.contains(par)
    }

    // Keeping this around for easy access to LPM.
    @unused private [this] final class ContWrap[M[_, +_], R] {
        type LPM[+A] = M[R, LazyParsley[A]]
    }

    private def visitWithM[M[_, +_]: ContOps, A](parser: LazyParsley[A],
                                                 tracker: ParserTracker,
                                                 visitor: DebugInjectingVisitorM[M, LazyParsley[A]]): LazyParsley[A] =
        perform[M, LazyParsley[A]](parser.visit(visitor, tracker))

    // Run this to inject the debugger itself.
    private [parsley] def injectM[A](parser: LazyParsley[A], tracker: ParserTracker, dbgCtx: DebugContext): LazyParsley[A] =
        if (parser.isCps) {
            implicit val ops: ContOps[Cont.Impl] = Cont.ops
            val visitor = new DebugInjectingVisitorM[Cont.Impl, LazyParsley[A]](dbgCtx)
            visitWithM[Cont.Impl, A](parser, tracker, visitor)
        } else {
            implicit val ops: ContOps[Id.Impl] = Id.ops
            val visitor = new DebugInjectingVisitorM[Id.Impl, LazyParsley[A]](dbgCtx)
            visitWithM[Id.Impl, A](parser, tracker, visitor)
        }

    // This visitor uses Cont / ContOps to ensure that if a parser is deeply recursive, the user can all a method
    // to use the trampoline ( https://en.wikipedia.org/wiki/Trampoline_(computing) ) to ensure that all calls are
    // turned into heap thunks instead of stack frames.
    // $COVERAGE-OFF$
    private [parsley] final class DebugInjectingVisitorM[M[_, +_]: ContOps, R](dbgCtx: DebugContext)
        extends GenericLazyParsleyIVisitor[ParserTracker, ContWrap[M, R]#LPM] {
        private type L[+A] = ContWrap[M, R]#LPM[A]

        private def handlePossiblySeenAbstract[A](self: LazyParsley[A],
                                                  context: ParserTracker,
                                                  gen: (LazyParsley[A], DebugContext) => Debugged[A])(dbgF: => L[A]): L[A] =
            if (context.contains(self)) {
                result[R, LazyParsley[A], M](context.get(self).asInstanceOf[Debugged[A]])
            } else {
                val current = gen(self, dbgCtx)
                context.put(self, current)
                dbgF.map { dbgF_ =>
                    current.par = Some(dbgF_)
                    current
                }
            }

        private def handlePossiblySeen[A](self: LazyParsley[A], context: ParserTracker)(dbgF: => L[A]): L[A] =
            handlePossiblySeenAbstract(self, context, (s: LazyParsley[A], d) => new Debugged(s, None, None)(d))(dbgF)

        private def handleNoChildren[A](self: LazyParsley[A], context: ParserTracker): L[A] =
            handlePossiblySeen[A](self, context)(result[R, LazyParsley[A], M](self))

        // We assume _q must be lazy, as it'd be better to *not* force a strict value versus accidentally forcing a lazy value.
        // This is called handle2Ary as to not be confused with handling Binary[_, _, _].
        private def handle2Ary[P[X] <: LazyParsley[X], A, B, C](self: P[A], context: ParserTracker)
                                                               (constructor: (LazyParsley[B], LazyParsley[C]) => P[A])
                                                               (p: LazyParsley[B], _q: => LazyParsley[C]): L[A] =
            handlePossiblySeen[A](self, context) {
                zipWith[M, R, LazyParsley[B], LazyParsley[C], LazyParsley[A]](constructor)(
                    suspend[M, R, LazyParsley[B]](p.visit(this, context)),
                    suspend[M, R, LazyParsley[C]](_q.visit(this, context))
                )
            }

        override def visitSingleton[A](self: singletons.Singleton[A], context: ParserTracker): L[A] =
            handleNoChildren[A](self, context)

        override def visitUnary[A, B](self: Unary[A, B], context: ParserTracker)(p: LazyParsley[A]): L[B] =
            handlePossiblySeen[B](self, context) {
                suspend[M, R, LazyParsley[A]](p.visit(this, context)).map(dbgC => new Unary[A, B](dbgC) {
                    override def make(p: StrictParsley[A]): StrictParsley[B] = self.make(p)

                    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[B] = visitor.visitGeneric(this, context)

                    override private[parsley] def prettyName = self.prettyName
                })
            }

        override def visitBinary[A, B, C](self: Binary[A, B, C], context: ParserTracker)(l: LazyParsley[A], r: => LazyParsley[B]): L[C] =
            handlePossiblySeen[C](self, context) {
                zipWith[M, R, LazyParsley[A], LazyParsley[B], Binary[A, B, C]]((dbgL, dbgR) => new Binary[A, B, C](dbgL, dbgR) {
                    override def make(p: StrictParsley[A], q: StrictParsley[B]): StrictParsley[C] = self.make(p, q)

                    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[C] = visitor.visitGeneric(this, context)

                    override private [parsley] def prettyName = self.prettyName
                })(
                    suspend[M, R, LazyParsley[A]](l.visit(this, context)),
                    suspend[M, R, LazyParsley[B]](r.visit(this, context))
                )
            }

        override def visitTernary[A, B, C, D](self: Ternary[A, B, C, D], context: ParserTracker)(f: LazyParsley[A],
                                                                                                 s: => LazyParsley[B],
                                                                                                 t: => LazyParsley[C]): L[D] =
            handlePossiblySeen[D](self, context) {
                zipWith3[M, R, LazyParsley[A], LazyParsley[B], LazyParsley[C], Ternary[A, B, C, D]](
                    (dbgF: LazyParsley[A], dbgS: LazyParsley[B], dbgT: LazyParsley[C]) =>
                        new Ternary[A, B, C, D](dbgF, dbgS, dbgT) {
                            override def make(p: StrictParsley[A], q: StrictParsley[B], r: StrictParsley[C]): StrictParsley[D] = self.make(p, q, r)

                            override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[D] = visitor.visitGeneric(this, context)

                            override private[parsley] def prettyName = self.prettyName
                        }
                )(
                    suspend[M, R, LazyParsley[A]](f.visit(this, context)),
                    suspend[M, R, LazyParsley[B]](s.visit(this, context)),
                    suspend[M, R, LazyParsley[C]](t.visit(this, context)),
                )
            }

        // We want flatMap-produced parsers to be debugged too, so we can see the full extent of the produced parse tree.
        // This is critical, as flatMap allows these parsers to be turing-complete, and can produce any arbitrary parse path.
        override def visit[A, B](self: A >>= B, context: ParserTracker)(p: LazyParsley[A], f: A => LazyParsley[B]): L[B] =
            handlePossiblySeen[B](self, context) {
                // flatMap / >>= produces parsers arbitrarily, so there is no way we'd match by reference.
                // This is why a map with weak keys is required, so that these entries do not flood the map and
                // cause a massive memory leak.
                suspend[M, R, LazyParsley[A]](p.visit(this, context)).map { dbgC =>
                    def dbgF(x: A): LazyParsley[B] = {
                        val subvisitor = new DebugInjectingVisitorM[M, LazyParsley[B]](dbgCtx)
                        perform[M, LazyParsley[B]](f(x).visit(subvisitor, context))
                    }
                    new >>=(dbgC, dbgF)
                }
            }

        override def visit[A](self: <|>[A], context: ParserTracker)(p: LazyParsley[A], q: LazyParsley[A]): L[A] =
            handle2Ary[<|>, A, A, A](self, context)(new <|>(_, _))(p, q)

        // Iterative parsers need their own handling.
        override def visit[A, C](self: Many[A, C], context: ParserTracker)(p: LazyParsley[A], factory: Factory[A, C]): L[C] =
            handlePossiblySeen(self, context) {
                suspend[M, R, LazyParsley[A]](p.visit(this, context)).map(new Many[A, C](_, factory))
            }

        override def visit[A](self: ChainPost[A], context: ParserTracker)(p: LazyParsley[A], _op: => LazyParsley[A => A]): L[A] =
            handle2Ary[ChainPost, A, A, A => A](self, context)(new ChainPost(_, _))(p, _op)

        override def visit[A](self: ChainPre[A], context: ParserTracker)(p: LazyParsley[A], op: => LazyParsley[A => A]): L[A] =
            handle2Ary[ChainPre, A, A, A => A](self, context)(new ChainPre(_, _))(p, op)

        override def visit[A, B](self: Chainl[A, B], context: ParserTracker)(init: LazyParsley[B],
                                                                             p: => LazyParsley[A],
                                                                             op: => LazyParsley[(B, A) => B]): L[B] =
            handlePossiblySeen[B](self, context) {
                zipWith3[M, R, LazyParsley[B], LazyParsley[A], LazyParsley[(B, A) => B], Chainl[A, B]](new Chainl[A, B](_, _, _))(
                    suspend[M, R, LazyParsley[B]](init.visit(this, context)),
                    suspend[M, R, LazyParsley[A]](p.visit(this, context)),
                    suspend[M, R, LazyParsley[(B, A) => B]](op.visit(this, context))
                )
            }

        override def visit[A, B](self: Chainr[A, B], context: ParserTracker)(p: LazyParsley[A], op: => LazyParsley[(A, B) => B], wrap: A => B): L[B] =
            handlePossiblySeen[B](self, context) {
                zipWith[M, R, LazyParsley[A], LazyParsley[(A, B) => B], Chainr[A, B]](new Chainr[A, B](_, _, wrap))(
                    suspend[M, R, LazyParsley[A]](p.visit(this, context)),
                    suspend[M, R, LazyParsley[(A, B) => B]](op.visit(this, context))
                )
            }

        override def visit[A, C](self: SepEndBy1[A, C], context: ParserTracker)(p: LazyParsley[A], sep: => LazyParsley[_], factory: Factory[A, C]): L[C] =
            handlePossiblySeen[C](self, context) {
                zipWith[M, R, LazyParsley[A], LazyParsley[_], SepEndBy1[A, C]](new SepEndBy1[A, C](_, _, factory))(
                    suspend[M, R, LazyParsley[A]](p.visit(this, context)),
                    suspend[M, R, LazyParsley[_]](sep.visit(this, context))
                )
            }

        override def visit[A, C](self: ManyUntil[A, C], context: ParserTracker)(body: LazyParsley[Any], factory: Factory[A, C]): L[C] =
            handlePossiblySeen[C](self, context) {
                suspend[M, R, LazyParsley[Any]](body.visit(this, context)).map(new ManyUntil[A, C](_, factory))
            }

        // XXX: This will assume all completely unknown parsers have no children at all (i.e. are Singletons).
        override def visitUnknown[A](self: LazyParsley[A], context: ParserTracker): L[A] = self match {
            case d: Debugged[_] => result[R, LazyParsley[A], M](d.asInstanceOf[Debugged[A]]) // No need to debug a parser twice!
            case n: Named[_]    => n.par match {
                case g: GenericLazyParsley[_] => visitGeneric(g, context).map(_.asInstanceOf[Debugged[A]].withName(n.name))
                case alt: <|>[_]              => alt.visit(this, context).map(_.asInstanceOf[Debugged[A]].withName(n.name))
                case cpre: ChainPre[_]        => cpre.visit(this, context).map(_.asInstanceOf[Debugged[A]].withName(n.name))
                case _                        => visitUnknown(n.par, context).map(_.asInstanceOf[Debugged[A]].withName(n.name))
            }
            case _              => handleNoChildren(self, context)
        }
    }
    // $COVERAGE-ON$
}
