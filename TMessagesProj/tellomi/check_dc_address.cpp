/*
 * Host check for tellomiAcceptDcAddress. Not part of the Android native build.
 *
 *   g++ -std=c++17 -Wall -Wextra \
 *     -I TMessagesProj/jni/tgnet \
 *     TMessagesProj/tellomi/check_dc_address.cpp \
 *     -o /tmp/check_dc_address
 *   /tmp/check_dc_address
 *
 * ConnectionSocket::openConnection accepts a datacenter address only when
 * inet_pton succeeds. This program checks the same rule used before
 * help.getConfig replaces a saved address.
 */

#include <iostream>
#include <string>

#include "TellomiDcAddress.h"

static int failures = 0;

static void expect(bool ok, const std::string &message) {
    if (!ok) {
        std::cerr << "FAIL " << message << std::endl;
        failures++;
    }
}

int main() {
    expect(tellomiAcceptDcAddress("8.137.39.82", false, 10443), "production ipv4");
    expect(tellomiAcceptDcAddress("8.137.39.82", false, 5222), "production ipv4 on 5222");
    expect(tellomiAcceptDcAddress("10.0.2.2", false, 10443), "emulator seed is still an ip");
    expect(tellomiAcceptDcAddress("127.0.0.1", false, 10443), "loopback is an ip literal");
    expect(tellomiAcceptDcAddress("2001:db8::1", true, 10443), "ipv6 literal");
    expect(!tellomiAcceptDcAddress("api.tellomi.cn", false, 10443), "hostname is not ipv4");
    expect(!tellomiAcceptDcAddress("api.tellomi.cn", true, 10443), "hostname is not ipv6");
    expect(!tellomiAcceptDcAddress("8.137.39.82", true, 10443), "ipv4 is not accepted as ipv6");
    expect(!tellomiAcceptDcAddress("2001:db8::1", false, 10443), "ipv6 is not accepted as ipv4");
    expect(!tellomiAcceptDcAddress("8.137.39.82:10443", false, 10443), "port stuck in the host");
    expect(!tellomiAcceptDcAddress("[2001:db8::1]", true, 10443), "brackets are not an ipv6 literal");
    expect(!tellomiAcceptDcAddress("", false, 10443), "empty host");
    expect(!tellomiAcceptDcAddress("8.137.39.82", false, 0), "port 0");
    expect(!tellomiAcceptDcAddress("8.137.39.82", false, 65536), "port above 65535");
    expect(!tellomiAcceptDcAddress(" 8.137.39.82", false, 10443), "leading space");

    if (failures != 0) {
        std::cerr << failures << " check(s) failed" << std::endl;
        return 1;
    }
    std::cout << "ok dc address literals" << std::endl;
    return 0;
}
