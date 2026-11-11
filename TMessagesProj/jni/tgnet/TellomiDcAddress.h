#ifndef TELLOMI_DC_ADDRESS_H
#define TELLOMI_DC_ADDRESS_H

/*
 * Android datacenter addresses are connected with inet_pton (ConnectionSocket).
 * A hostname from help.getConfig is dropped and must not replace a working IP.
 * A seed hostname is not a literal: ConnectionSocket resolves it with the same
 * getHostByName path as a proxy host, then dials that IP. See docs/TELLOMI.md (D-33).
 */

#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdint.h>
#include <string>

inline bool tellomiAcceptDcAddress(const std::string &address, bool ipv6, int32_t port) {
    if (port <= 0 || port > 65535 || address.empty()) {
        return false;
    }
    unsigned char buf[sizeof(struct in6_addr)];
    int family = ipv6 ? AF_INET6 : AF_INET;
    return inet_pton(family, address.c_str(), buf) == 1;
}

// True when ConnectionSocket cannot inet_pton the host and must resolve it.
// Empty strings and IP literals are dialed directly or rejected as bad addresses.
inline bool tellomiDcHostNeedsResolve(const std::string &address) {
    if (address.empty()) {
        return false;
    }
    return !tellomiAcceptDcAddress(address, false, 1) && !tellomiAcceptDcAddress(address, true, 1);
}

#endif
