cmake_minimum_required(VERSION 3.22.1)
project(daemon_runner C)

# This CMakeLists.txt is provided for reference / local builds.
# In production, daemon_runner is cross-compiled in GitHub Actions
# using the NDK clang directly (static ARM64 binary).

add_executable(daemon_runner daemon_runner.c)
target_link_options(daemon_runner PRIVATE -static)
