cmake_minimum_required(VERSION 3.22.1)

project("pulseguard")

add_library(
        pulseguard-lib
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log
)

target_link_libraries(
        pulseguard-lib
        ${log-lib}
)
