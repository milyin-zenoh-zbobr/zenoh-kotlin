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
import io.zenoh.sample.SampleKind

/**
 * Event emitted when a [Transport] is opened or closed.
 *
 * [kind] is [SampleKind.PUT] when the transport is opened, and [SampleKind.DELETE] when it is closed.
 */
@Unstable
data class TransportEvent(
    val kind: SampleKind,
    val transport: Transport,
)
