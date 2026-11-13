package com.css.nginx.editor

import com.css.nginx.psi.NginxTypes
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/** Highlights the matching brace; Enter after `{` also inserts the closing `}`. */
class NginxBraceMatcher : PairedBraceMatcher {
    override fun getPairs() = arrayOf(BracePair(NginxTypes.LBRACE, NginxTypes.RBRACE, true))
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) = openingBraceOffset
}
