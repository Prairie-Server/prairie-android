package com.continuum.app.android.ui.screens.reader.reflow

import org.json.JSONObject

sealed interface ReflowEvent {
    object Ready : ReflowEvent
    data class Paginated(val pageCount: Int) : ReflowEvent
    data class Relocated(val page: Int, val pageProgression: Double) : ReflowEvent
    data class Error(val message: String) : ReflowEvent
}

sealed interface ReflowCommand {
    data class Load(val html: String, val baseUrl: String) : ReflowCommand
    data class GoToPage(val page: Int) : ReflowCommand
    data class ApplyStyle(val css: String) : ReflowCommand
}

fun decodeReflowEvent(message: String): ReflowEvent? = runCatching {
    val o = JSONObject(message)
    when (o.getString("type")) {
        "ready" -> ReflowEvent.Ready
        "paginated" -> ReflowEvent.Paginated(o.getInt("pageCount"))
        "relocated" -> ReflowEvent.Relocated(o.getInt("page"), o.getDouble("pageProgression"))
        "error" -> ReflowEvent.Error(o.optString("message"))
        else -> null
    }
}.getOrNull()

fun encodeReflowCommand(cmd: ReflowCommand): String = when (cmd) {
    is ReflowCommand.Load -> JSONObject()
        .put("type", "load").put("html", cmd.html).put("baseUrl", cmd.baseUrl).toString()
    is ReflowCommand.GoToPage -> JSONObject().put("type", "goToPage").put("page", cmd.page).toString()
    is ReflowCommand.ApplyStyle -> JSONObject().put("type", "applyStyle").put("css", cmd.css).toString()
}
