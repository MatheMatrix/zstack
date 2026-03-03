#!/bin/bash
#
# ErrorCode Architecture Enforcement Script
# 
# This script enforces ErrorCode architectural rules via grep/regex checks.
# It's designed for CI integration to prevent architectural violations.
#
# Rules enforced:
# 1. No direct `new ErrorCode()` in production code (except infrastructure and allowed files)
# 2. No direct errf.* deprecated methods in production code (except infrastructure)
# 3. No `getCauses().get(0)` pattern anywhere (must use getRootCause())
#
# Exit codes:
#   0 - No violations found (PASS)
#   1 - Violations found (FAIL)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

VIOLATIONS=0

echo "=========================================="
echo "ErrorCode Architecture Enforcement Check"
echo "=========================================="
echo ""

cd "$PROJECT_ROOT"

# ==========================================
# Rule 1: No direct new ErrorCode() in production code
# ==========================================
echo "[Rule 1] Checking for direct 'new ErrorCode()' in production code..."

# Exclude infrastructure packages and allowed files
RULE1_EXCLUDE_PATTERNS=(
    "header/src/main/java/org/zstack/header/errorcode/"
    "core/src/main/java/org/zstack/core/errorcode/"
    "FutureCompletion.java"
    "FutureReturnValueCompletion.java"
    "AbstractHostAllocatorFlow.java"
    "sdk/src/main/java/"
    "ManagementNodeManagerImpl.java"
    "APIDeleteVolumeSnapshotGroupEvent.java"
    "APIRevertVmFromSnapshotGroupEvent.java"
    "HostAllocatorConstant.java"
)

# Search for "new ErrorCode()" in production code (src/main/java)
RULE1_RESULTS=$(grep -rn "new ErrorCode(" \
    --include='*.java' \
    --exclude-dir='target' \
    --exclude-dir='node_modules' \
    */src/main/java/ \
    2>/dev/null || true)

if [ -n "$RULE1_RESULTS" ]; then
    # Filter out excluded patterns
    FILTERED_RESULTS=""
    while IFS= read -r line; do
        EXCLUDED=0
        for pattern in "${RULE1_EXCLUDE_PATTERNS[@]}"; do
            if [[ "$line" == *"$pattern"* ]]; then
                EXCLUDED=1
                break
            fi
        done
        if [ $EXCLUDED -eq 0 ]; then
            FILTERED_RESULTS="${FILTERED_RESULTS}${line}\n"
        fi
    done <<< "$RULE1_RESULTS"
    
    if [ -n "$FILTERED_RESULTS" ]; then
        echo -e "${RED}[FAIL]${NC} Found direct 'new ErrorCode()' in production code:"
        echo -e "$FILTERED_RESULTS"
        VIOLATIONS=$((VIOLATIONS + $(echo -e "$FILTERED_RESULTS" | grep -c "new ErrorCode(" || echo 0)))
    else
        echo -e "${GREEN}[PASS]${NC} No violations found"
    fi
else
    echo -e "${GREEN}[PASS]${NC} No violations found"
fi
echo ""

# ==========================================
# Rule 2: No direct errf.* deprecated methods in production code
# ==========================================
echo "[Rule 2] Checking for direct errf.* deprecated methods in production code..."

# Exclude infrastructure packages
# Also exclude files that use List<ErrorCode> overloads of errf.* (not deprecated per contract)
# TODO: migrate these to a Platform.operrWithCauses() when added in a follow-up
RULE2_EXCLUDE_PATTERNS=(
    "core/src/main/java/org/zstack/core/errorcode/"
    "core/src/main/java/org/zstack/core/Platform.java"
    "image/src/main/java/org/zstack/image/ImageManagerImpl.java"
    "compute/src/main/java/org/zstack/compute/host/HostBase.java"
    "storage/src/main/java/org/zstack/storage/snapshot/VolumeSnapshotCascadeExtension.java"
)

# Search for the 5 specific deprecated method patterns (NOT List<ErrorCode> overloads)
# The deprecated methods are:
#   errf.stringToOperationError
#   errf.instantiateErrorCode
#   errf.throwableToOperationError
#   errf.throwableToInternalError
#   errf.stringToInternalError
DEPRECATED_METHODS=(
    "errf\.stringToOperationError"
    "errf\.instantiateErrorCode"
    "errf\.throwableToOperationError"
    "errf\.throwableToInternalError"
    "errf\.stringToInternalError"
)

RULE2_TOTAL=0
for method in "${DEPRECATED_METHODS[@]}"; do
    RULE2_RESULTS=$(grep -rn "$method" \
        --include='*.java' \
        --exclude-dir='target' \
        --exclude-dir='node_modules' \
        */src/main/java/ \
        2>/dev/null || true)
    
    if [ -n "$RULE2_RESULTS" ]; then
        # Filter out excluded patterns
        FILTERED_RESULTS=""
        while IFS= read -r line; do
            EXCLUDED=0
            for pattern in "${RULE2_EXCLUDE_PATTERNS[@]}"; do
                if [[ "$line" == *"$pattern"* ]]; then
                    EXCLUDED=1
                    break
                fi
            done
            if [ $EXCLUDED -eq 0 ]; then
                FILTERED_RESULTS="${FILTERED_RESULTS}${line}\n"
            fi
        done <<< "$RULE2_RESULTS"
        
        if [ -n "$FILTERED_RESULTS" ]; then
            echo -e "${RED}[FAIL]${NC} Found deprecated method '$method' in production code:"
            echo -e "$FILTERED_RESULTS"
            RULE2_TOTAL=$((RULE2_TOTAL + $(echo -e "$FILTERED_RESULTS" | grep -c "$method" || echo 0)))
        fi
    fi
done

if [ $RULE2_TOTAL -eq 0 ]; then
    echo -e "${GREEN}[PASS]${NC} No violations found"
else
    VIOLATIONS=$((VIOLATIONS + RULE2_TOTAL))
fi
echo ""

# ==========================================
# Rule 3: No getCauses().get(0) pattern anywhere
# ==========================================
echo "[Rule 3] Checking for 'getCauses().get(0)' pattern in all code..."

# Search in all Java files (production and test)
RULE3_RESULTS=$(grep -rn "getCauses()\.get(0)" \
    --include='*.java' \
    --exclude='ErrorCodeArchitectureTest.java' \
    --exclude-dir='target' \
    --exclude-dir='node_modules' \
    . \
    2>/dev/null || true)

if [ -n "$RULE3_RESULTS" ]; then
    echo -e "${RED}[FAIL]${NC} Found 'getCauses().get(0)' pattern:"
    echo "$RULE3_RESULTS"
    VIOLATIONS=$((VIOLATIONS + $(echo "$RULE3_RESULTS" | grep -c "getCauses().get(0)" || echo 0)))
else
    echo -e "${GREEN}[PASS]${NC} No violations found"
fi
echo ""

# ==========================================
# Summary
# ==========================================
echo "=========================================="
if [ $VIOLATIONS -eq 0 ]; then
    echo -e "${GREEN}PASS: 0 violations${NC}"
    echo "=========================================="
    exit 0
else
    echo -e "${RED}FAIL: $VIOLATIONS violation(s) found${NC}"
    echo "=========================================="
    echo ""
    echo "Violations must be fixed before merging:"
    echo "  - Rule 1: Use Platform.operr()/Platform.err() instead of new ErrorCode()"
    echo "  - Rule 2: Use Platform.operr()/Platform.err() instead of errf.* methods"
    echo "  - Rule 3: Use ErrorCodeList.getRootCause() instead of getCauses().get(0)"
    exit 1
fi
