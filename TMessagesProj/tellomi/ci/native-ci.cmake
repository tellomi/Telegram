# Included from jni/CMakeLists.txt only when TELLOMI_CI_NATIVE is set.
# Caps ninja link parallelism (full LTO of tmessages.49) and optionally wraps clang with ccache.

if(DEFINED ENV{TELLOMI_CCACHE})
    find_program(TELLOMI_CCACHE_BIN ccache)
    if(TELLOMI_CCACHE_BIN)
        set(CMAKE_C_COMPILER_LAUNCHER "${TELLOMI_CCACHE_BIN}")
        set(CMAKE_CXX_COMPILER_LAUNCHER "${TELLOMI_CCACHE_BIN}")
        message(STATUS "Tellomi CI native: ccache ${TELLOMI_CCACHE_BIN}")
    else()
        message(WARNING "TELLOMI_CCACHE is set but ccache was not found")
    endif()
endif()

if(DEFINED ENV{TELLOMI_NINJA_COMPILE_JOBS} OR DEFINED ENV{TELLOMI_NINJA_LINK_JOBS})
    set(_tellomi_compile_jobs "2")
    set(_tellomi_link_jobs "1")
    if(DEFINED ENV{TELLOMI_NINJA_COMPILE_JOBS} AND NOT "$ENV{TELLOMI_NINJA_COMPILE_JOBS}" STREQUAL "")
        set(_tellomi_compile_jobs "$ENV{TELLOMI_NINJA_COMPILE_JOBS}")
    endif()
    if(DEFINED ENV{TELLOMI_NINJA_LINK_JOBS} AND NOT "$ENV{TELLOMI_NINJA_LINK_JOBS}" STREQUAL "")
        set(_tellomi_link_jobs "$ENV{TELLOMI_NINJA_LINK_JOBS}")
    endif()
    set_property(GLOBAL APPEND PROPERTY JOB_POOLS
        "tellomi_compile=${_tellomi_compile_jobs}"
        "tellomi_link=${_tellomi_link_jobs}")
    set(CMAKE_JOB_POOL_COMPILE tellomi_compile)
    set(CMAKE_JOB_POOL_LINK tellomi_link)
    message(STATUS "Tellomi CI native: ninja compile jobs ${_tellomi_compile_jobs}, link jobs ${_tellomi_link_jobs}")
endif()
