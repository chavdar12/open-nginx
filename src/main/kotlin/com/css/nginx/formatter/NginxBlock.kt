package com.css.nginx.formatter

import com.css.nginx.psi.NginxTypes
import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.css.nginx.NginxConstants.ARGUMENT_TOKENS

/**
 * One block per PSI node. Only whitespace between tokens is ever changed, and tokens glued together
 * (`/path/$uri`) stay glued, so formatting can't change what nginx reads.
 */
class NginxBlock(
    node: ASTNode,
    alignment: Alignment?,
    private val indent: Indent,
    private val style: Style,
    private val firstArgAlignment: Alignment? = null,
) : AbstractBlock(node, null, alignment) {
    class Style(val common: CommonCodeStyleSettings, val nginx: NginxCodeStyleSettings)

    override fun getIndent() = indent
    override fun isLeaf() = myNode.firstChildNode == null

    // a leaf never gets sub-blocks, so injected regex / Lua stay opaque (the platform would add blocks for injected text)
    override fun getSubBlocks(): List<Block> = if (isLeaf) emptyList() else super.getSubBlocks()

    private fun kids(n: ASTNode = myNode) =
        n.getChildren(null).filter { it.elementType != TokenType.WHITE_SPACE && it.textLength > 0 }

    override fun buildChildren(): List<Block> {
        val kids = kids()
        return when (myNode.elementType) {
            NginxTypes.DIRECTIVE -> {
                // wrapped arguments line up under the last argument of the first line, like log_format in the nginx docs
                val wrapAlign = if (style.nginx.ALIGN_MULTILINE_ARGUMENTS) Alignment.createAlignment() else null
                val argStarts =
                    kids.indices.filter { i -> i > 0 && kids[i].elementType in ARGUMENT_TOKENS && kids[i - 1].textRange.endOffset != kids[i].startOffset }
                val firstWrapped = argStarts.firstOrNull { startsLine(kids[it]) }
                val anchor = argStarts.lastOrNull { firstWrapped == null || it < firstWrapped }
                kids.mapIndexed { i, k ->
                    val align = when {
                        i == argStarts.firstOrNull() && firstArgAlignment != null -> firstArgAlignment
                        firstWrapped != null && (i == anchor || i in argStarts && startsLine(k)) -> wrapAlign
                        else -> null
                    }
                    val argIndent = i > 0 && k.elementType != NginxTypes.BLOCK && k.elementType != NginxTypes.SEMI
                    NginxBlock(
                        k,
                        align,
                        if (argIndent) Indent.getContinuationIndent() else Indent.getNoneIndent(),
                        style
                    )
                }
            }

            else -> {  // file or { } block
                val inBlock = myNode.elementType == NginxTypes.BLOCK
                val columns = if (style.nginx.ALIGN_VALUES_IN_COLUMNS) columnGroups(kids) else emptyMap()
                kids.map { k ->
                    val braces = k.elementType == NginxTypes.LBRACE || k.elementType == NginxTypes.RBRACE
                    NginxBlock(
                        k,
                        null,
                        if (inBlock && !braces) Indent.getNormalIndent() else Indent.getNoneIndent(),
                        style,
                        columns[k]
                    )
                }
            }
        }
    }

    /** Runs of one-line directives with arguments, split by blank lines, comment lines and { } directives, share a column. */
    private fun columnGroups(kids: List<ASTNode>): Map<ASTNode, Alignment> {
        val groups = HashMap<ASTNode, Alignment>()
        var current: Alignment? = null
        for (k in kids) {
            if (k.elementType == NginxTypes.COMMENT && !startsLine(k)) continue  // trailing comment
            val alignable =
                k.elementType == NginxTypes.DIRECTIVE && '\n' !in k.text && k.findChildByType(NginxTypes.BLOCK) == null &&
                        kids(k).getOrNull(1)?.elementType in ARGUMENT_TOKENS
            if (!alignable || blankLineBefore(k)) current = null
            if (alignable) groups[k] = current ?: Alignment.createAlignment(true).also { current = it }
        }
        return groups
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val n1 = (child1 as? NginxBlock)?.node ?: return null
        val n2 = (child2 as NginxBlock).node
        val t1 = n1.elementType
        val t2 = n2.elementType
        if (myNode.elementType == NginxTypes.DIRECTIVE) return when {
            t2 == NginxTypes.SEMI -> Spacing.createSpacing(0, 0, 0, false, 0)
            t2 == NginxTypes.BLOCK -> Spacing.createSpacing(1, 1, 0, false, 0)
            t1 == NginxTypes.COMMENT -> Spacing.createSpacing(0, 0, 1, true, 0)
            n1.textRange.endOffset == n2.startOffset -> Spacing.createSpacing(0, 0, 0, false, 0)
            else -> Spacing.createSpacing(1, 1, 0, true, 0)  // wrapped arguments stay wrapped
        }
        val oneLine =
            myNode.elementType == NginxTypes.BLOCK && style.common.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE && '\n' !in myNode.text
        return when {
            t1 == NginxTypes.LBRACE && t2 == NginxTypes.RBRACE -> Spacing.createSpacing(0, 0, 0, false, 0)
            oneLine && (t1 == NginxTypes.LBRACE || t2 == NginxTypes.RBRACE) -> {
                val n =
                    if (style.common.SPACE_WITHIN_BRACES) 1 else 0  // { return 204; } vs {return 204;}: cosmetic only
                Spacing.createSpacing(n, n, 0, false, 0)
            }

            oneLine -> Spacing.createSpacing(1, 1, 0, false, 0)
            t1 == NginxTypes.LBRACE || t2 == NginxTypes.RBRACE -> Spacing.createSpacing(0, 0, 1, true, 0)
            t2 == NginxTypes.COMMENT && !startsLine(n2) -> Spacing.createSpacing(
                1,
                Int.MAX_VALUE,
                0,
                false,
                0
            )  // keep trailing comments where they are
            else -> Spacing.createSpacing(0, 0, 1, true, style.common.KEEP_BLANK_LINES_IN_CODE)
        }
    }

    override fun getChildAttributes(newChildIndex: Int) = ChildAttributes(
        when (myNode.elementType) {
            NginxTypes.BLOCK -> Indent.getNormalIndent()
            NginxTypes.DIRECTIVE -> Indent.getContinuationIndent()
            else -> Indent.getNoneIndent()
        },
        null,
    )

    override fun isIncomplete() =
        myNode.elementType == NginxTypes.BLOCK && myNode.lastChildNode?.elementType != NginxTypes.RBRACE
}
