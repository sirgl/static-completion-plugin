package sirgl.config

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import javax.swing.JList

class JavaStaticCompletionConfigurable(
        private val project: Project
) : Configurable {
    private val completionTimesSpinner = JBIntSpinner(3, 1, 5)
    private val completionClassesList = JBList<String>(listOf("gfghfhg"))
    private val decorator = ToolbarDecorator.createDecorator(completionClassesList)

    override fun isModified(): Boolean {
        return completionTimesSpinner.number != project.completionSettings.state.completionTimes
    }

    override fun getDisplayName(): String {
        return "Java Static Completion"
    }

    override fun apply() {
        project.completionSettings.internalState = PersistentCompletionConfig.State(completionTimesSpinner.number)
    }

    override fun createComponent() = panel {
        row(Label("Completion times"), false, { completionTimesSpinner() })
//        row(Label("Classes to complete from"), false, { completionClassesList() })
        row(Label("decorator"), false, { decorator.createPanel() })
    }

}