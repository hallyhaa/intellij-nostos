package org.babelserver.intellijnostos.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions

class NostosRunConfigurationOptions : LocatableRunConfigurationOptions() {

    private val myScriptPath = string("").provideDelegate(this, "scriptPath")
    private val myNostosExecutable = string("").provideDelegate(this, "nostosExecutable")
    private val myArguments = string("").provideDelegate(this, "arguments")
    private val myWorkingDirectory = string("").provideDelegate(this, "workingDirectory")

    var scriptPath: String
        get() = myScriptPath.getValue(this) ?: ""
        set(value) = myScriptPath.setValue(this, value)

    var nostosExecutable: String
        get() = myNostosExecutable.getValue(this) ?: ""
        set(value) = myNostosExecutable.setValue(this, value)

    var arguments: String
        get() = myArguments.getValue(this) ?: ""
        set(value) = myArguments.setValue(this, value)

    var workingDirectory: String
        get() = myWorkingDirectory.getValue(this) ?: ""
        set(value) = myWorkingDirectory.setValue(this, value)
}
