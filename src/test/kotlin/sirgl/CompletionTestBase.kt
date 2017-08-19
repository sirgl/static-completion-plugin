package sirgl

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

open class CompletionTestBase : LightCodeInsightFixtureTestCase() {
    fun `test `() {
        doSingleCompletion("", "")
    }

    inner class InlineFile(private @Language("Java") val code: String, val name: String = "Main.java") {
        private val hasCaretMarker = "/*caret*/" in code

        init {
            myFixture.configureByText(name, replaceCaretMarker(code))
        }

        fun withCaret() {
            check(hasCaretMarker) {
                "Please, add `/*caret*/` marker to\n$code"
            }
        }

    }

    fun hasCaretMarker(text: String): Boolean = text.contains("/*caret*/")


    protected fun checkByText(
            @Language("Rust") before: String,
            @Language("Rust") after: String,
            action: () -> Unit
    ) {
        InlineFile(before)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected fun doSingleCompletion(@Language("Java") before: String, @Language("Java") after: String) {
        check(hasCaretMarker(before) && hasCaretMarker(after)) {
            "Please add `/*caret*/` marker"
        }
        checkByText(before, after) { executeSoloCompletion() }
    }

    protected fun replaceCaretMarker(text: String) = text.replace("/*caret*/", "<caret>")

    protected fun checkSingleCompletion(target: String, @Language("Java") code: String) {
        InlineFile(code).withCaret()
        executeSoloCompletion()

        val normName = target
                .substringBeforeLast("()")
                .substringBeforeLast(" {}")
                .substringAfterLast("::")
                .substringAfterLast(".")

        val shift = when {
            target.endsWith("()") || target.endsWith("::") -> 3
            target.endsWith(" {}") -> 4
            else -> 2
        }
        val element = myFixture.file.findElementAt(myFixture.caretOffset - shift)!!
        val skipTextCheck = normName.isEmpty() || normName.contains(' ')
        check((skipTextCheck || element.text == normName) && (element.fitsHierarchically(target) || element.fitsLinearly(target))) {
            "Wrong completion, expected `$target`, but got\n${myFixture.file.text}"
        }
    }

    protected fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()

        if (variants != null) {
            fun LookupElement.debug(): String = "$lookupString ($psiElement)"
            error("Expected a single completion, but got ${variants.size}\n"
                    + variants.map { it.debug() }.joinToString("\n"))
        }
    }

    private fun PsiElement.fitsHierarchically(target: String): Boolean = when {
        text == target -> true
        text.length > target.length -> false
        parent != null -> parent.fitsHierarchically(target)
        else -> false
    }

    private fun PsiElement.fitsLinearly(target: String) =
            checkLinearly(target, Direction.LEFT) || checkLinearly(target, Direction.RIGHT)

    private fun PsiElement.checkLinearly(target: String, direction: Direction): Boolean {
        var el = this
        var text = ""
        while (text.length < target.length) {
            text = if (direction == Direction.LEFT) el.text + text else text + el.text
            if (text == target) return true
            el = (if (direction == Direction.LEFT) PsiTreeUtil.prevVisibleLeaf(el) else PsiTreeUtil.nextVisibleLeaf(el)) ?: break
        }
        return false
    }

    private enum class Direction {
        LEFT,
        RIGHT
    }
}