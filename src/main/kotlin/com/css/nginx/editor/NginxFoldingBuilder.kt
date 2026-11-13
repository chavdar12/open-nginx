package com.css.nginx.editor

import com.css.nginx.psi.NginxTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** Folds multi-line { } blocks. */
class NginxFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
        PsiTreeUtil.collectElements(root) { it.node.elementType == NginxTypes.BLOCK && '\n' in it.text }
            .map { FoldingDescriptor(it.node, it.textRange) }.toTypedArray()

    override fun getPlaceholderText(node: ASTNode) = "{...}"
    override fun isCollapsedByDefault(node: ASTNode) = false
}
