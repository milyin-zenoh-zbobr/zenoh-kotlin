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
import io.zenoh.config.WhatAmI
import io.zenoh.config.ZenohId

/**
 * A transport session established to a zenoh peer node.
 *
 * Multiple transports to the same peer can exist (e.g., both unicast and multicast).
 * Each transport can have multiple corresponding [Link]s which represent actual data links.
 *
 * This is a pure snapshot value; instances can be used as filters for [Link] queries.
 */
@Unstable
data class Transport(
    val zid: ZenohId,
    val whatAmI: WhatAmI,
    val isQos: Boolean,
    val isMulticast: Boolean,
)
