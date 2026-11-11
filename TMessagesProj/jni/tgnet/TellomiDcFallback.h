#ifndef TELLOMI_DC_FALLBACK_H
#define TELLOMI_DC_FALLBACK_H

/*
 * Tellomi serves MTProto on 10443 and, for networks that drop that port, on
 * 443 of the same host. 10443 stays the first address. 443 is the next one.
 * The built-in IP is tried first. api.tellomi.cn follows, resolved by the
 * existing getHostByName path, so a DNS change can move the server without
 * an app update. help.getConfig replaces IP literals and the hostname is
 * appended again.
 *
 * Upstream tgnet also rewrites a single address onto ports 443 and 5222, but
 * only after repeating the configured port, and 5222 is not this server.
 * The custom backend dials the configured address port and moves to the next
 * address when a connect times out.
 */

#include <stddef.h>
#include <stdint.h>
#include <string>
#include <vector>
#include "TellomiDcAddress.h"

static const int32_t TELLOMI_MTPROTO_PORT = 10443;
static const int32_t TELLOMI_MTPROTO_FALLBACK_PORT = 443;

// Bound for a TCP handshake that receives no SYN-ACK. checkTimeout runs
// after epoll_wait, and ConnectionsManager::callEvents sleeps at most one
// second, so a dropped 10443 is abandoned in about 5-6s.
static const int32_t TELLOMI_CONNECT_TIMEOUT_SEC = 5;

// Upstream waits 1000 ms before dialing again. The fallback port should
// not sit behind that pause once 10443 has already timed out.
static const int32_t TELLOMI_FALLBACK_RECONNECT_MS = 200;

struct TellomiEndpoint {
    std::string host;
    int32_t port = 0;
    int32_t flags = 0;
    bool secret = false;
};

inline bool tellomiSameEndpoint(const TellomiEndpoint &left, const TellomiEndpoint &right) {
    return left.host == right.host && left.port == right.port && left.flags == right.flags && left.secret == right.secret;
}

// 10443 and 443 travel as a pair on the same host. 10443 is placed first.
// A secret (MTProxy) endpoint is left alone. Returns true when the list changes.
inline bool tellomiEnsurePortPair(std::vector<TellomiEndpoint> &addresses) {
    std::vector<TellomiEndpoint> rebuilt;
    rebuilt.reserve(addresses.size() + 2);
    auto listed = [&](const std::string &host, int32_t port, bool secret) {
        for (const TellomiEndpoint &entry : rebuilt) {
            if (entry.secret == secret && entry.host == host && entry.port == port) {
                return true;
            }
        }
        return false;
    };
    auto original = [&](const std::string &host, int32_t port) -> const TellomiEndpoint * {
        for (const TellomiEndpoint &entry : addresses) {
            if (!entry.secret && entry.host == host && entry.port == port) {
                return &entry;
            }
        }
        return nullptr;
    };
    for (const TellomiEndpoint &entry : addresses) {
        if (!entry.secret && !entry.host.empty() && (entry.port == TELLOMI_MTPROTO_PORT || entry.port == TELLOMI_MTPROTO_FALLBACK_PORT)) {
            if (listed(entry.host, TELLOMI_MTPROTO_PORT, false) || listed(entry.host, TELLOMI_MTPROTO_FALLBACK_PORT, false)) {
                continue;
            }
            const TellomiEndpoint *primary = original(entry.host, TELLOMI_MTPROTO_PORT);
            const TellomiEndpoint *fallback = original(entry.host, TELLOMI_MTPROTO_FALLBACK_PORT);
            TellomiEndpoint primaryOut = primary != nullptr ? *primary : entry;
            primaryOut.port = TELLOMI_MTPROTO_PORT;
            primaryOut.secret = false;
            TellomiEndpoint fallbackOut = fallback != nullptr ? *fallback : entry;
            fallbackOut.port = TELLOMI_MTPROTO_FALLBACK_PORT;
            fallbackOut.secret = false;
            rebuilt.push_back(primaryOut);
            rebuilt.push_back(fallbackOut);
            continue;
        }
        if (!listed(entry.host, entry.port, entry.secret)) {
            rebuilt.push_back(entry);
        }
    }
    bool changed = rebuilt.size() != addresses.size();
    if (!changed) {
        for (size_t i = 0; i < rebuilt.size(); i++) {
            if (!tellomiSameEndpoint(rebuilt[i], addresses[i])) {
                changed = true;
                break;
            }
        }
    }
    if (changed) {
        addresses.swap(rebuilt);
    }
    return changed;
}

