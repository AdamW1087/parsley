/* SPDX-FileCopyrightText: © 2021 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.stacks

import parsley.internal.machine.instructions.Instr

private [machine] final class HandlerStack(
    val calls: CallStack,
    val instrs: Array[Instr],
    val pc: Int,
    val stacksz: Int,
    val tail: HandlerStack)
private [machine] object HandlerStack extends Stack[HandlerStack] {
    implicit val inst: Stack[HandlerStack] = this
    type ElemTy = (Int, Int)
    // $COVERAGE-OFF$
    override protected def show(x: ElemTy): String = {
        val (pc, stacksz) = x
        s"Handler:$pc(-${stacksz + 1})"
    }
    override protected def head(xs: HandlerStack): ElemTy = (xs.pc, xs.stacksz)
    override protected def tail(xs: HandlerStack): HandlerStack = xs.tail
    // $COVERAGE-ON$
}
