package com.css.nginx.parser

import com.css.nginx.lexer.NginxLexer
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxFile
import com.css.nginx.psi.NginxTypes
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

class NginxParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = NginxLexer()
    override fun createParser(project: Project?) = NginxParser()
    override fun getFileNodeType() = NginxTypes.FILE
    override fun getCommentTokens() = TokenSet.create(NginxTypes.COMMENT)
    override fun getStringLiteralElements() = TokenSet.create(NginxTypes.STRING)
    override fun createElement(node: ASTNode): PsiElement =
        if (node.elementType == NginxTypes.DIRECTIVE) NginxDirective(node) else ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider) = NginxFile(viewProvider)
}
