#include "TellomiDcFallback.h"

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

static int failures = 0;

static void expect(bool condition, const char *message) {
    if (!condition) {
        std::cerr << "FAIL " << message << "\n";
        failures++;
    }
}

static TellomiEndpoint endpoint(const char *host, int32_t port, int32_t flags = 0, bool secret = false) {
    TellomiEndpoint entry;
    entry.host = host;
    entry.port = port;
    entry.flags = flags;
    entry.secret = secret;
    return entry;
}

static bool fileContains(const std::string &path, const std::string &needle) {
    std::ifstream input(path.c_str());
    if (!input) {
        std::cerr << "FAIL cannot read " << path << "\n";
        failures++;
        return false;
    }
    std::stringstream buffer;
    buffer << input.rdbuf();
    return buffer.str().find(needle) != std::string::npos;
}

static void testPersistedDatacenterGains443() {
    std::vector<TellomiEndpoint> saved;
    saved.push_back(endpoint("8.137.39.82", 10443));
    expect(tellomiEnsurePortPair(saved), "persisted 10443 list changes");
    expect(saved.size() == 2, "persisted list has two addresses");
    expect(saved[0].port == 10443 && saved[1].port == 443, "10443 stays first, 443 follows");
    expect(saved[0].host == "8.137.39.82" && saved[1].host == saved[0].host, "fallback uses the same host");
    expect(!tellomiEnsurePortPair(saved), "second ensure is a no-op");
}

static void testServerListThatOmits443IsRepaired() {
    std::vector<TellomiEndpoint> fromConfig;
    fromConfig.push_back(endpoint("8.137.39.82", 10443, 4));
    expect(tellomiEnsurePortPair(fromConfig), "getConfig 10443-only list changes");
    expect(fromConfig.size() == 2, "getConfig list gains 443");
    expect(fromConfig[0].port == 10443 && fromConfig[0].flags == 4, "10443 flags are kept");
    expect(fromConfig[1].port == 443 && fromConfig[1].flags == 4, "443 copies the 10443 flags");

    std::vector<TellomiEndpoint> only443;
    only443.push_back(endpoint("8.137.39.82", 443, 32));
    expect(tellomiEnsurePortPair(only443), "443-only list gains 10443");
    expect(only443[0].port == 10443 && only443[1].port == 443, "10443 is inserted ahead of 443");
    expect(only443[0].flags == 32 && only443[1].flags == 32, "synthesized 10443 keeps the 443 flags");

    std::vector<TellomiEndpoint> both;
    both.push_back(endpoint("8.137.39.82", 10443, 0));
    both.push_back(endpoint("8.137.39.82", 443, 32));
    expect(!tellomiEnsurePortPair(both), "server list that already has both ports is kept");
    expect(both[0].port == 10443 && both[0].flags == 0, "existing 10443 flags stay");
    expect(both[1].port == 443 && both[1].flags == 32, "existing 443 flags stay");
}

static void testNewIpAndSecrets() {
    std::vector<TellomiEndpoint> moved;
    moved.push_back(endpoint("203.0.113.10", 10443));
    moved.push_back(endpoint("203.0.113.10", 10443));
    expect(tellomiEnsurePortPair(moved), "duplicate 10443 collapses into the pair");
    expect(moved.size() == 2, "one host produces one pair");
    expect(moved[0].host == "203.0.113.10" && moved[0].port == 10443, "new ip keeps 10443");
    expect(moved[1].host == "203.0.113.10" && moved[1].port == 443, "new ip gains 443");

    std::vector<TellomiEndpoint> secret;
    secret.push_back(endpoint("8.137.39.82", 10443, 0, true));
    expect(!tellomiEnsurePortPair(secret), "mtproxy secret is not given a clear port");
    expect(secret.size() == 1 && secret[0].secret && secret[0].port == 10443, "secret endpoint stays");

    std::vector<TellomiEndpoint> empty;
    expect(!tellomiEnsurePortPair(empty), "empty getConfig result does not invent an address");
}

