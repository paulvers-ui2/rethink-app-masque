/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arcadesignpro.auroravpn.service

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import java.net.InetAddress

/**
 * Blocked networks (ASN) for the Countries & networks firewall. Holds the CIDR
 * prefixes announced by the AS numbers the user blocked, looked up from RIPEstat
 * and refreshed weekly by the ASN refresh worker (full flavor). This object is
 * in the main source set and does no networking itself: the worker computes the
 * prefixes and hands them over with [setPrefixes]. Until that runs the list is
 * empty and [isBlocked] is false, so country blocking works without it.
 *
 * Users block a handful of networks, so a linear scan over their prefixes is
 * cheap and avoids pulling in the native IP trie.
 */
object AsnRulesManager {

    @Volatile private var prefixes: List<IPAddress> = emptyList()

    fun setPrefixes(list: List<IPAddress>) {
        prefixes = list
    }

    fun clear() {
        prefixes = emptyList()
    }

    fun isEmpty(): Boolean = prefixes.isEmpty()

    /** True if [inet] falls inside any blocked network's prefix. */
    fun isBlocked(inet: InetAddress?): Boolean {
        if (inet == null) return false
        val snapshot = prefixes
        if (snapshot.isEmpty()) return false
        val host = inet.hostAddress ?: return false
        val addr = IPAddressString(host).address ?: return false
        return snapshot.any { it.isIPv4 == addr.isIPv4 && it.contains(addr) }
    }
}
