package org.babelserver.intellijnostos.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NotNullLazyValue

class NostosConfigurationType : ConfigurationTypeBase(
    ID,
    "Nostos",
    "Run a Nostos program",
    NotNullLazyValue.createValue {
        IconLoader.getIcon("/icons/nostos.svg", NostosConfigurationType::class.java)
    }
) {
    init {
        addFactory(NostosConfigurationFactory(this))
    }

    companion object {
        const val ID = "NostosRunConfiguration"

        fun getInstance(): NostosConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(NostosConfigurationType::class.java)
    }
}
