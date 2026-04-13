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

package io.zenoh.jni

import io.zenoh.connectivity.TransportEventsListener

/**
 * Adapter class to handle the interactions with Zenoh through JNI for a [TransportEventsListener].
 *
 * @property ptr: raw pointer to the underlying native TransportEventsListener.
 */
internal class JNITransportEventsListener(private val ptr: Long) {

    /**
     * Close and free the underlying transport events listener pointer.
     */
    fun close() {
        freePtrViaJNI(ptr)
    }

    private external fun freePtrViaJNI(ptr: Long)
}
