package javafx.css.syntax;

import com.sun.javafx.css.syntax.CssRawToken;

public sealed interface ComponentValue
        extends CssRawToken
        permits Block, AtKeywordToken, BadStringToken, BadUrlToken, ColonToken, CommaToken, CDCToken, CDOToken,
                DelimToken, HashToken, IdentToken, NumericToken, RightCurlyToken, RightParenToken,
                RightBracketToken, SemicolonToken, StringToken, UrlToken, WhitespaceToken {}
