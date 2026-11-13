package com.css.nginx.inspection

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/** Runs `nginx -t` on nginx.conf when an nginx binary is on PATH and shows its errors on the reported lines. */
class NginxConfigTestAnnotator :
    ExternalAnnotator<NginxConfigTestAnnotator.Input, List<NginxConfigTestAnnotator.Problem>>() {
    data class Input(val nginx: String, val path: String)
    data class Problem(val line: Int, val severity: HighlightSeverity, val message: String)

    override fun collectInformation(file: PsiFile): Input? {
        // only the main file: included snippets can't be tested on their own
        if (file.name != "nginx.conf") return null
        val vf = file.virtualFile?.takeIf { it.isInLocalFileSystem } ?: return null
        // nginx -t loads load_module .so files and runs perl_require code: never for a project the user hasn't trusted
        if (!TrustedProjects.isProjectTrusted(file.project)) return null
        val nginx = PathEnvironmentVariableUtil.findInPath("nginx") ?: return null
        return Input(nginx.path, vf.path)
    }

    // shortcut: tests the saved file, so lines drift until the IDE autosaves; feed the document through a temp copy if that bothers users
    override fun doAnnotate(input: Input): List<Problem> {
        val out = try {
            ExecUtil.execAndGetOutput(GeneralCommandLine(input.nginx, "-t", "-q", "-c", input.path), 10_000)
        } catch (e: ExecutionException) {  // not executable, broken symlink...: no results rather than an IDE error
            thisLogger().info("nginx -t could not run: ${e.message}")
            return emptyList()
        }
        return parseNginxTestOutput(out.stderr, input.path)
    }

    override fun apply(file: PsiFile, problems: List<Problem>, holder: AnnotationHolder) {
        val doc = file.viewProvider.document ?: return
        for (p in problems) {
            if (p.line !in 1..doc.lineCount) continue
            val range = TextRange(doc.getLineStartOffset(p.line - 1), doc.getLineEndOffset(p.line - 1))
            holder.problem(p.severity, "nginx -t: ${p.message}").range(range).create()
        }
    }
}
