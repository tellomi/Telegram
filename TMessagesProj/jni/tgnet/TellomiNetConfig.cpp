#include "TellomiNetConfig.h"

#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/pem.h>
#include <openssl/rsa.h>
#include <openssl/sha.h>

#include <cinttypes>
#include <cstdlib>

#include "FileLog.h"

static bool customBackendFlag = false;
static bool lockAddressesFlag = false;
static int32_t defaultDc = 1;
static std::vector<TellomiDcAddress> datacenterList;
static std::string serverPublicKeyPem;
static uint64_t serverPublicKeyFingerprint = 0;

static void appendTlBytes(std::vector<uint8_t> &out, const uint8_t *data, uint32_t length) {
    if (length <= 253) {
        out.push_back((uint8_t) length);
    } else {
        out.push_back(254);
        out.push_back((uint8_t) length);
        out.push_back((uint8_t) (length >> 8));
        out.push_back((uint8_t) (length >> 16));
    }
    out.insert(out.end(), data, data + length);
    uint32_t padded = (uint32_t) out.size();
    uint32_t addition = padded % 4;
    if (addition != 0) {
        out.insert(out.end(), 4 - addition, 0);
    }
}

static uint64_t fingerprintOfKey(RSA *rsaKey) {
    const BIGNUM *n = nullptr;
    const BIGNUM *e = nullptr;
    RSA_get0_key(rsaKey, &n, &e, nullptr);
    if (n == nullptr || e == nullptr) {
        return 0;
    }
    int nBytes = BN_num_bytes(n);
    int eBytes = BN_num_bytes(e);
    if (nBytes <= 0 || eBytes <= 0) {
        return 0;
    }
    std::vector<uint8_t> nBuf((size_t) nBytes);
    std::vector<uint8_t> eBuf((size_t) eBytes);
    BN_bn2bin(n, nBuf.data());
    BN_bn2bin(e, eBuf.data());
    std::vector<uint8_t> payload;
    payload.reserve((size_t) nBytes + (size_t) eBytes + 16);
    appendTlBytes(payload, nBuf.data(), (uint32_t) nBuf.size());
    appendTlBytes(payload, eBuf.data(), (uint32_t) eBuf.size());
    uint8_t sha1Buffer[SHA_DIGEST_LENGTH];
    SHA1(payload.data(), payload.size(), sha1Buffer);
    return ((uint64_t) sha1Buffer[19]) << 56 |
           ((uint64_t) sha1Buffer[18]) << 48 |
           ((uint64_t) sha1Buffer[17]) << 40 |
           ((uint64_t) sha1Buffer[16]) << 32 |
           ((uint64_t) sha1Buffer[15]) << 24 |
           ((uint64_t) sha1Buffer[14]) << 16 |
           ((uint64_t) sha1Buffer[13]) << 8 |
           ((uint64_t) sha1Buffer[12]);
}

static uint64_t fingerprintPem(const std::string &pem) {
    if (pem.empty()) {
        return 0;
    }
    ERR_clear_error();
    BIO *keyBio = BIO_new_mem_buf(pem.data(), (int) pem.size());
    if (keyBio == nullptr) {
        return 0;
    }
    RSA *rsaKey = PEM_read_bio_RSAPublicKey(keyBio, nullptr, nullptr, nullptr);
    if (rsaKey == nullptr) {
        BIO_free(keyBio);
        ERR_clear_error();
        keyBio = BIO_new_mem_buf(pem.data(), (int) pem.size());
        if (keyBio == nullptr) {
            return 0;
        }
        rsaKey = PEM_read_bio_RSA_PUBKEY(keyBio, nullptr, nullptr, nullptr);
    }
    BIO_free(keyBio);
    if (rsaKey == nullptr) {
        if (LOGS_ENABLED) DEBUG_E("tellomi: failed to parse server RSA public key");
        ERR_clear_error();
        return 0;
    }
    uint64_t fingerprint = fingerprintOfKey(rsaKey);
    RSA_free(rsaKey);
    return fingerprint;
}

static void parseDcSpec(const std::string &dcSpec) {
    datacenterList.clear();
    size_t cursor = 0;
    while (cursor < dcSpec.size()) {
        size_t comma = dcSpec.find(',', cursor);
        if (comma == std::string::npos) {
            comma = dcSpec.size();
        }
        std::string entry = dcSpec.substr(cursor, comma - cursor);
        cursor = comma + 1;
        size_t begin = entry.find_first_not_of(" \t\r\n");
        size_t end = entry.find_last_not_of(" \t\r\n");
        if (begin == std::string::npos) {
            continue;
        }
        entry = entry.substr(begin, end - begin + 1);

        size_t lastColon = entry.rfind(':');
        size_t firstColon = entry.find(':');
        if (lastColon == std::string::npos || firstColon == lastColon) {
            if (LOGS_ENABLED) DEBUG_E("tellomi: skip bad dc entry %s", entry.c_str());
            continue;
        }
        std::string idText = entry.substr(0, firstColon);
        std::string host = entry.substr(firstColon + 1, lastColon - firstColon - 1);
        std::string portText = entry.substr(lastColon + 1);
        if (host.size() >= 2 && host.front() == '[' && host.back() == ']') {
            host = host.substr(1, host.size() - 2);
        }
        char *idEnd = nullptr;
        char *portEnd = nullptr;
        long id = std::strtol(idText.c_str(), &idEnd, 10);
        long port = std::strtol(portText.c_str(), &portEnd, 10);
        if (idEnd == nullptr || *idEnd != '\0' || portEnd == nullptr || *portEnd != '\0' || id <= 0 || port <= 0 || port > 65535 || host.empty()) {
            if (LOGS_ENABLED) DEBUG_E("tellomi: skip bad dc entry %s", entry.c_str());
            continue;
        }
        TellomiDcAddress address;
        address.dcId = (int32_t) id;
        address.host = host;
        address.port = (uint16_t) port;
        datacenterList.push_back(address);
    }
}

void TellomiNetConfig::set(const std::string &dcSpec, int32_t defaultDcId, const std::string &publicKeyPem, bool customBackend, bool lockAddresses) {
    customBackendFlag = customBackend;
    lockAddressesFlag = lockAddresses;
    parseDcSpec(dcSpec);
    defaultDc = defaultDcId > 0 ? defaultDcId : 1;
    if (defaultDcId <= 0 && !datacenterList.empty()) {
        defaultDc = datacenterList.front().dcId;
    }
    serverPublicKeyPem = publicKeyPem;
    serverPublicKeyFingerprint = fingerprintPem(serverPublicKeyPem);
    if (LOGS_ENABLED) DEBUG_D("tellomi backend custom=%d lock=%d dcs=%u defaultDc=%d fingerprint=0x%" PRIx64, customBackendFlag ? 1 : 0, lockAddressesFlag ? 1 : 0, (uint32_t) datacenterList.size(), defaultDc, serverPublicKeyFingerprint);
}

bool TellomiNetConfig::customBackend() {
    return customBackendFlag;
}

bool TellomiNetConfig::lockDatacenterAddresses() {
    return customBackendFlag && lockAddressesFlag;
}

int32_t TellomiNetConfig::defaultDcId() {
    return defaultDc;
}

const std::vector<TellomiDcAddress> &TellomiNetConfig::datacenters() {
    return datacenterList;
}

const std::string &TellomiNetConfig::publicKeyPem() {
    return serverPublicKeyPem;
}

uint64_t TellomiNetConfig::publicKeyFingerprint() {
    return serverPublicKeyFingerprint;
}
