#include "TellomiNetConfig.h"

#include "Datacenter.h"
#include "FileLog.h"
#include "TellomiDcAddress.h"

static std::vector<TcpAddress> usableDcAddresses(const std::vector<TcpAddress> &input, bool ipv6) {
    std::vector<TcpAddress> kept;
    kept.reserve(input.size());
    for (const TcpAddress &entry : input) {
        if (!tellomiAcceptDcAddress(entry.address, ipv6, entry.port)) {
            if (LOGS_ENABLED) DEBUG_D("tellomi: ignore dc address \"%s\":%d ipv6=%d", entry.address.c_str(), entry.port, ipv6 ? 1 : 0);
            continue;
        }
        kept.push_back(entry);
    }
    return kept;
}

static std::string dcEndpoint(Datacenter *datacenter, uint32_t flags) {
    TcpAddress *current = datacenter->getCurrentAddress(flags);
    if (current == nullptr) {
        return std::string();
    }
    return current->address + ":" + std::to_string(current->port);
}

static void replaceDcFamily(Datacenter *datacenter, std::vector<TcpAddress> &addresses, uint32_t flags, bool filter, bool ipv6) {
    if (!filter) {
        datacenter->replaceAddresses(addresses, flags);
        return;
    }
    std::vector<TcpAddress> kept = usableDcAddresses(addresses, ipv6);
    if (kept.empty()) {
        if (!addresses.empty() && LOGS_ENABLED) {
            DEBUG_D("tellomi getConfig dc%u keep previous flags=%u (%u option(s) were not IP literals)", datacenter->getDatacenterId(), flags, (uint32_t) addresses.size());
        }
        return;
    }
    datacenter->replaceAddresses(kept, flags);
}

void TellomiNetConfig::applyConfigAddresses(Datacenter *datacenter, std::vector<TcpAddress> &ipv4, std::vector<TcpAddress> &ipv6, std::vector<TcpAddress> &ipv4Download, std::vector<TcpAddress> &ipv6Download, bool cdn) {
    if (datacenter == nullptr || TellomiNetConfig::lockDatacenterAddresses()) {
        return;
    }
    bool filter = TellomiNetConfig::customBackend();
    uint32_t cdnBit = cdn ? 8u : 0u;
    std::string before = dcEndpoint(datacenter, 0);
    replaceDcFamily(datacenter, ipv4, cdnBit | 0u, filter, false);
    replaceDcFamily(datacenter, ipv6, cdnBit | 1u, filter, true);
    replaceDcFamily(datacenter, ipv4Download, cdnBit | 2u, filter, false);
    replaceDcFamily(datacenter, ipv6Download, cdnBit | 3u, filter, true);
    if (LOGS_ENABLED) {
        std::string after = dcEndpoint(datacenter, 0);
        if (before != after) {
            DEBUG_D("tellomi getConfig dc%u %s -> %s", datacenter->getDatacenterId(), before.c_str(), after.c_str());
        }
    }
}
