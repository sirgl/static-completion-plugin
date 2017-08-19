package sirgl

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.index.JavaMethodParameterTypesIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import com.siyeh.ig.psiutils.ImportUtils


class JavaStaticCompletionContributor : CompletionContributor() {
    private val element = PsiJavaPatterns.psiElement()

    init {
        extend(CompletionType.BASIC, element, JavaStaticMethodPostfixProvider())
    }
}

class JavaStaticMethodPostfixProvider : CompletionProvider<CompletionParameters>() {
    private val methodParamIndex = JavaMethodParameterTypesIndex.getInstance()

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        if (parameters.invocationCount < 2) return
        parameters.withInvocationCount(2)
        val ref = parameters.position.containingFile.findReferenceAt(parameters.offset) ?: return
        val element = ref.element as? PsiReferenceExpression ?: return
        val type = element.qualifierExpression?.type ?: return

        val project = parameters.editor.project ?: return

        //TODO settings
        //TODO priority?
        result.addAllElements(getStaticFunctionsWithFirstArgumentType(type, project, element).map { createLookupElement(it) })
    }

    private fun isSuitable(method: PsiMethod, receiverType: PsiType, place: PsiElement): Boolean {
        val parameters = method.parameterList.parameters
        if (parameters.isEmpty()) return false
        val targetParam = parameters[0]
        val type = targetParam.type
        val accessible = JavaPsiFacade.getInstance(method.project).resolveHelper.isAccessible(method, place, null)
        return accessible && type.isAssignableFrom(receiverType)
    }

    //TODO not only this type, but also all ancestors
    private fun getStaticFunctionsWithFirstArgumentType(type: PsiType, project: Project, place: PsiElement): Collection<PsiMethod> {
        val name = PsiNameHelper.getShortClassName(type.canonicalText)
        return methodParamIndex[name, project, GlobalSearchScope.allScope(project)]
                .filter { it.hasModifierProperty("static") && isSuitable(method = it, receiverType = type, place = place) }
    }

    private val insertHandler = StaticMethodInsertHandler()

    private fun createLookupElement(method: PsiMethod): LookupElement =
            PrioritizedLookupElement.withPriority(LookupElementBuilder.create(method)
                    .withIcon(AllIcons.Nodes.Method)
                    .withPresentableText(method.containingClass?.name + "." + method.name + "(" + getParameterListText(method.parameterList) + ")")
                    .strikeout()
                    .withInsertHandler(insertHandler), 0.001)

    private fun getParameterListText(parameterList: PsiParameterList) = parameterList.parameters.joinToString(separator = ", ")
    { ((it.type as? PsiClassType)?.className ?: it.type.presentableText) + " " + it.name }

}

class StaticMethodInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val file = context.file
        val element = file.findElementAt(context.startOffset) ?: return
        val ref = element.parent as? PsiReferenceExpression ?: return
        val qualifierExpression = ref.qualifierExpression ?: return
        val method = item.psiElement as? PsiMethod ?: return
        val methodName = method.name
        val containingClass = method.containingClass ?: return
        ImportUtils.addImportIfNeeded(containingClass, ref)
        val className = containingClass.qualifiedName ?: return
        val factory = JavaPsiFacade.getElementFactory(context.project)
        val manyParams = method.parameterList.parameters.size > 1
        val probablyComma = if (manyParams) ", " else ""
        val probablyQualifier = qualifierExpression.text
        val text = "$className.$methodName($probablyQualifier$probablyComma)"
        val expression = factory.createExpressionFromText(text, qualifierExpression)
        ref.replace(expression)
        if (manyParams) {
            context.commitDocument()
            EditorModificationUtil.moveCaretRelatively(context.editor, -1)
        } else {
            context.commitDocument()
        }
    }
}