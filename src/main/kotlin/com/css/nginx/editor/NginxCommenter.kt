package com.css.nginx.editor

import com.intellij.lang.Commenter

class NginxCommenter : Commenter {
    override fun getLineCommentPrefix() = "#"
    override fun getBlockCommentPrefix() = null
    override fun getBlockCommentSuffix() = null
    override fun getCommentedBlockCommentPrefix() = null
    override fun getCommentedBlockCommentSuffix() = null
}
