cmake_minimum_required(VERSION 3.22.1)
project(tinyasr_jni C)

# Filled by scripts/prepare.sh: third_party/tinyasr (sources + header) and the static library per ABI.
set(TINYASR_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/tinyasr)
set(TINYASR_LIB ${CMAKE_CURRENT_SOURCE_DIR}/../../../build/tinyasr/${ANDROID_ABI}/libtinyasr.a)
if(NOT EXISTS ${TINYASR_LIB})
    message(FATAL_ERROR "${TINYASR_LIB} is missing: run scripts/prepare.sh first")
endif()

add_library(tinyasr STATIC IMPORTED)
set_target_properties(tinyasr PROPERTIES IMPORTED_LOCATION ${TINYASR_LIB})

add_library(tinyasr_jni SHARED tinyasr_jni.c)
target_include_directories(tinyasr_jni PRIVATE ${TINYASR_DIR}/include)
target_compile_options(tinyasr_jni PRIVATE -Wall -Wextra -O2)
target_link_libraries(tinyasr_jni PRIVATE tinyasr log dl m)
target_link_options(tinyasr_jni PRIVATE -Wl,-z,max-page-size=16384 -Wl,--gc-sections)
