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

package io.zenoh

import io.zenoh.connectivity.TransportEvent
import io.zenoh.connectivity.LinkEvent
import io.zenoh.sample.SampleKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConnectivityTest {

    companion object {
        private fun listenerConfig(port: Int) = Config.fromJson(
            """{ mode: "peer", listen: { endpoints: ["tcp/localhost:$port"] } }"""
        ).getOrThrow()

        private fun connectConfig(port: Int) = Config.fromJson(
            """{ mode: "peer", connect: { endpoints: ["tcp/localhost:$port"] } }"""
        ).getOrThrow()
    }

    @Test
    fun `transports list is non-empty when connected`() {
        val port = 7465
        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        Thread.sleep(500)

        val transports = sessionA.info().transports().getOrThrow()
        assertTrue(transports.isNotEmpty(), "Expected at least one transport")

        val zids = transports.map { it.zid }
        val zidB = sessionB.info().zid().getOrThrow()
        assertTrue(zids.contains(zidB), "Expected to see peer B's ZID in transports")

        sessionB.close()
        sessionA.close()
    }

    @Test
    fun `links list is non-empty when connected`() {
        val port = 7466
        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        Thread.sleep(500)

        val links = sessionA.info().links().getOrThrow()
        assertTrue(links.isNotEmpty(), "Expected at least one link")

        val link = links.first()
        assertTrue(link.src.isNotEmpty(), "Link src should not be empty")
        assertTrue(link.dst.isNotEmpty(), "Link dst should not be empty")

        sessionB.close()
        sessionA.close()
    }

    @Test
    fun `links list filtered by transport`() {
        val port = 7467
        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        Thread.sleep(500)

        val transports = sessionA.info().transports().getOrThrow()
        assertTrue(transports.isNotEmpty(), "Expected at least one transport")

        val transport = transports.first()
        val filteredLinks = sessionA.info().links(transport).getOrThrow()
        assertTrue(filteredLinks.isNotEmpty(), "Expected at least one link for transport filter")

        // All links should belong to the filtered transport
        filteredLinks.forEach { link ->
            assertEquals(transport.zid, link.zid, "Link's zid should match transport's zid")
        }

        sessionB.close()
        sessionA.close()
    }

    @Test
    fun `transport events listener receives PUT on connect and DELETE on disconnect`() = runBlocking {
        val port = 7468
        val channel = Channel<TransportEvent>(10)

        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        val listener = sessionA.info().declareTransportEventsListener(channel).getOrThrow()

        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        // Should receive PUT event when B connects
        val putEvent = withTimeoutOrNull(3000) { channel.receive() }
        assertNotNull(putEvent, "Expected PUT event on connect")
        assertEquals(SampleKind.PUT, putEvent.kind)

        sessionB.close()

        // Should receive DELETE event when B disconnects
        val deleteEvent = withTimeoutOrNull(3000) { channel.receive() }
        assertNotNull(deleteEvent, "Expected DELETE event on disconnect")
        assertEquals(SampleKind.DELETE, deleteEvent.kind)

        listener.close()
        sessionA.close()
        channel.close().let { }
    }

    @Test
    fun `transport events listener with history delivers existing transport`() = runBlocking {
        val port = 7469
        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        Thread.sleep(500)

        val channel = Channel<TransportEvent>(10)
        val listener = sessionA.info().declareTransportEventsListener(channel, history = true).getOrThrow()

        // History should deliver the existing transport as PUT immediately
        val historyEvent = withTimeoutOrNull(3000) { channel.receive() }
        assertNotNull(historyEvent, "Expected history PUT event")
        assertEquals(SampleKind.PUT, historyEvent.kind)

        listener.close()
        sessionB.close()
        sessionA.close()
        channel.close().let { }
    }

    @Test
    fun `link events listener receives PUT on connect and DELETE on disconnect`() = runBlocking {
        val port = 7470
        val channel = Channel<LinkEvent>(10)

        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        val listener = sessionA.info().declareLinkEventsListener(channel).getOrThrow()

        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        // Should receive PUT event when link is established
        val putEvent = withTimeoutOrNull(3000) { channel.receive() }
        assertNotNull(putEvent, "Expected PUT event for link")
        assertEquals(SampleKind.PUT, putEvent.kind)
        assertTrue(putEvent.link.src.isNotEmpty())
        assertTrue(putEvent.link.dst.isNotEmpty())

        sessionB.close()

        // Should receive DELETE event when link is removed
        val deleteEvent = withTimeoutOrNull(3000) { channel.receive() }
        assertNotNull(deleteEvent, "Expected DELETE event for link")
        assertEquals(SampleKind.DELETE, deleteEvent.kind)

        listener.close()
        sessionA.close()
        channel.close().let { }
    }

    @Test
    fun `link events listener with history and transport filter`() = runBlocking {
        val port = 7471
        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        Thread.sleep(500)

        val transports = sessionA.info().transports().getOrThrow()
        assertTrue(transports.isNotEmpty())
        val transport = transports.first()

        val channel = Channel<LinkEvent>(10)
        val listener = sessionA.info().declareLinkEventsListener(
            channel, history = true, transport = transport
        ).getOrThrow()

        // History should deliver existing links for the filtered transport
        val historyEvent = withTimeoutOrNull(3000) { channel.receive() }
        assertNotNull(historyEvent, "Expected history PUT event for link")
        assertEquals(SampleKind.PUT, historyEvent.kind)
        assertEquals(transport.zid, historyEvent.link.zid)

        listener.close()
        sessionB.close()
        sessionA.close()
        channel.close().let { }
    }

    @Test
    fun `background transport events listener fires callback`() {
        val port = 7472
        val events = mutableListOf<TransportEvent>()
        val latch = java.util.concurrent.CountDownLatch(1)

        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        sessionA.info().declareBackgroundTransportEventsListener(
            callback = { event ->
                if (event.kind == SampleKind.PUT) {
                    events.add(event)
                    latch.countDown()
                }
            }
        ).getOrThrow()

        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        assertTrue(latch.await(3, java.util.concurrent.TimeUnit.SECONDS), "Background listener should fire")
        assertTrue(events.isNotEmpty())

        sessionB.close()
        sessionA.close()
    }

    @Test
    fun `background link events listener fires callback`() {
        val port = 7473
        val events = mutableListOf<LinkEvent>()
        val latch = java.util.concurrent.CountDownLatch(1)

        val sessionA = Zenoh.open(listenerConfig(port)).getOrThrow()
        sessionA.info().declareBackgroundLinkEventsListener(
            callback = { event ->
                if (event.kind == SampleKind.PUT) {
                    events.add(event)
                    latch.countDown()
                }
            }
        ).getOrThrow()

        val sessionB = Zenoh.open(connectConfig(port)).getOrThrow()

        assertTrue(latch.await(3, java.util.concurrent.TimeUnit.SECONDS), "Background link listener should fire")
        assertTrue(events.isNotEmpty())

        sessionB.close()
        sessionA.close()
    }
}
