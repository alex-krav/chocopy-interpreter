cmake_minimum_required(VERSION 3.24)
project(ChocoPy)
set(CMAKE_CXX_STANDARD 17)
add_executable(chocopy main.c chunk.c debug.c memory.c value.c vm.c compiler.c scanner.c)