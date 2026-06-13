cmake_minimum_required(VERSION 3.22.1)
project("uniroot")

add_library(uniroot SHARED
    uniroot_jni.cpp
)

find_library(log-lib log)

target_link_libraries(uniroot ${log-lib})

target_compile_options(uniroot PRIVATE -Wall -Wextra -O2)
