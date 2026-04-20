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

package io.zenoh.connectivity

import io.zenoh.annotations.Unstable
import io.zenoh.jni.JNITransportEventsListener
import io.zenoh.session.SessionDeclaration

/**
 * A listener for transport lifecycle events.
 *
 * Receives [TransportEvent] notifications when transports are opened or closed.
 * Call [undeclare] or [close] to stop receiving events.
 */
@Unstable
class TransportEventsListener internal constructor(
    private var jniTransportEventsListener: JNITransportEventsListener?,
) : SessionDeclaration, AutoCloseable {

    /**
     * Returns `true` if the listener is still running.
     */
    fun isValid(): Boolean {
        return jniTransportEventsListener != null
    }

    /**
     * Closes the listener. Equivalent to [undeclare].
     */
    override fun close() {
        undeclare()
    }

    /**
     * Undeclares the listener. Further operations will not be valid.
     */
    override fun undeclare() {
        jniTransportEventsListener?.close()
        jniTransportEventsListener = null
    }

    protected fun finalize() {
        undeclare()
    }
}
