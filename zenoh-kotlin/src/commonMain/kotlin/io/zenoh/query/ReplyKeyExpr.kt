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

package io.zenoh.query

/**
 * The key expression semantics for replies to a query.
 *
 * Controls whether a get/query operation accepts replies on key expressions
 * that don't match the query's key expression.
 */
enum class ReplyKeyExpr {

    /**
     * Accept only replies whose key expression matches the query's key expression.
     * This is the default behavior.
     */
    MatchingQuery,

    /**
     * Accept replies on any key expression, including those that don't match the query.
     */
    Any;
}
