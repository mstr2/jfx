package com.sun.javafx.css.syntax;

import javafx.css.syntax.AtKeywordToken;
import javafx.css.syntax.BadStringToken;
import javafx.css.syntax.BadUrlToken;
import javafx.css.syntax.ColonToken;
import javafx.css.syntax.CommaToken;
import javafx.css.syntax.CDCToken;
import javafx.css.syntax.CDOToken;
import javafx.css.syntax.DelimToken;
import javafx.css.syntax.HashToken;
import javafx.css.syntax.IdentToken;
import javafx.css.syntax.NumericToken;
import javafx.css.syntax.RightCurlyToken;
import javafx.css.syntax.RightParenToken;
import javafx.css.syntax.RightBracketToken;
import javafx.css.syntax.SemicolonToken;
import javafx.css.syntax.StringToken;
import javafx.css.syntax.UrlToken;
import javafx.css.syntax.WhitespaceToken;

public sealed interface CssPreservedToken
        permits AtKeywordToken, BadStringToken, BadUrlToken, ColonToken, CommaToken, CDCToken, CDOToken,
                DelimToken, HashToken, IdentToken, NumericToken, RightCurlyToken, RightParenToken,
                RightBracketToken, SemicolonToken, StringToken, UrlToken, WhitespaceToken {}
