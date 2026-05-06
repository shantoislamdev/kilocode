package ai.kilocode.client.session.history

sealed class HistoryModelEvent {
    data object LocalLoading : HistoryModelEvent()
    data class LocalLoaded(val count: Int) : HistoryModelEvent() {
        override fun toString() = "LocalLoaded count=$count"
    }
    data class CloudLoading(val reset: Boolean) : HistoryModelEvent() {
        override fun toString() = "CloudLoading reset=$reset"
    }
    data class CloudLoaded(val count: Int, val nextCursor: String?) : HistoryModelEvent() {
        override fun toString() = "CloudLoaded count=$count next=$nextCursor"
    }
    data class DeleteStarted(val id: String) : HistoryModelEvent() {
        override fun toString() = "DeleteStarted $id"
    }
    data class Deleted(val id: String) : HistoryModelEvent() {
        override fun toString() = "Deleted $id"
    }
    data class Error(val source: HistorySource, val message: String) : HistoryModelEvent() {
        override fun toString() = "Error $source $message"
    }
    data class SourceChanged(val source: HistorySource) : HistoryModelEvent() {
        override fun toString() = "SourceChanged $source"
    }

    fun interface Listener {
        fun onEvent(event: HistoryModelEvent)
    }
}
