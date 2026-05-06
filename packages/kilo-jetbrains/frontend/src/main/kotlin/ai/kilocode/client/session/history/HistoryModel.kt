package ai.kilocode.client.session.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

class HistoryModel {
    private val listeners = mutableListOf<HistoryModelEvent.Listener>()

    var source = HistorySource.LOCAL
        private set
    var local = emptyList<HistoryItem>()
        private set
    var cloud = emptyList<HistoryItem>()
        private set
    var localLoading = false
        private set
    var cloudLoading = false
        private set
    var localError: String? = null
        private set
    var cloudError: String? = null
        private set
    var cursor: String? = null
        private set

    fun addListener(parent: Disposable, listener: HistoryModelEvent.Listener) {
        listeners.add(listener)
        Disposer.register(parent) { listeners.remove(listener) }
    }

    fun items(): List<HistoryItem> = if (source == HistorySource.LOCAL) local else cloud

    fun select(value: HistorySource) {
        if (source == value) return
        source = value
        fire(HistoryModelEvent.SourceChanged(value))
    }

    fun startLocal() {
        localLoading = true
        localError = null
        fire(HistoryModelEvent.LocalLoading)
    }

    fun setLocal(items: List<HistoryItem>) {
        local = items
        localLoading = false
        localError = null
        fire(HistoryModelEvent.LocalLoaded(items.size))
    }

    fun startCloud(reset: Boolean) {
        cloudLoading = true
        cloudError = null
        if (reset) {
            cloud = emptyList()
            cursor = null
        }
        fire(HistoryModelEvent.CloudLoading(reset))
    }

    fun setCloud(items: List<HistoryItem>, next: String?, append: Boolean) {
        cloud = if (append) cloud + items else items
        cursor = next
        cloudLoading = false
        cloudError = null
        fire(HistoryModelEvent.CloudLoaded(items.size, next))
    }

    fun startDelete(id: String) {
        fire(HistoryModelEvent.DeleteStarted(id))
    }

    fun deleted(id: String) {
        local = local.filterNot { it.id == id }
        fire(HistoryModelEvent.Deleted(id))
    }

    fun error(source: HistorySource, message: String) {
        if (source == HistorySource.LOCAL) {
            localLoading = false
            localError = message
        } else {
            cloudLoading = false
            cloudError = message
        }
        fire(HistoryModelEvent.Error(source, message))
    }

    private fun fire(event: HistoryModelEvent) {
        for (listener in listeners) listener.onEvent(event)
    }
}
