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
import io.zenoh.config.ZenohId
import io.zenoh.qos.Reliability

/**
 * A concrete data link within a [Transport].
 *
 * Zenoh can establish multiple links to the same remote node using different protocols
 * (e.g., TCP, UDP, QUIC, etc.).
 */
@Unstable
data class Link(
    val zid: ZenohId,
    val src: String,
    val dst: String,
    val group: String?,
    val mtu: Int,
    val isStreamed: Boolean,
    val interfaces: List<String>,
    val authIdentifier: String?,
    val priorityMin: Int?,
    val priorityMax: Int?,
    val reliability: Reliability?,
)
