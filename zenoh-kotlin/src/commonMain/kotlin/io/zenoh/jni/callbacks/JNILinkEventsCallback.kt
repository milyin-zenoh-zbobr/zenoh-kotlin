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

package io.zenoh.jni.callbacks

/**
 * JNI callback for link events.
 *
 * For optional numeric fields (priorityMin, priorityMax, reliability), a value of -1 indicates None/absent.
 */
internal fun interface JNILinkEventsCallback {
    fun run(
        kind: Int,
        zidBytes: ByteArray,
        src: String,
        dst: String,
        group: String?,
        mtu: Int,
        isStreamed: Boolean,
        interfaces: Array<String>,
        authIdentifier: String?,
        priorityMin: Int,
        priorityMax: Int,
        reliability: Int,
    )
}
