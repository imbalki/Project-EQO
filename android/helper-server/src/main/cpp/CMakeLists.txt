cmake_minimum_required(VERSION 3.31)

project("eqo-helper")

# TASK-007 (issue #12): derived from RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea,
# path: manager/src/main/jni/CMakeLists.txt. Differences from upstream, all deliberate for the S1
# spike: the executable is named libeqo-starter.so (upstream libshizuku.so; upstream terms forbid
# reusing its names); the adb wireless-pairing library (adb_pairing.cpp, TASK-008 scope) is not
# built; boringssl/cxx prefab packages are not used (the starter links libc++ statically via
# -DANDROID_STL=c++_static in the module build file instead of upstream's prefab cxx::cxx).
set(CMAKE_CXX_STANDARD 17)

add_compile_options(-Werror=format -fdata-sections -ffunction-sections -fno-exceptions -fno-rtti -fno-threadsafe-statics)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    message("Building Release...")

    add_compile_options(-Os -flto -fvisibility=hidden -fvisibility-inlines-hidden)
    add_link_options(-flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all)
else ()
    message("Building Debug...")

    add_definitions(-DDEBUG)
endif ()

# The helper starter is an EXECUTABLE named like a JNI library so AGP packages it inside the EQO
# APK's lib/<abi>/ (the same loadable-library packaging upstream uses for libshizuku.so).
# "Packaged as a loadable library inside the APK" (TASK-007 scope).
add_executable(libeqo-starter.so
        starter.cpp misc.cpp selinux.cpp cgroup.cpp)

target_link_libraries(libeqo-starter.so log)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libeqo-starter.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libeqo-starter.so")
endif ()
