#!/bin/bash

# Performance Comparison: Native vs JVM
# This script compares startup time and memory usage between native and JVM execution

set -e

echo "=========================================="
echo "Lyocell Performance Comparison"
echo "Native Image vs JVM"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if native image exists
if [ ! -f "build/native/nativeCompile/lyocell" ]; then
    echo "${YELLOW}Native image not found. Building...${NC}"
    ./gradlew nativeCompile
    echo ""
fi

# Check if JAR exists
if [ ! -f "build/libs/lyocell-1.0-SNAPSHOT.jar" ]; then
    echo "${YELLOW}JAR not found. Building...${NC}"
    ./gradlew build -x test
    echo ""
fi

echo "${BLUE}=== Native Image ===${NC}"
echo "Binary size:"
du -h build/native/nativeCompile/lyocell

echo ""
echo "Startup time (3 runs):"
for i in {1..3}; do
    /usr/bin/time -p timeout 5s ./build/native/nativeCompile/lyocell 2>&1 | grep real || true
done

echo ""
echo "Memory usage (during execution):"
./build/native/nativeCompile/lyocell &
PID=$!
sleep 2
if ps -p $PID > /dev/null; then
    ps -o rss=,vsz= -p $PID | awk '{printf "RSS: %.2f MB, VSZ: %.2f MB\n", $1/1024, $2/1024}'
    kill $PID 2>/dev/null || true
fi
wait $PID 2>/dev/null || true

echo ""
echo "${BLUE}=== JVM ===${NC}"
echo "JAR size:"
du -h build/libs/lyocell-1.0-SNAPSHOT.jar

echo ""
echo "Startup time (3 runs):"
for i in {1..3}; do
    /usr/bin/time -p timeout 5s java --enable-preview -jar build/libs/lyocell-1.0-SNAPSHOT.jar 2>&1 | grep real || true
done

echo ""
echo "Memory usage (during execution):"
java --enable-preview -jar build/libs/lyocell-1.0-SNAPSHOT.jar &
PID=$!
sleep 2
if ps -p $PID > /dev/null; then
    ps -o rss=,vsz= -p $PID | awk '{printf "RSS: %.2f MB, VSZ: %.2f MB\n", $1/1024, $2/1024}'
    kill $PID 2>/dev/null || true
fi
wait $PID 2>/dev/null || true

echo ""
echo "${GREEN}=== Summary ===${NC}"
echo "Native Image:"
echo "  ✓ Faster startup (typically 10-50ms)"
echo "  ✓ Lower memory footprint (typically 50-100MB)"
echo "  ✓ Single executable, no JVM needed"
echo "  ✓ Better for CLI tools and microservices"
echo ""
echo "JVM:"
echo "  ✓ Faster warmup after JIT compilation"
echo "  ✓ Better peak performance for long-running apps"
echo "  ✓ Dynamic optimization at runtime"
echo "  ✓ Easier debugging and profiling"
echo ""
echo "=========================================="
