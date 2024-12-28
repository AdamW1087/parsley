/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debugger

import parsley.Parsley
import parsley.Parsley.{atomic, empty, fresh}
import parsley.debugger.frontend.{DebugFrontend, ReusableFrontend}
import parsley.debugger.internal.{DebugContext, DivergenceContext}

import parsley.internal.deepembedding.frontend.LazyParsley
import parsley.internal.deepembedding.frontend.debugger.{TaggedWith, Named}
import parsley.internal.deepembedding.backend.debugger.{CheckDivergence, Debugging}

/** This object contains the combinators for attaching debuggers to parsers.
  *
  * @since 4.5.0
  */
object combinator {
    /** Returns true for any function type, which we ideally don't store as a string */
    val DefaultStringRules: PartialFunction[Any, Boolean] = {
        case _ : Function1[_, _] => true
        case _ : Function2[_, _, _] => true
        case _ : Function3[_, _, _, _] => true
        case _ : Function4[_, _, _, _, _] => true
        case _ : Function5[_, _, _, _, _, _] => true
        case _ : Function6[_, _, _, _, _, _, _] => true
        case _ : Function7[_, _, _, _, _, _, _, _] => true
        case _ : Function8[_, _, _, _, _, _, _, _, _] => true
        case _ : Function9[_, _, _, _, _, _, _, _, _, _] => true
        case _ : Function10[_, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function11[_, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function12[_, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function13[_, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function14[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function15[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function16[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function17[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function18[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function19[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function20[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function21[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
        case _ : Function22[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _] => true
    }

    /** Shorthand representation of a pair of a tree extraction function and a debugged parser. */
    private [parsley] type DebuggedPair[+A] = (() => DebugTree, Parsley[A])

    /** Attaches a debugger to a parser, returning a reference to the debug tree produced by
      * the parser's parse tree formed as it runs.
      *
      * Ideally, this is called on the highest level parser that you want to debug, as this combinator
      * will traverse downwards in the parser tree and attach debuggers to all the child parsers. As a
      * consequence of this, it is not really recommended to include debugged parsers as lower-level
      * parsers within your main parser, and rather to isolate their testing.
      *
      * Before running the parser, it may be advised to see if the current platform's implementation
      * of the debugger supports [[parsley.debugger.util.Collector]]
      * (via [[parsley.debugger.util.Collector.isSupported]]), which allows you to input an
      * object or class instance containing parsers, in order to analyse it via reflection to assign
      * the names given to the parser fields / methods in that object to disambiguate the parser names
      * in the tree in a much easier manner. Otherwise, you may want to attach explicit names to
      * your parsers using [[named]] to achieve the same effect manually.
      *
      * Without either of those name sources, parsers will simply appear as prettified versions
      * of their internal class names.
      *
      * After instrumentation, you would run the instrumented parser first, then after the parser
      * finishes running on ONE input ideally, you would run the debug tree generator function in
      * order to acquire the debug tree.
      *
      * This tree can then be introspected manually in a (runtime) debugger, or passed over to one
      * of the frontends in [[parsley.debugger.frontend]] in one of the extra companion libraries
      * for this debugger.
      *
      * It is recommended that you save the debug trees somewhere should you want to re-use the same
      * debugged parser, or if you want to use the same debugged parser as a child parser on
      * multiple parts of your main parser, make sure you use
      * [[parsley.debugger.combinator$.attachReusable[A](parser:parsley\.Parsley[A],toStringRules* attachReusable]]
      * , as re-use of the same debugged parser across multiple parent parsers will cause the
      * different parse trees to incorrectly merge in an undefined manner.
      *
      * A small warning: debugging an already debugged parser (via
      * [[parsley.debugger.combinator$.attachDebugger[A](parser:parsley\.Parsley[A],toStringRules* attachDebugger]]
      * and friends) is an undefined behaviour.
      *
      * See
      * [[parsley.debugger.combinator$.attachWithFrontend[A](parser:parsley\.Parsley[A],frontend:parsley\.debugger\.frontend\.DebugFrontend,toStringRules* attachWithFrontend]]
      * to automate the tree processing after parsing.
      *
      * @note Do not run a parser through this combinator multiple times.
      *
      * @param parser The parser to debug.
      * @param toStringRules If a parser's result matches any of the predicates in this sequence, it
      *                      will get turned into a string before storing in the debug tree. This is
      *                      usually for memory-usage optimisation. By default, all function-like
      *                      objects will be converted into strings, as closures are expensive to store.
      * @tparam A Output type of original parser.
      * @return A pair of the finalised tree, and the instrumented parser.
      */
    private [parsley] def attachDebugger[A](parser: Parsley[A], toStringRules: PartialFunction[Any, Boolean]): DebuggedPair[A] = {
        val context: DebugContext = new DebugContext(toStringRules)

        val attached: LazyParsley[A] = TaggedWith.tagRecursively(parser.internal, new Debugging(context))
        val resetter: Parsley[Unit]  = fresh(context.reset()).impure

        (() => context.getFinalTree, resetter *> new Parsley(attached))
    }

    // TODO: document
    def detectDivergence[A](parser: Parsley[A]): Parsley[A] = {
        val context: DivergenceContext = new DivergenceContext

        val attached: LazyParsley[A] = TaggedWith.tagRecursively(parser.internal, new CheckDivergence(context))
        val resetter: Parsley[Unit]  = fresh(context.reset()).impure

        resetter *> new Parsley(attached)
    }

    /* Attaches a debugger to a parser.
      *
      * This assumes the default rules of converting only lambdas and closures into strings when
      * storing in the output debug tree.
      *
      * @note Do not run a parser through this combinator multiple times.
      * @see
      *   The other overload of this method
      *   ([[parsley.debugger.combinator$.attachDebugger[A](parser:parsley\.Parsley[A],toStringRules* attachDebugger]])
      *   has more information on how this attachment works.
      * @param parser The parser to debug.
      * @tparam A Output type of original parser.
      * @return A pair of the finalised tree, and the instrumented parser.
      */
    private [parsley] def attachDebugger[A](parser: Parsley[A]): DebuggedPair[A] = attachDebugger[A](parser, DefaultStringRules)

    // $COVERAGE-OFF$
    /* Create a closure that freshly attaches a debugger to a parser every time it is called.
      * This is used for creating debugged parsers that can be used as children to multiple parent
      * parsers, as using the same debugged parser as a child to multiple parsers is unsafe.
      *
      * @note Do not run a parser through this combinator multiple times.
      * @see
      *   [[parsley.debugger.combinator$.attachDebugger[A](parser:parsley\.Parsley[A],toStringRules* attachDebugger]]
      *   for more information, or
      *   [[parsley.debugger.combinator$.attachWithFrontend[A](parser:parsley\.Parsley[A],frontend:parsley\.debugger\.frontend\.DebugFrontend,toStringRules* attachWithFrontend]]
      *   for a version that also accepts a [[parsley.debugger.frontend.DebugFrontend]].
      *
      * @return Generator closure for debugged versions of the input parser.
      */
    //private def attachReusable[A](parser: Parsley[A], toStringRules: PartialFunction[Any, Boolean]): () => DebuggedPair[A] = () => attachDebugger[A](parser, toStringRules)

    /* Create a closure that freshly attaches a debugger to a parser every time it is called.
      * This is used for creating debugged parsers that can be used as children to multiple parent
      * parsers, as using the same debugged parser as a child to multiple parsers is unsafe.
      *
      * This assumes the default rules of converting only lambdas and closures into strings when
      * storing in the output debug tree.
      *
      * @note Do not run a parser through this combinator multiple times.
      * @see
      *   The other overload of this method
      *   ([[parsley.debugger.combinator$.attachDebugger[A](parser:parsley\.Parsley[A],toStringRules* attachDebugger]])
      *   has more information on its usage.
      * @return Generator closure for debugged versions of the input parser.
      */
    //private def attachReusable[A](parser: Parsley[A]): () => DebuggedPair[A] = attachReusable[A](parser, defaultRules)

    // TODO: fix docs by incorporating some of the base stuff above
    /** Attach a debugger and an explicitly-available frontend in which the debug tree should be
      * proessed with.
      *
      * You would normally obtain a [[parsley.debugger.frontend.DebugFrontend]] frontend from its
      * respective package as either a static object or an instance object depending on whether the
      * renderer stores state. In the latter case, it is better to regenerate the frontend with
      * every new debugged parser. The frontend can be reusable (i.e. inherits from
      * [[parsley.debugger.frontend.ReusableFrontend]]) or single-use (i.e. inherits from
      * [[parsley.debugger.frontend.SingleUseFrontend]]).
      *
      * The instrumented parser will automatically call the frontend to render the debug tree, so it
      * may be recommended that you only use this with smaller parsers as large parsers may cause
      * large amounts of memory to be used for processing the tree.
      *
      * @note Do not run a parser through this combinator multiple times.
      *
      * @param parser The parser to debug.
      * @param frontend The frontend instance to render with.
      * @param toStringRules If a parser's result matches any of the predicates in this sequence, it
      *                      will get turned into a string before storing in the debug tree. This is
      *                      usually for memory-usage optimisation. By default, all function-like
      *                      objects will be converted into strings, as closures are expensive to store.
      * @tparam A Output type of parser.
      * @return A modified parser which will ask the frontend to process the produced debug tree after
      *         a call to [[Parsley.parse]] is made.
      */
    def attach[A](parser: Parsley[A], frontend: DebugFrontend, toStringRules: PartialFunction[Any, Boolean]): Parsley[A] = {
        val (tree, attached) = attachDebugger(parser, toStringRules)

        // Ideally, this should run 'attached', and render the tree regardless of the parser's success.
        val renderer = fresh {
            val frozen = tree()
            val input = frozen.fullInput

            frontend.process(input, frozen)
        }.impure

        atomic(attached <* renderer) <|> (renderer *> empty)
    }

    /** Attach a debugger and an explicitly-available frontend in which the debug tree should be
      * processed with. This frontend will also be called automatically with any debug trees produced
      * by the parser.
      *
      * This assumes the default rules of converting only lambdas and closures into strings when
      * storing in the output debug tree.
      *
      * @note Do not run a parser through this combinator multiple times.
      * @see
      *   The other overload of this method
      *   ([[parsley.debugger.combinator$.attach[A](parser:parsley\.Parsley[A],frontend:parsley\.debugger\.frontend\.DebugFrontend,toStringRules* attachDebugger]])
      *   has more information on its usage.
      * @param parser The parser to debug.
      * @param frontend The frontend instance to render with.
      * @tparam A Output type of parser.
      * @return A modified parser which will ask the frontend to process the produced debug tree after
      *         a call to [[Parsley.parse]] is made.
      *
      */
    def attach[A](parser: Parsley[A], frontend: DebugFrontend): Parsley[A] = attach[A](parser, frontend, DefaultStringRules)

    /** Create a closure that freshly attaches a debugger and a tree-processing frontend to a parser every
      * time it is called.
      *
      * @note Do not run a parser through this combinator multiple times.
      *
      * @return Generator closure for frontend-debugged versions of the input parser.
      */
    def attachReusable[A](parser: Parsley[A], frontend: =>ReusableFrontend, toStringRules: PartialFunction[Any, Boolean]): () => Parsley[A] = {
        () => attach(parser, frontend, toStringRules)
    }

    /** Create a closure that freshly attaches a debugger and a tree-processing frontend to a parser every
      * time it is called.
      *
      * This assumes the default rules of converting only lambdas and closures into strings when
      * storing in the output debug tree.
      *
      * @note Do not run a parser through this combinator multiple times.
      * @see
      *   The other overload of this method
      *   ([[parsley.debugger.combinator$.attachReusable[A](parser:parsley\.Parsley[A],frontend:=>parsley\.debugger\.frontend\.ReusableFrontend,toStringRules* attachDebugger]])
      *   has more information on its usage.
      * @return Generator closure for frontend-debugged versions of the input parser.
      */
    def attachReusable[A](parser: Parsley[A], frontend: =>ReusableFrontend): () => Parsley[A] = attachReusable[A](parser, frontend, DefaultStringRules)

    /* Attach a debugger and an implicitly-available frontend in which the debug tree should be
      * processed with.
      *
      * @note Do not run a parser through this combinator multiple times.
      * @see
      *   [[parsley.debugger.combinator$.attachWithFrontend[A](parser:parsley\.Parsley[A],frontend:parsley\.debugger\.frontend\.DebugFrontend,toStringRules* attachWithFrontend]]
      *   for more information.
      */
    /*def attach[A](parser: Parsley[A], toStringRules: PartialFunction[Any, Boolean])(implicit frontend: DebugFrontend): Parsley[A] = {
        attach(parser, frontend, toStringRules)
    }*/

    /* Attach a debugger and an implicitly-available frontend in which the debug tree should be
      * processed with.
      *
      * This assumes the default rules of converting only lambdas and closures into strings when
      * storing in the output debug tree.
      *
      * @note Do not run a parser through this combinator multiple times.
      * @see
      *   The other overload of this method
      *   ([[parsley.debugger.combinator$.attachWithImplicitFrontend[A](parser:parsley\.Parsley[A],toStringRules* attachWithImplicitFrontend]])
      *   has more information on its usage.
      */
    //def attach[A](parser: Parsley[A])(implicit frontend: DebugFrontend): Parsley[A] = attach[A](parser, defaultRules)

    /** Attach a name to a parser, for display within the debugger output.
      * Names assigned here have a higher precedence than names collected with [[parsley.debugger.util.Collector]].
      *
      * Renaming the same parser multiple times simply overwrites its name to the most latest rename.
      */
    def named[A](parser: Parsley[A], name: String): Parsley[A] = parser.internal match {
        case Named(i, _) => new Parsley(Named(i.asInstanceOf[LazyParsley[A]], name))
        case _           => new Parsley(Named(parser.internal, name))
    }

    /** Dot accessor versions of the combinators.  */
    implicit class DebuggerOps[A](par: Parsley[A]) {
        //def attachDebugger(toStringRules: PartialFunction[Any, Boolean]): DebuggedPair[A] = combinator.attachDebugger(par, toStringRules)
        //def attachReusable(toStringRules: PartialFunction[Any, Boolean]): () => DebuggedPair[A] = combinator.attachReusable(par, toStringRules)
        def attach(frontend: DebugFrontend, toStringRules: PartialFunction[Any, Boolean]): Parsley[A] = combinator.attach(par, frontend, toStringRules)
        def attachReusable(frontend: =>ReusableFrontend, toStringRules: PartialFunction[Any, Boolean]): () => Parsley[A] =
            combinator.attachReusable(par, frontend, toStringRules)
        //def attach(toStringRules: PartialFunction[Any, Boolean])(implicit frontend: DebugFrontend): Parsley[A] = combinator.attach(par, toStringRules)
        //def attachDebugger: DebuggedPair[A] = combinator.attachDebugger(par, defaultRules)
        //def attachReusable: () => DebuggedPair[A] = combinator.attachReusable(par, defaultRules)
        def attach(frontend: DebugFrontend): Parsley[A] = combinator.attach(par, frontend, DefaultStringRules)
        def attachReusable(frontend: =>ReusableFrontend): () => Parsley[A] = combinator.attachReusable(par, frontend, DefaultStringRules)
        //def attach(implicit frontend: DebugFrontend): Parsley[A] = combinator.attach(par, defaultRules)
        def named(name: String): Parsley[A] = combinator.named(par, name)
    }
    // $COVERAGE-ON$
}
