package com.css.nginx.structure

import com.css.nginx.NginxConstants.STRUCTURE_DIRECTIVES
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxFile
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/** Outline of blocks (http, server, location, upstream...) plus the include, listen and server_name lines that identify them. */
class NginxStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(file: PsiFile) = object : TreeBasedStructureViewBuilder() {
        override fun createStructureViewModel(editor: Editor?): StructureViewModel =
            object : StructureViewModelBase(file, editor, Element(file)), StructureViewModel.ElementInfoProvider {
                override fun isAlwaysShowsPlus(element: StructureViewTreeElement) = false
                override fun isAlwaysLeaf(element: StructureViewTreeElement) =
                    (element.value as? NginxDirective)?.block == null
            }.withSuitableClasses(NginxDirective::class.java)
    }

    class Element(private val e: PsiElement) : StructureViewTreeElement {
        override fun getValue() = e
        override fun navigate(requestFocus: Boolean) = (e as? Navigatable)?.navigate(requestFocus) ?: Unit
        override fun canNavigate() = (e as? Navigatable)?.canNavigate() == true
        override fun canNavigateToSource() = canNavigate()

        override fun getChildren(): Array<TreeElement> {
            val children = when (e) {
                is NginxFile -> PsiTreeUtil.getChildrenOfTypeAsList(e, NginxDirective::class.java)
                is NginxDirective -> e.directives
                else -> emptyList()
            }
            return children.filter { it.block != null || it.directiveName in STRUCTURE_DIRECTIVES }.map(::Element)
                .toTypedArray()
        }

        override fun getPresentation(): ItemPresentation = PresentationData(
            when (e) {
                is NginxDirective -> (listOf(e.directiveName) + e.args.map { a -> a.joinToString("") { it.text } }).joinToString(
                    " "
                )

                else -> (e as? PsiFile)?.name
            },
            null, null, null,
        )
    }
}
