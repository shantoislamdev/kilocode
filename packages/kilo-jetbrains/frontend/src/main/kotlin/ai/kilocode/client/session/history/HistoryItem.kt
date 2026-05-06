package ai.kilocode.client.session.history

import ai.kilocode.rpc.dto.SessionDto

enum class HistorySource { LOCAL, CLOUD }

data class HistoryItem(
    val id: String,
    val source: HistorySource,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val directory: String? = null,
    val local: SessionDto? = null,
)
