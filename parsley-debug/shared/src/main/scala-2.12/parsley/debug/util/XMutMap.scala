/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.util

import scala.collection.mutable

// Simple wrapper over mutable.Map, for bridging between Scala 2.12 and 2.13+.
// Gives access to addOne and subtractOne for Scala 2.12.
private [parsley] trait XMutMap[K, V] extends mutable.Map[K, V] {
    def addOne(elem: (K, V)): XMutMap.this.type

    def subtractOne(elem: K): XMutMap.this.type

    override def -=(k: K): XMutMap.this.type = subtractOne(k)

    override def +=(kv: (K, V)): XMutMap.this.type = addOne(kv)
}
