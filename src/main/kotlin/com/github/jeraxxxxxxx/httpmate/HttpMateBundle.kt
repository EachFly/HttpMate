package com.github.jeraxxxxxxx.httpmate

import com.intellij.AbstractBundle
import org.jetbrains.annotations.PropertyKey

private const val HTTP_MATE_BUNDLE = "messages.MyBundle"

object HttpMateBundle : AbstractBundle(HTTP_MATE_BUNDLE) {

    fun message(@PropertyKey(resourceBundle = HTTP_MATE_BUNDLE) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
