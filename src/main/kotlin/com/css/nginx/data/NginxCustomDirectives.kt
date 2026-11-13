package com.css.nginx.data

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Directive and variable names a project declares known (its own modules), stored in .idea/nginx.xml so the team shares them. */
@Service(Service.Level.PROJECT)
@State(name = "NginxCustomDirectives", storages = [Storage("nginx.xml")])
class NginxCustomDirectives : SimplePersistentStateComponent<NginxCustomDirectives.Names>(Names()) {
    class Names : BaseState() {
        var names by stringSet()
        var variables by stringSet()
    }

    val names: Set<String> get() = state.names
    val variables: Set<String> get() = state.variables

    fun add(name: String) = update { it.names.add(name) }

    fun addVariable(name: String) = update { it.variables.add(name) }

    private fun update(change: (Names) -> Unit) {
        change(state)
        state.intIncrementModificationCount()
    }
}
