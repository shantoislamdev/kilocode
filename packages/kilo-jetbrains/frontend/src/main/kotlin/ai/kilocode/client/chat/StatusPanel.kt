package ai.kilocode.client.chat

import ai.kilocode.client.chat.model.SessionEvent
import ai.kilocode.client.chat.model.SessionModel
import ai.kilocode.client.chat.model.SessionModelListener
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.KiloWorkspaceStateDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import ai.kilocode.rpc.dto.ProfileStatusDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Welcome panel showing app + workspace initialization progress.
 *
 * Pure view — listens to [SessionModel] events and reads
 * [ChatModel][ai.kilocode.client.chat.model.ChatModel] for data.
 * No coroutines, no service references.
 *
 * Uses icon+label rows for each resource being loaded. Icons act as
 * status indicators: animated spinner for loading, green check for
 * success, red circle for error, grey circle for idle.
 */
class StatusPanel(
    parent: Disposable,
    private val model: SessionModel,
) : JPanel(GridBagLayout()), SessionModelListener, Disposable {

    init {
        Disposer.register(parent, this)
    }

    // ------ status icons ------

    private val iconLoading: Icon = AnimatedIcon.Default()
    private val iconOk: Icon = AllIcons.RunConfigurations.TestPassed
    private val iconError: Icon = AllIcons.RunConfigurations.TestFailed
    private val iconWarn: Icon = AllIcons.General.Warning
    private val iconIdle: Icon = AllIcons.RunConfigurations.TestNotRan

    // ------ header ------

    private val logo = JBLabel(
        IconLoader.getIcon("/icons/kilo-content.svg", StatusPanel::class.java),
    ).apply {
        alignmentX = CENTER_ALIGNMENT
    }

    private val status = JBLabel().apply {
        alignmentX = CENTER_ALIGNMENT
        horizontalAlignment = SwingConstants.CENTER
        font = JBUI.Fonts.label(13f)
        foreground = UIUtil.getLabelForeground()
    }

    // ------ app rows ------

    private val configRow = row("Config")
    private val notifRow = row("Notifications")
    private val profileRow = row("Profile")

    // ------ workspace rows ------

    private val providersRow = row("Providers")
    private val agentsRow = row("Agents")
    private val commandsRow = row("Commands")
    private val skillsRow = row("Skills")

    // ------ section headers ------

    private val appHeader = header("App")
    private val wsHeader = header("Workspace")

    private val appSection = section(appHeader, configRow, notifRow, profileRow)
    private val wsSection = section(wsHeader, providersRow, agentsRow, commandsRow, skillsRow)

    init {
        isOpaque = false

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(12, 16)

            add(logo)
            add(Box.createVerticalStrut(JBUI.scale(12)))
            add(status)
            add(Box.createVerticalStrut(JBUI.scale(12)))
            add(appSection)
            add(Box.createVerticalStrut(JBUI.scale(12)))
            add(wsSection)
        }

        add(body, GridBagConstraints())

        resetAll()
        model.addListener(this, this)
    }

    override fun onEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.AppChanged -> {
                renderApp(model.chat.app)
                revalidate()
                repaint()
            }

            is SessionEvent.WorkspaceChanged -> {
                renderWorkspace(model.chat.workspace)
                revalidate()
                repaint()
            }

            else -> {}
        }
    }

    // ------ rendering ------

    private fun renderApp(state: KiloAppStateDto) {
        status.text = title(state)

        when (state.status) {
            KiloAppStatusDto.DISCONNECTED -> {
                resetAll()
            }
            KiloAppStatusDto.CONNECTING -> {
                configRow.loading()
                notifRow.loading()
                profileRow.loading()
            }
            KiloAppStatusDto.LOADING -> {
                val p = state.progress
                if (p != null) {
                    if (p.config) configRow.ok("Config") else configRow.loading()
                    if (p.notifications) notifRow.ok("Notifications") else notifRow.loading()
                    renderProfile(p.profile)
                }
            }
            KiloAppStatusDto.READY -> {
                val p = state.progress
                if (p != null) {
                    configRow.ok("Config")
                    notifRow.ok("Notifications")
                    renderProfile(p.profile)
                } else {
                    configRow.ok("Config")
                    notifRow.ok("Notifications")
                    profileRow.ok("Logged in")
                }
            }
            KiloAppStatusDto.ERROR -> {
                val errors = state.errors.associate { it.resource to it }
                configRow.apply {
                    if ("config" in errors) error("Config: ${errors["config"]?.detail ?: "failed"}")
                    else ok("Config")
                }
                notifRow.apply {
                    if ("notifications" in errors) error("Notifications: ${errors["notifications"]?.detail ?: "failed"}")
                    else ok("Notifications")
                }
                profileRow.apply {
                    if ("profile" in errors) error("Profile: ${errors["profile"]?.detail ?: "failed"}")
                    else ok("Logged in")
                }
            }
        }
    }

    private fun renderWorkspace(state: KiloWorkspaceStateDto) {
        val appReady = model.chat.app.status == KiloAppStatusDto.READY
        val visible = appReady || state.status != KiloWorkspaceStatusDto.PENDING
        wsSection.isVisible = visible
        if (!visible) return

        when (state.status) {
            KiloWorkspaceStatusDto.PENDING -> {
                providersRow.idle("Providers")
                agentsRow.idle("Agents")
                commandsRow.idle("Commands")
                skillsRow.idle("Skills")
            }
            KiloWorkspaceStatusDto.LOADING -> {
                val p = state.progress
                if (p != null) {
                    if (p.providers) providersRow.ok("Providers") else providersRow.loading()
                    if (p.agents) agentsRow.ok("Agents") else agentsRow.loading()
                    if (p.commands) commandsRow.ok("Commands") else commandsRow.loading()
                    if (p.skills) skillsRow.ok("Skills") else skillsRow.loading()
                } else {
                    providersRow.loading()
                    agentsRow.loading()
                    commandsRow.loading()
                    skillsRow.loading()
                }
            }
            KiloWorkspaceStatusDto.READY -> {
                val prov = state.providers?.providers?.size ?: 0
                val ag = state.agents?.all?.size ?: 0
                val cmd = state.commands.size
                val sk = state.skills.size
                providersRow.ok("Providers ($prov)")
                agentsRow.ok("Agents ($ag)")
                commandsRow.ok("Commands ($cmd)")
                skillsRow.ok("Skills ($sk)")
            }
            KiloWorkspaceStatusDto.ERROR -> {
                val msg = state.error ?: "Unknown error"
                providersRow.error(msg)
                agentsRow.idle("Agents")
                commandsRow.idle("Commands")
                skillsRow.idle("Skills")
            }
        }
    }

    // ------ helpers ------

    private fun title(state: KiloAppStateDto): String =
        when (state.status) {
            KiloAppStatusDto.DISCONNECTED -> KiloBundle.message("toolwindow.status.disconnected")
            KiloAppStatusDto.CONNECTING -> KiloBundle.message("toolwindow.status.connecting")
            KiloAppStatusDto.LOADING -> KiloBundle.message("toolwindow.status.loading")
            KiloAppStatusDto.READY -> {
                val ver = model.chat.version
                if (ver != null) "Connected (CLI $ver)" else KiloBundle.message("toolwindow.status.connected")
            }
            KiloAppStatusDto.ERROR -> KiloBundle.message(
                "toolwindow.status.error",
                state.error ?: KiloBundle.message("toolwindow.error.unknown"),
            )
        }

    private fun renderProfile(profile: ProfileStatusDto) {
        when (profile) {
            ProfileStatusDto.LOADED -> profileRow.ok("Logged in")
            ProfileStatusDto.NOT_LOGGED_IN -> profileRow.warn("Not logged in")
            ProfileStatusDto.PENDING -> profileRow.loading("Profile")
        }
    }

    private fun resetAll() {
        configRow.idle("Config")
        notifRow.idle("Notifications")
        profileRow.idle("Profile")
        providersRow.idle("Providers")
        agentsRow.idle("Agents")
        commandsRow.idle("Commands")
        skillsRow.idle("Skills")
    }

    // ------ row factory ------

    private fun row(text: String): StatusRow = StatusRow(text, iconIdle)

    private fun header(text: String): JBLabel = JBLabel(text).apply {
        alignmentX = LEFT_ALIGNMENT
        font = JBUI.Fonts.label().deriveFont(JBUI.Fonts.label().style or java.awt.Font.BOLD)
        foreground = UIUtil.getLabelForeground()
        border = JBUI.Borders.empty(0, 0, 4, 0)
    }

    private fun section(hdr: JBLabel, vararg rows: StatusRow): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = CENTER_ALIGNMENT
        add(hdr)
        for (r in rows) add(r.label)
    }

    inner class StatusRow(text: String, icon: Icon) {
        val label = JBLabel(text, icon, SwingConstants.LEFT).apply {
            font = JBUI.Fonts.label()
            foreground = UIUtil.getContextHelpForeground()
            iconTextGap = JBUI.scale(6)
            border = JBUI.Borders.empty(2, 0)
            alignmentX = LEFT_ALIGNMENT
        }

        fun ok(msg: String, ic: Icon = iconOk) {
            label.icon = ic
            label.text = msg
            label.foreground = UIUtil.getContextHelpForeground()
        }

        fun loading(msg: String = label.text) {
            label.icon = iconLoading
            label.text = msg
            label.foreground = UIUtil.getContextHelpForeground()
        }

        fun warn(msg: String) {
            label.icon = iconWarn
            label.text = msg
            label.foreground = UIUtil.getContextHelpForeground()
        }

        fun error(msg: String) {
            label.icon = iconError
            label.text = msg
            label.foreground = UIUtil.getErrorForeground()
        }

        fun idle(msg: String) {
            label.icon = iconIdle
            label.text = msg
            label.foreground = UIUtil.getContextHelpForeground()
        }
    }

    override fun dispose() {
        // Listener auto-removed by Disposer (registered in init via addListener)
    }
}