static void testTimeoutRotatesTo443() {
    std::vector<TellomiEndpoint> addresses;
    addresses.push_back(endpoint("8.137.39.82", 10443));
    addresses.push_back(endpoint("8.137.39.82", 443));
    uint32_t index = 0;
    expect(addresses[index].port == TELLOMI_MTPROTO_PORT, "first dial is 10443");
    expect(TELLOMI_CONNECT_TIMEOUT_SEC == 5, "connect timeout is 5s");

    // First timeout, no TCP connect, no useful data: upstream would retry
    // 10443 (failed == 1, willRetry == 1). The custom backend rotates.
    expect(!tellomiRotateOnDisconnect(false, 2, false, 1, 1, false), "upstream retries the same port once");
    expect(tellomiRotateOnDisconnect(true, 2, false, 1, 1, false), "custom backend rotates on the first connect timeout");
    expect(tellomiReconnectDelayMs(true, true) == (uint32_t) TELLOMI_FALLBACK_RECONNECT_MS, "fallback reconnect is 200ms");
    expect(tellomiReconnectDelayMs(false, true) == 1000, "upstream reconnect stays 1000ms");

    index = tellomiNextAddressIndex(index, (uint32_t) addresses.size());
    expect(index == 1 && addresses[index].port == TELLOMI_MTPROTO_FALLBACK_PORT, "next address is 443");

    // 443 timing out wraps back to 10443.
    expect(tellomiRotateOnDisconnect(true, 2, false, 1, 1, false), "443 timeout rotates too");
    index = tellomiNextAddressIndex(index, (uint32_t) addresses.size());
    expect(index == 0 && addresses[index].port == 10443, "wrap returns to 10443");

    // A refused connect (reason 1) still retries once before rotating.
    expect(!tellomiRotateOnDisconnect(true, 1, false, 1, 1, false), "a reset does not skip the retry");
    expect(tellomiRotateOnDisconnect(true, 1, false, 2, 1, false), "the second reset rotates");

    // An established connection that later times out is not this path.
    expect(!tellomiRotateOnDisconnect(true, 2, true, 1, 3, false), "a connected timeout uses the upstream retry count");
    expect(tellomiRotateOnDisconnect(true, 2, true, 1, 3, true), "switchToNextPort still rotates");

    // 5s deadline + 1s epoll slack + 200ms reconnect. 443 starts within 7s.
    const int32_t worstMs = TELLOMI_CONNECT_TIMEOUT_SEC * 1000 + 1000 + TELLOMI_FALLBACK_RECONNECT_MS;
    expect(worstMs <= 7000, "443 dial starts within 7s of a dropped 10443");
}

static void testShippableConfigListsBothPorts() {
    const char *expanded = "1:8.137.39.82:10443,1:8.137.39.82:443,2:8.137.39.82:10443,2:8.137.39.82:443";
    const char *templateSpec =
        "1:${TELLOMI_PRODUCTION_HOST}:${TELLOMI_PRODUCTION_PORT},1:${TELLOMI_PRODUCTION_HOST}:${TELLOMI_FALLBACK_PORT},2:${TELLOMI_PRODUCTION_HOST}:${TELLOMI_PRODUCTION_PORT},2:${TELLOMI_PRODUCTION_HOST}:${TELLOMI_FALLBACK_PORT}";
    expect(fileContains("TMessagesProj/build.gradle", "def TELLOMI_PRODUCTION_HOST = '8.137.39.82'"), "production host stays 8.137.39.82");
    expect(fileContains("TMessagesProj/build.gradle", "def TELLOMI_PRODUCTION_PORT = '10443'"), "10443 stays the primary port");
    expect(fileContains("TMessagesProj/build.gradle", "def TELLOMI_FALLBACK_PORT = '443'"), "443 is the fallback port");
    expect(fileContains("TMessagesProj/build.gradle", templateSpec), "gradle lists 10443 before 443 on dc 1 and dc 2");
    expect(fileContains(".github/workflows/android-apk.yml", expanded), "CI expects the expanded production DC string");
    expect(fileContains("TMessagesProj/build.gradle", "1:10.0.2.2:10443,2:10.0.2.2:10443"), "local profile stays on 10443");
}

int main() {
    testPersistedDatacenterGains443();
    testServerListThatOmits443IsRepaired();
    testNewIpAndSecrets();
    testTimeoutRotatesTo443();
    testShippableConfigListsBothPorts();
    if (failures != 0) {
        std::cerr << failures << " failure(s)\n";
        return 1;
    }
    std::cout << "tellomi dc fallback ok\n";
    return 0;
}
