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
import io.zenoh.connectivity.TransportEvent

/**
 * Handler interface for classes implementing behavior to handle incoming [TransportEvent] events.
 *
 * @param R An arbitrary receiver.
 */
@Unstable
interface TransportEventsHandler<R> {

    /**
     * Handle the received [TransportEvent].
     *
     * @param event A [TransportEvent].
     */
    fun handle(event: TransportEvent)

    /**
     * Return the receiver of the handler.
     */
    fun receiver(): R

    /**
     * This callback is invoked by Zenoh at the conclusion of the handler's lifecycle.
     */
    fun onClose()
}
