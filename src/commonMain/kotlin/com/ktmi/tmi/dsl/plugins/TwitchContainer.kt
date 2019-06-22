package com.ktmi.tmi.dsl.plugins

import com.ktmi.irc.IrcState
import com.ktmi.tmi.dsl.builder.IrcStateProvider
import com.ktmi.tmi.dsl.builder.TwitchDsl
import com.ktmi.tmi.dsl.builder.TwitchScope
import com.ktmi.tmi.messages.TwitchMessage
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Scope responsible for containing all inside it and control all the [TwitchMessage] traffic.
 * It's modifiable with [TwitchPlugin]s which can add plu&play functionality
 * @param parent parent scope where messages are forwarded and from  where main [Flow] of [TwitchMessage]s is retrieved
 * @param context [CoroutineContext] used for creating [TwitchMessage] listeners
 * @throws NoIrcStateHandlerException when ircStateFlow is not supplied and no parent is IrcStateProvider
 */
class TwitchContainer(
    parent: TwitchScope,
    context: CoroutineContext,
    ircStateFlow: Flow<IrcState>? = null
) : TwitchScope(parent, context + CoroutineName("Container")),
    IrcStateProvider {

    private val ircState: Flow<IrcState>
    private val plugins = mutableListOf<TwitchPlugin>()

    init {
        ircState = ircStateFlow ?: run {
            var currentScope: TwitchScope? = this.parent

            // Find parent who implements IrcStateProvider
            while (currentScope != null && currentScope !is IrcStateProvider) {
                currentScope = currentScope.parent
            }

            if (currentScope == null || currentScope !is IrcStateProvider)
                throw NoIrcStateHandlerException()

            currentScope.getIrcStateFlow()
        }

        launch { ircState.collect {
            for (plugin in plugins)
                plugin.onConnectionStateChange(it)
        } }
    }

    override fun getIrcStateFlow(): Flow<IrcState> = ircState

    override suspend fun getTwitchFlow(): Flow<TwitchMessage> = super.getTwitchFlow()
        .filter { message ->
            plugins.all { it.filterIncoming(message) }
        }.map {
            var message = it

            for (plugin in plugins)
                message = plugin.mapIncoming(message)

            message
        }

    override suspend fun sendRaw(message: String) {
        if (!plugins.all { it.filterOutgoing(message) })
            return

        var finalMessage = message
        for (plugin in plugins)
            finalMessage = plugin.mapOutgoing(finalMessage)

        super.sendRaw(finalMessage)
    }

    operator fun TwitchPlugin.unaryPlus() {
        plugins.add(this)
    }
}

@TwitchDsl
inline fun TwitchScope.container(block: TwitchContainer.() -> Unit) =
    TwitchContainer(this, coroutineContext).apply(block)

/** Thrown when no parent is IrcStateProvider */
class NoIrcStateHandlerException : Exception("No parent of TwitchContainer provides IrcState (implements IrcStateProvider)")