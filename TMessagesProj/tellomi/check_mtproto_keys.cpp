/*
 * Host check for the checked-in MTProto public keys.
 * Not part of the Android native build.
 *
 *   g++ -std=c++17 -Wall -Wextra -Wno-deprecated-declarations \
 *     -include pthread.h -include cstdio -include memory \
 *     -I TMessagesProj/jni/tgnet \
 *     TMessagesProj/jni/tgnet/TellomiNetConfig.cpp \
 *     TMessagesProj/tellomi/check_mtproto_keys.cpp \
 *     -lcrypto -o /tmp/check_mtproto_keys
 *   /tmp/check_mtproto_keys
 *
 * build.gradle tellomiPublicKeyPem() reads the whole PEM file (strip CR,
 * trim). This program feeds that same text to TellomiNetConfig::set.
 */

#include <pthread.h>
#include <cstdio>
#include <cstdarg>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "FileLog.h"
#include "TellomiNetConfig.h"

bool LOGS_ENABLED = true;

FileLog::FileLog() {
}

FileLog &FileLog::getInstance() {
    static FileLog instance;
    return instance;
}

static void writeLog(const char *message, va_list args) {
    vfprintf(stderr, message, args);
    fputc('\n', stderr);
}

void FileLog::d(const char *message, ...) {
    va_list args;
    va_start(args, message);
    writeLog(message, args);
    va_end(args);
}

void FileLog::e(const char *message, ...) {
    va_list args;
    va_start(args, message);
    writeLog(message, args);
    va_end(args);
}

static int failures = 0;

static void expect(bool ok, const std::string &message) {
    if (!ok) {
        std::cerr << "FAIL " << message << std::endl;
        failures++;
    }
}

static std::string readPemLikeGradle(const std::string &path) {
    std::ifstream in(path.c_str(), std::ios::binary);
    if (!in) {
        return "";
    }
    std::ostringstream buffer;
    buffer << in.rdbuf();
    std::string text = buffer.str();
    std::string stripped;
    stripped.reserve(text.size());
    for (size_t i = 0; i < text.size(); i++) {
        if (text[i] != '\r') {
            stripped.push_back(text[i]);
        }
    }
    size_t begin = stripped.find_first_not_of(" \t\n");
    size_t end = stripped.find_last_not_of(" \t\n");
    if (begin == std::string::npos) {
        return "";
    }
    return stripped.substr(begin, end - begin + 1);
}

int main(int argc, char **argv) {
    const char *path = argc > 1 ? argv[1] : "TMessagesProj/config/tellomi-server-public.pem";
    const uint64_t kNew = 4381149949997195609ULL;
    const uint64_t kOld = 12240908862933197005ULL;

    std::string pem = readPemLikeGradle(path);
    expect(!pem.empty(), std::string("read ") + path);
    expect(pem.find("-----BEGIN") != std::string::npos, "file contains a PEM block");
    size_t first = pem.find("-----BEGIN");
    size_t second = first == std::string::npos ? std::string::npos : pem.find("-----BEGIN", first + 1);
    expect(second != std::string::npos, "file contains both PEM blocks");

    TellomiNetConfig::set("1:8.137.39.82:10443", 1, pem, true, true);
    const std::vector<uint64_t> &fingerprints = TellomiNetConfig::publicKeyFingerprints();
    const std::vector<std::string> &pems = TellomiNetConfig::publicKeyPems();
    expect(fingerprints.size() == 2, "parsed two fingerprints");
    expect(pems.size() == fingerprints.size(), "pem and fingerprint counts match");
    if (fingerprints.size() == 2) {
        expect(fingerprints[0] == kNew, "first fingerprint is the 2026-11 key");
        expect(fingerprints[1] == kOld, "second fingerprint is the fallback key");
    }
    std::string firstPem = pems.empty() ? std::string() : pems.front();
    std::string secondPem = pems.size() < 2 ? std::string() : pems[1];
    expect(TellomiNetConfig::publicKeyFingerprint() == kNew, "publicKeyFingerprint returns the first key");
    expect(!TellomiNetConfig::publicKeyPem().empty() && TellomiNetConfig::publicKeyPem() == firstPem, "publicKeyPem returns the first block");
    expect(firstPem.find("MIIBCgKCAQEAoPJU") != std::string::npos, "first block is the 2026-11 modulus");

    if (!secondPem.empty()) {
        TellomiNetConfig::set("", 1, secondPem, true, true);
        expect(TellomiNetConfig::publicKeyFingerprints().size() == 1, "a single PEM still parses as one key");
        expect(TellomiNetConfig::publicKeyFingerprint() == kOld, "single-key fingerprint is the fallback");
    }

    std::string broken = "-----BEGIN RSA PUBLIC KEY-----\nnot-a-key\n-----END RSA PUBLIC KEY-----\n" + firstPem;
    TellomiNetConfig::set("", 1, broken, true, true);
    expect(TellomiNetConfig::publicKeyFingerprints().size() == 1, "an unreadable block is skipped");
    expect(TellomiNetConfig::publicKeyFingerprint() == kNew, "the next valid block is kept");

    TellomiNetConfig::set("", 1, "", true, true);
    expect(TellomiNetConfig::publicKeyFingerprints().empty(), "empty input stores no keys");
    expect(TellomiNetConfig::publicKeyFingerprint() == 0, "empty input fingerprint is 0");
    expect(TellomiNetConfig::publicKeyPem().empty(), "empty input pem is empty");

    if (failures != 0) {
        std::cerr << failures << " check(s) failed" << std::endl;
        return 1;
    }
    std::cout << "ok fingerprints " << kNew << " " << kOld << std::endl;
    return 0;
}
