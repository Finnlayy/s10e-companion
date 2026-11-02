#!/bin/bash
set -euo pipefail

MODEL_DIR="${1:-./models}"
ADB="${ADB:-adb}"

echo "Checking ADB devices..."
$ADB devices

verify_file() {
    local filename="$1"
    local json_file="$2"
    
    local expected_size=$(grep -A 2 "\"file\": \"$filename\"" "$json_file" | grep "\"bytes\":" | tr -dc '0-9')
    local expected_sha256=$(grep -A 3 "\"file\": \"$filename\"" "$json_file" | grep "\"sha256\":" | cut -d '"' -f 4)
    
    local filepath="$MODEL_DIR/$filename"
    
    if [ ! -f "$filepath" ]; then
        echo "ERROR: File $filename not found in $MODEL_DIR."
        exit 1
    fi
    
    local actual_size=$(stat -c%s "$filepath")
    if [ "$actual_size" -ne "$expected_size" ]; then
        echo "ERROR: Size of $filename ($actual_size) does not match expected size ($expected_size)."
        exit 1
    fi
    
    local actual_sha256=$(sha256sum "$filepath" | awk '{print $1}')
    if [ "$actual_sha256" != "$expected_sha256" ]; then
        echo "ERROR: SHA256 of $filename ($actual_sha256) does not match expected SHA256 ($expected_sha256)."
        exit 1
    fi
    
    echo "OK: $filename"
}

verify_file "Hermes-3-Llama-3.2-3B-Q4_K_M.gguf" "app/src/main/assets/models.json"
verify_file "vosk-model-small-de-0.15.zip" "app/src/main/assets/speech.json"
verify_file "vits-piper-de_DE-thorsten-medium.tar.bz2" "app/src/main/assets/speech.json"

TARGET_DIR_APP="/sdcard/Android/data/dev.s10e.companion/files/models/"

echo "Pushing models to device..."
$ADB shell mkdir -p "$TARGET_DIR_APP"
$ADB push "$MODEL_DIR/Hermes-3-Llama-3.2-3B-Q4_K_M.gguf" "$TARGET_DIR_APP"
$ADB push "$MODEL_DIR/vosk-model-small-de-0.15.zip" "$TARGET_DIR_APP"
$ADB push "$MODEL_DIR/vits-piper-de_DE-thorsten-medium.tar.bz2" "$TARGET_DIR_APP"

echo "Models pushed successfully. Die App importiert sie beim nächsten Start."
