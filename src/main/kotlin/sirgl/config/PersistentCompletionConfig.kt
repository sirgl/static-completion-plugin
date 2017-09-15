package sirgl.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project

@State(name = "StaticCompletionSettings")
class PersistentCompletionConfig  : PersistentStateComponent<PersistentCompletionConfig.State> {
    var internalState: State = State()

    override fun loadState(state: State) {
        this.internalState = state
    }

    override fun getState() = internalState

    data class State(
            var completionTimes: Int = 2
    )
}

val Project.completionSettings: PersistentCompletionConfig
    get() = ServiceManager.getService(this, PersistentCompletionConfig::class.java)