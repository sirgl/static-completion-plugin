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
import com.intellij.psi.util.InheritanceUtil.*
import com.intellij.util.ProcessingContext
import com.siyeh.ig.psiutils.ImportUtils
import sirgl.config.completionSettings


class JavaStaticCompletionContributor : CompletionContributor() {
    private val element = PsiJavaPatterns.psiElement()

    init {
        extend(CompletionType.BASIC, element, JavaStaticMethodPostfixProvider())
    }
}

class JavaStaticMethodPostfixProvider : CompletionProvider<CompletionParameters>() {
    private val methodParamIndex = JavaMethodParameterTypesIndex.getInstance()

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        val project = parameters.editor.project ?: return
        // TODO It is better to send notification through message bus, that it has been changed
        val completionTimes = project.completionSettings.internalState.completionTimes
        if (parameters.invocationCount < completionTimes) return
        parameters.withInvocationCount(2)
        val ref = parameters.position.containingFile.findReferenceAt(parameters.offset) ?: return
        val element = ref.element as? PsiReferenceExpression ?: return
        val type = element.qualifierExpression?.type ?: return


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

    private fun getStaticFunctionsWithFirstArgumentType(type: PsiType, project: Project, place: PsiElement): Collection<PsiMethod> {
        val refType = type as? PsiClassType
        val set = HashSet<PsiMethod>()
        if (refType == null) {
            return emptyList()
        }
        val clazz = refType.resolve() ?: return emptyList()
        processSupers(clazz, true, { currentClass ->
            if(currentClass.qualifiedName?.equals(CommonClassNames.JAVA_LANG_OBJECT) != true) {
                set.addAll(methodParamIndex[currentClass.name ?: "", project, GlobalSearchScope.allScope(project)]
                        .filter { it.hasModifierProperty("static") })
            }
            true
        })
        return set.filter {  isSuitable(method = it, receiverType = type, place = place) }
    }

    private val insertHandler = StaticMethodInsertHandler()

    private fun createLookupElement(method: PsiMethod): LookupElement =
            PrioritizedLookupElement.withPriority(LookupElementBuilder.create(method)
                    .withIcon(AllIcons.Nodes.Method)
                    .withPresentableText(method.containingClass?.name + "." + method.name + "(" + getParameterListText(method.parameterList) + ")")
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