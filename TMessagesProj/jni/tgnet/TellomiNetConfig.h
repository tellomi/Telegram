#ifndef TELLOMI_NET_CONFIG_H
#define TELLOMI_NET_CONFIG_H

/*
 * Tellomi fork isolation. Upstream tgnet calls these hooks instead of
 * embedding a second copy of the datacenter/RSA logic. See docs/TELLOMI.md.
 */

#include <stdint.h>
#include <string>
#include <vector>

struct TellomiDcAddress {
    int32_t dcId;
    std::string host;
    uint16_t port;
};

class TellomiNetConfig {
public:
    static void set(const std::string &dcSpec, int32_t defaultDcId, const std::string &publicKeyPem, bool customBackend, bool lockAddresses);
    static bool customBackend();
    static bool lockDatacenterAddresses();
    static int32_t defaultDcId();
    static const std::vector<TellomiDcAddress> &datacenters();
    static const std::string &publicKeyPem();
    static uint64_t publicKeyFingerprint();
};

#endif
