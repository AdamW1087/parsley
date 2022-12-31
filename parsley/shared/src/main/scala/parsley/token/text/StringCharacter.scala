/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley, Parsley.empty
import parsley.character.{char, satisfy, satisfyUtf16}
import parsley.combinator.skipSome
import parsley.errors.combinator.ErrorMethods
import parsley.implicits.character.charLift
import parsley.token.descriptions.text.EscapeDesc
import parsley.token.errors.ErrorConfig
import parsley.token.predicate.{Basic, CharPredicate, NotRequired, Unicode}

private [token] abstract class StringCharacter {
    def apply(isLetter: CharPredicate): Parsley[Option[Int]]
}

private [token] class RawCharacter(err: ErrorConfig) extends StringCharacter {
    override def apply(isLetter: CharPredicate): Parsley[Option[Int]] = isLetter match {
        case Basic(isLetter) => ErrorConfig.label(err.labelStringCharacter)(satisfy(isLetter).map(c => Some(c.toInt)))
        case Unicode(isLetter) => ErrorConfig.label(err.labelStringCharacter)(satisfyUtf16(isLetter).map(Some(_)))
        case NotRequired => empty
    }
}

private [token] class EscapableCharacter(desc: EscapeDesc, escapes: Escape, space: Parsley[_], err: ErrorConfig) extends StringCharacter {
    private lazy val escapeEmpty = desc.emptyEscape.fold[Parsley[Char]](empty)(char)
    private lazy val escapeGap = {
        if (desc.gapsSupported) skipSome(ErrorConfig.label(err.labelEscapeStringGap)(space)) *> ErrorConfig.label(err.labelEscapeStringGapEnd)(desc.escBegin)
        else empty
    }
    private lazy val stringEscape: Parsley[Option[Int]] = ErrorConfig.label(err.labelEscapeSequence) {
        desc.escBegin *> (escapeGap #> None
                      <|> escapeEmpty #> None
                      <|> escapes.escapeCode.map(Some(_)).explain(err.explainEscapeInvalid))
    }

    override def apply(isLetter: CharPredicate): Parsley[Option[Int]] = isLetter match {
        case Basic(isLetter) => ErrorConfig.label(err.labelStringCharacter) {
            ErrorConfig.label(err.labelStringCharacterGraphic)(satisfy(c => isLetter(c) && c != desc.escBegin).map(c => Some(c.toInt))) <|> stringEscape
        }
        case Unicode(isLetter) => ErrorConfig.label(err.labelStringCharacter) {
            ErrorConfig.label(err.labelStringCharacterGraphic)(satisfyUtf16(c => isLetter(c) && c != desc.escBegin.toInt).map(Some(_))) <|> stringEscape
        }
        case NotRequired => stringEscape
    }
}
