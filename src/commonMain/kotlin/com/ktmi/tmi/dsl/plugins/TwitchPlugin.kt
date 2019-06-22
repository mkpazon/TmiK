package com.ktmi.tmi.dsl.plugins

import com.ktmi.irc.IrcState
import com.ktmi.tmi.messages.TwitchMessage

/** Pluggable object that transforms or filters incoming and/or outgoing messages */
interface TwitchPlugin {

    fun filterIncoming(message: TwitchMessage): Boolean
            = true

    fun filterOutgoing(message: String): Boolean
            = true

    fun mapIncoming(message: TwitchMessage): TwitchMessage
            = message

    fun mapOutgoing(message: String): String
            = message

    fun onConnectionStateChange(state: IrcState) { }
}