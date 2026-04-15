package ai.kilocode.client.chat

import ai.kilocode.client.KiloAppService
import ai.kilocode.client.KiloProjectService
import ai.kilocode.client.KiloSessionService
import ai.kilocode.client.chat.model.SessionEvent
import ai.kilocode.client.chat.model.SessionModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

/**
 * Main chat panel — reacts to [SessionModel] events.
 *
 * Uses [CardLayout] in the center to switch between the empty panel
 * (shown before the first prompt) and the scrollable message list.
 *
 * All business logic (app/workspace watching, session lifecycle, event
 * handling, status computation) lives in [SessionModel]. Welcome
 * rendering lives in [StatusPanel]. This class handles layout, prompt
 * wiring, message list updates, card switching, picker population,
 * busy state, and scrolling.
 */
class ChatPanel(
    project: Project,
    app: KiloAppService,
    workspace: KiloProjectService,
    sessions: KiloSessionService,
    cs: CoroutineScope,
) : JPanel(BorderLayout()), Disposable {

    companion object {
        private const val WELCOME = "welcome"
        private const val MESSAGES = "messages"
    }

    private val model = SessionModel(this, sessions, workspace, app, cs)
    private val welcome = StatusPanel(this, model)
    private val messages = MessageListPanel()

    private val cards = CardLayout()
    private val center = JPanel(cards)

    private val scroll = JBScrollPane(messages).apply {
        border = JBUI.Borders.empty()
        verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val prompt = PromptPanel(
        project = project,
        onSend = { text -> send(text) },
        onAbort = { model.abort() },
    )

    init {
        // Layout
        center.add(welcome, WELCOME)
        center.add(scroll, MESSAGES)
        cards.show(center, WELCOME)

        add(center, BorderLayout.CENTER)
        add(prompt, BorderLayout.SOUTH)

        // Wire picker callbacks via typed model methods
        prompt.mode.onSelect = { item ->
            model.selectAgent(item.id)
        }
        prompt.model.onSelect = { item ->
            val group = item.group
            if (group != null) {
                model.selectModel(group, item.id)
            }
        }

        // React to model events — no coroutines, pure EDT
        model.addListener(this) { event ->
            when (event) {
                is SessionEvent.MessageAdded -> {
                    val msg = model.chat.message(event.id) ?: return@addListener
                    messages.addMessage(msg.info)
                    refreshMessages()
                }

                is SessionEvent.MessageRemoved -> {
                    messages.removeMessage(event.id)
                    refreshMessages()
                }

                is SessionEvent.PartUpdated -> {
                    val part = model.chat.part(event.messageId, event.partId) ?: return@addListener
                    messages.updatePartText(event.messageId, event.partId, part.text.toString())
                    refreshMessages()
                }

                is SessionEvent.PartDelta -> {
                    messages.appendDelta(event.messageId, event.partId, event.delta)
                    refreshMessages()
                }

                is SessionEvent.StatusChanged -> {
                    messages.setStatus(event.text)
                    refreshMessages()
                }

                is SessionEvent.Error -> {
                    messages.addError(event.message)
                    refreshMessages()
                }

                is SessionEvent.HistoryLoaded -> {
                    messages.clear()
                    for (msg in model.chat.messages()) {
                        messages.addMessage(msg.info)
                        for ((partId, part) in msg.parts) {
                            if (part.dto.type == "text" && part.text.isNotEmpty()) {
                                messages.updatePartText(msg.info.id, partId, part.text.toString())
                            }
                        }
                    }
                    refreshMessages()
                }

                is SessionEvent.Cleared -> {
                    messages.clear()
                    refreshMessages()
                }

                is SessionEvent.WorkspaceReady -> {
                    val c = model.chat
                    prompt.mode.setItems(
                        c.agents.map { LabelPicker.Item(it.name, it.display) },
                        c.agent,
                    )
                    prompt.model.setItems(
                        c.models.map { LabelPicker.Item(it.id, it.display, it.provider) },
                        c.model,
                    )
                    prompt.setReady(c.ready)
                }

                is SessionEvent.ViewChanged -> {
                    cards.show(center, if (event.show) MESSAGES else WELCOME)
                }

                is SessionEvent.BusyChanged -> {
                    prompt.setBusy(event.busy)
                }

                is SessionEvent.AppChanged,
                is SessionEvent.WorkspaceChanged -> {
                    // Handled by EmptyChatUi
                }
            }
        }
    }

    private fun send(text: String) {
        if (text.isBlank()) return
        model.prompt(text)
        prompt.clear()
    }

    private fun refreshMessages() {
        messages.revalidate()
        messages.repaint()
        scrollToBottom()
    }

    private fun scrollToBottom() {
        val bar = scroll.verticalScrollBar
        bar.value = bar.maximum
    }

    override fun dispose() {
        // All children (welcome, model) disposed by Disposer
    }
}
