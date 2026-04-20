//
// Copyright (c) 2026 ZettaScale Technology
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

package io.zenoh.handlers

import io.zenoh.annotations.Unstable
import io.zenoh.connectivity.LinkEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

/**
 * Channel handler for [LinkEvent] events.
 *
 * Implementation of a [LinkEventsHandler] with a [Channel] receiver.
 */
@Unstable
internal class LinkEventsChannelHandler(private val channel: Channel<LinkEvent>) :
    LinkEventsHandler<Channel<LinkEvent>> {

    override fun handle(event: LinkEvent) {
        runBlocking { channel.send(event) }
    }

    override fun receiver(): Channel<LinkEvent> {
        return channel
    }

    override fun onClose() {
        channel.close()
    }
}
