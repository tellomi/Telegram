#ifndef TELLOMI_DC_ADDRESS_H
#define TELLOMI_DC_ADDRESS_H

/*
 * Android datacenter addresses are connected with inet_pton (ConnectionSocket).
 * A hostname is closed as "bad ipv4" / "bad ipv6" and must not replace a
 * working IP. See docs/TELLOMI.md (D-33).
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

#endif
