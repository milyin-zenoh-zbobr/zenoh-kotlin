//
// Copyright (c) 2023 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

package io.zenoh.session

import io.zenoh.Session
import io.zenoh.annotations.Unstable
import io.zenoh.config.ZenohId
import io.zenoh.connectivity.Link
import io.zenoh.connectivity.LinkEvent
import io.zenoh.connectivity.LinkEventsListener
import io.zenoh.connectivity.Transport
import io.zenoh.connectivity.TransportEvent
import io.zenoh.connectivity.TransportEventsListener
import io.zenoh.handlers.LinkEventsCallback
import io.zenoh.handlers.LinkEventsChannelHandler
import io.zenoh.handlers.LinkEventsHandler
import io.zenoh.handlers.TransportEventsCallback
import io.zenoh.handlers.TransportEventsChannelHandler
import io.zenoh.handlers.TransportEventsHandler
import kotlinx.coroutines.channels.Channel

/**
 * Class allowing to obtain the information of a [Session].
 */
class SessionInfo(private val session: Session) {

    /**
     *  Return the [ZenohId] of the current Zenoh [Session]
     */
    fun zid(): Result<ZenohId> {
        return session.zid()
    }

    /**
     * Return the [ZenohId] of the zenoh peers the session is currently connected to.
     */
    fun peersZid(): Result<List<ZenohId>> {
        return session.getPeersId()
    }

    /**
     * Return the [ZenohId] of the zenoh routers the session is currently connected to.
     */
    fun routersZid(): Result<List<ZenohId>> {
        return session.getRoutersId()
    }

    /**
     * Return information about currently opened transport sessions.
     */
    @Unstable
    fun transports(): Result<List<Transport>> {
        return session.getTransports()
    }

    /**
     * Return information about links across all transports, optionally filtered by a specific [Transport].
     *
     * @param transport Optional [Transport] to filter links by. If null, all links are returned.
     */
    @Unstable
    fun links(transport: Transport? = null): Result<List<Link>> {
        return session.getLinks(transport)
    }

    // ---- Transport Events Listener ----

    /**
     * Declare a [TransportEventsListener] with a callback.
     *
     * @param callback Callback to run on each [TransportEvent].
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing transports before live events.
     */
    @Unstable
    fun declareTransportEventsListener(
        callback: TransportEventsCallback,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
    ): Result<TransportEventsListener> {
        val resolvedOnClose = fun() { onClose?.invoke() }
        return session.declareTransportEventsListener(callback, resolvedOnClose, history)
    }

    /**
     * Declare a [TransportEventsListener] with a handler.
     *
     * @param handler [TransportEventsHandler] to handle transport events.
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing transports before live events.
     */
    @Unstable
    fun <R> declareTransportEventsListener(
        handler: TransportEventsHandler<R>,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
    ): Result<TransportEventsListener> {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = TransportEventsCallback { event: TransportEvent -> handler.handle(event) }
        return session.declareTransportEventsListener(callback, resolvedOnClose, history)
    }

    /**
     * Declare a [TransportEventsListener] with a channel.
     *
     * @param channel [Channel] to pipe transport events into.
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing transports before live events.
     */
    @Unstable
    fun declareTransportEventsListener(
        channel: Channel<TransportEvent>,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
    ): Result<TransportEventsListener> {
        val channelHandler = TransportEventsChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = TransportEventsCallback { event: TransportEvent -> channelHandler.handle(event) }
        return session.declareTransportEventsListener(callback, resolvedOnClose, history)
    }

    /**
     * Declare a background [TransportEventsListener] with a callback.
     *
     * Background listeners are not bound to the Kotlin listener object lifecycle.
     *
     * @param callback Callback to run on each [TransportEvent].
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing transports before live events.
     */
    @Unstable
    fun declareBackgroundTransportEventsListener(
        callback: TransportEventsCallback,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
    ): Result<Unit> {
        val resolvedOnClose = fun() { onClose?.invoke() }
        return session.declareBackgroundTransportEventsListener(callback, resolvedOnClose, history)
    }

    /**
     * Declare a background [TransportEventsListener] with a handler.
     *
     * @param handler [TransportEventsHandler] to handle transport events.
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing transports before live events.
     */
    @Unstable
    fun <R> declareBackgroundTransportEventsListener(
        handler: TransportEventsHandler<R>,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
    ): Result<Unit> {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = TransportEventsCallback { event: TransportEvent -> handler.handle(event) }
        return session.declareBackgroundTransportEventsListener(callback, resolvedOnClose, history)
    }

