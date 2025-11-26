package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.generator.JsonGenerator
import com.github.jeraxxxxxxx.httpmate.generator.MockJsonGenerator

class GenerateMockJsonAction : BaseGenerateJsonAction() {
    override fun getGenerator(): JsonGenerator {
        return MockJsonGenerator()
    }
}
