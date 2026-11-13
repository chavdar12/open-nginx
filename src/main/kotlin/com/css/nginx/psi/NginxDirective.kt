package com.css.nginx.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException

class NginxDirective(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    val nameElement: PsiElement get() = firstChild
    val directiveName: String get() = firstChild.text
    val block: PsiElement? get() = node.findChildByType(NginxTypes.BLOCK)?.psi
    val directives: List<NginxDirective> get() = block?.let { PsiTreeUtil.getChildrenOfTypeAsList(it, NginxDirective::class.java) }.orEmpty()

    /** Arguments as nginx sees them: tokens with no whitespace between them (/path/$uri) form one argument. */
    val args: List<List<PsiElement>>
        get() {
            val out = mutableListOf<MutableList<PsiElement>>()
            var glued = false
            var c = firstChild.nextSibling
            while (c != null) {
                when (c.node.elementType) {
                    NginxTypes.WORD, NginxTypes.STRING, NginxTypes.VARIABLE -> {
                        if (glued) out.last().add(c) else out.add(mutableListOf(c))
                        glued = true
                    }

                    NginxTypes.SEMI, NginxTypes.BLOCK -> break
                    else -> glued = false
                }
                c = c.nextSibling
            }
            return out
        }

    /** Declarations (`upstream app`, `set $x`, `location @x`...) are named: targets of Cmd+B, Find Usages, Rename; see NginxDeclarations. */
    override fun getNameIdentifier(): PsiElement? = NginxDeclarations.nameIdentifier(this)

    /** Index key: "app", "@fallback" or "$x" (for both $x and ${x}). */
    val declaredKey: String? get() = nameIdentifier?.let { NginxDeclarations.key(it.text) }

    // a variable's name has no $: usages are found by text, and "${x}" does not contain "$x"
    override fun getName(): String? = declaredKey?.removePrefix("$")

    override fun setName(name: String): PsiElement {
        val id = nameIdentifier ?: throw IncorrectOperationException("Only upstreams, variables and named locations can be renamed")
        val text = if (id.node.elementType == NginxTypes.VARIABLE) "$" + name.removePrefix("$") else name
        id.replace(nginxToken(this, text))
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getReferences(): Array<PsiReference> = NginxReferences.of(this)
}