    /**
     * Declare a background [TransportEventsListener] with a channel.
     *
     * @param channel [Channel] to pipe transport events into.
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing transports before live events.
     */
    @Unstable
    fun declareBackgroundTransportEventsListener(
        channel: Channel<TransportEvent>,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
    ): Result<Unit> {
        val channelHandler = TransportEventsChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = TransportEventsCallback { event: TransportEvent -> channelHandler.handle(event) }
        return session.declareBackgroundTransportEventsListener(callback, resolvedOnClose, history)
    }

    // ---- Link Events Listener ----

    /**
     * Declare a [LinkEventsListener] with a callback.
     *
     * @param callback Callback to run on each [LinkEvent].
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing links before live events.
     * @param transport Optional [Transport] to filter link events by.
     */
    @Unstable
    fun declareLinkEventsListener(
        callback: LinkEventsCallback,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
        transport: Transport? = null,
    ): Result<LinkEventsListener> {
        val resolvedOnClose = fun() { onClose?.invoke() }
        return session.declareLinkEventsListener(callback, resolvedOnClose, history, transport)
    }

    /**
     * Declare a [LinkEventsListener] with a handler.
     *
     * @param handler [LinkEventsHandler] to handle link events.
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing links before live events.
     * @param transport Optional [Transport] to filter link events by.
     */
    @Unstable
    fun <R> declareLinkEventsListener(
        handler: LinkEventsHandler<R>,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
        transport: Transport? = null,
    ): Result<LinkEventsListener> {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = LinkEventsCallback { event: LinkEvent -> handler.handle(event) }
        return session.declareLinkEventsListener(callback, resolvedOnClose, history, transport)
    }

    /**
     * Declare a [LinkEventsListener] with a channel.
     *
     * @param channel [Channel] to pipe link events into.
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing links before live events.
     * @param transport Optional [Transport] to filter link events by.
     */
    @Unstable
    fun declareLinkEventsListener(
        channel: Channel<LinkEvent>,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
        transport: Transport? = null,
    ): Result<LinkEventsListener> {
        val channelHandler = LinkEventsChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = LinkEventsCallback { event: LinkEvent -> channelHandler.handle(event) }
        return session.declareLinkEventsListener(callback, resolvedOnClose, history, transport)
    }

    /**
     * Declare a background [LinkEventsListener] with a callback.
     *
     * @param callback Callback to run on each [LinkEvent].
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing links before live events.
     * @param transport Optional [Transport] to filter link events by.
     */
    @Unstable
    fun declareBackgroundLinkEventsListener(
        callback: LinkEventsCallback,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
        transport: Transport? = null,
    ): Result<Unit> {
        val resolvedOnClose = fun() { onClose?.invoke() }
        return session.declareBackgroundLinkEventsListener(callback, resolvedOnClose, history, transport)
    }

    /**
     * Declare a background [LinkEventsListener] with a handler.
     *
     * @param handler [LinkEventsHandler] to handle link events.
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing links before live events.
     * @param transport Optional [Transport] to filter link events by.
     */
    @Unstable
    fun <R> declareBackgroundLinkEventsListener(
        handler: LinkEventsHandler<R>,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
        transport: Transport? = null,
    ): Result<Unit> {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = LinkEventsCallback { event: LinkEvent -> handler.handle(event) }
        return session.declareBackgroundLinkEventsListener(callback, resolvedOnClose, history, transport)
    }

    /**
     * Declare a background [LinkEventsListener] with a channel.
     *
     * @param channel [Channel] to pipe link events into.
     * @param onClose Optional callback invoked when the listener is closed.
     * @param history If true, deliver events for existing links before live events.
     * @param transport Optional [Transport] to filter link events by.
     */
    @Unstable
    fun declareBackgroundLinkEventsListener(
        channel: Channel<LinkEvent>,
        onClose: (() -> Unit)? = null,
        history: Boolean = false,
        transport: Transport? = null,
    ): Result<Unit> {
        val channelHandler = LinkEventsChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = LinkEventsCallback { event: LinkEvent -> channelHandler.handle(event) }
        return session.declareBackgroundLinkEventsListener(callback, resolvedOnClose, history, transport)
    }
}
