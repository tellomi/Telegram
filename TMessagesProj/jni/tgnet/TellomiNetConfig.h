#ifndef TELLOMI_NET_CONFIG_H
#define TELLOMI_NET_CONFIG_H

/*
 * Tellomi fork isolation. Upstream tgnet calls these hooks instead of
 * embedding a second copy of the datacenter/RSA logic. See docs/TELLOMI.md.
 */

#include <stdint.h>
#include <string>
#include <vector>
#include "Defines.h"

struct TellomiDcAddress {
    int32_t dcId;
    std::string host;
    uint16_t port;
};

class Datacenter;

class TellomiNetConfig {
public:
    static void set(const std::string &dcSpec, int32_t defaultDcId, const std::string &publicKeyPem, bool customBackend, bool lockAddresses);
    static bool customBackend();
    static bool lockDatacenterAddresses();
    static int32_t defaultDcId();
    static const std::vector<TellomiDcAddress> &datacenters();
    static const std::vector<std::string> &publicKeyPems();
    static const std::vector<uint64_t> &publicKeyFingerprints();
    static const std::string &publicKeyPem();
    static uint64_t publicKeyFingerprint();
    // help.getConfig dc_options. No-op while addresses are locked. On the
    // custom backend, a family is replaced only when at least one inet_pton
    // address remains; hostnames are dropped and an empty result keeps the
    // previous list (the build-config seed, until a good config is saved).
    // After a replace, 10443 and 443 are paired on each host, so a config
    // that lists only 10443 does not remove the fallback.
    static void applyConfigAddresses(Datacenter *datacenter, std::vector<TcpAddress> &ipv4, std::vector<TcpAddress> &ipv6, std::vector<TcpAddress> &ipv4Download, std::vector<TcpAddress> &ipv6Download, bool cdn);
};

bool tellomiEnsureTcpAddresses(std::vector<TcpAddress> &addresses);

#endif