// An IPv6 literal list is not a place for an A-record hostname. ConnectionSocket
// would open an AF_INET6 socket and close the name as a bad IPv6 address.
inline bool tellomiEndpointListIsIpv6(const std::vector<TellomiEndpoint> &addresses) {
    for (const TellomiEndpoint &entry : addresses) {
        if (entry.secret) {
            continue;
        }
        int32_t port = entry.port > 0 ? entry.port : 1;
        if (tellomiAcceptDcAddress(entry.host, true, port) && !tellomiAcceptDcAddress(entry.host, false, port)) {
            return true;
        }
    }
    return false;
}

// Append host:10443 when that hostname is missing, then pair 443. A literal
// is ignored. Returns true when the list changes.
inline bool tellomiAppendHostnameFallback(std::vector<TellomiEndpoint> &addresses, const std::string &host) {
    if (!tellomiDcHostNeedsResolve(host)) {
        return false;
    }
    bool present = false;
    for (const TellomiEndpoint &entry : addresses) {
        if (!entry.secret && entry.host == host) {
            present = true;
            break;
        }
    }
    if (!present) {
        TellomiEndpoint added;
        added.host = host;
        added.port = TELLOMI_MTPROTO_PORT;
        added.flags = 0;
        added.secret = false;
        addresses.push_back(added);
    }
    return tellomiEnsurePortPair(addresses);
}

// After help.getConfig replaces a family with IP literals, put each seed
// hostname back at the end (10443 then 443). An empty list is left empty so
// a hostname-only config cannot replace the previous addresses. An IPv6
// family is left alone.
inline bool tellomiRestoreHostnameFallbacks(std::vector<TellomiEndpoint> &addresses, const std::vector<std::string> &hostnames) {
    if (addresses.empty() || hostnames.empty() || tellomiEndpointListIsIpv6(addresses)) {
        return false;
    }
    bool changed = false;
    for (const std::string &host : hostnames) {
        if (tellomiAppendHostnameFallback(addresses, host)) {
            changed = true;
        }
    }
    return changed;
}

// Next configured address. Wraps to the first after the last.
inline uint32_t tellomiNextAddressIndex(uint32_t current, uint32_t count) {
    if (count == 0) {
        return 0;
    }
    if (current + 1 < count) {
        return current + 1;
    }
    return 0;
}

// reason 2 is ConnectionSocket::closeSocket from checkTimeout. A port that
// is dropped never finishes TCP, so wasConnected stays false. Rotate on that
// first timeout. Other failures keep the upstream "retry once" rule.
inline bool tellomiRotateOnDisconnect(bool customBackend, int32_t reason, bool wasConnected, uint32_t failedConnectionCount, uint32_t willRetryConnectCount, bool switchToNextPort) {
    if (switchToNextPort) {
        return true;
    }
    if (customBackend && reason == 2 && !wasConnected) {
        return true;
    }
    return failedConnectionCount > willRetryConnectCount;
}

inline uint32_t tellomiReconnectDelayMs(bool customBackend, bool rotatedAfterConnectTimeout) {
    if (customBackend && rotatedAfterConnectTimeout) {
        return (uint32_t) TELLOMI_FALLBACK_RECONNECT_MS;
    }
    return 1000;
}

#endif
