#!/bin/bash
# 无 gradle 手动构建脚本：aapt2 编译资源 -> javac 编译 Java -> d8 打 dex -> 打包 -> zipalign -> apksigner 签名
set -eo pipefail

export ANDROID_HOME=/data/user/work/android-sdk
BT=$ANDROID_HOME/build-tools/34.0.0
PLAT=$ANDROID_HOME/platforms/android-34/android.jar
PROJ=/workspace/WaterWidget
RES=$PROJ/src/main/res
BLD=$PROJ/build

[ -f "$PLAT" ] || { echo "ERROR: android.jar 未找到 ($PLAT)，SDK 未安装完成"; exit 1; }
[ -x "$BT/aapt2" ] || { echo "ERROR: build-tools 未找到 ($BT)"; exit 1; }

rm -rf "$BLD"
mkdir -p "$BLD/compiled" "$BLD/classes" "$BLD/dex" "$BLD/gen"

echo "[1/7] 编译资源 (aapt2 compile)"
"$BT/aapt2" compile --dir "$RES" -o "$BLD/compiled/res.zip"

echo "[2/7] 链接资源并生成 base.apk + R.java (aapt2 link)"
"$BT/aapt2" link \
  -o "$BLD/base.apk" \
  -I "$PLAT" \
  --manifest "$PROJ/src/main/AndroidManifest.xml" \
  -R "$BLD/compiled/res.zip" \
  --java "$BLD/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --auto-add-overlay

echo "[3/7] 编译 Java (javac)"
find "$PROJ/src/main/java" -name "*.java" > "$BLD/sources.txt"
find "$BLD/gen" -name "R.java" >> "$BLD/sources.txt"
javac -source 1.8 -target 1.8 -classpath "$PLAT" \
  -d "$BLD/classes" @"$BLD/sources.txt" 2>&1 | tee "$BLD/javac.log" | tail -25
if grep -q "error:" "$BLD/javac.log"; then
  echo "❌ javac 编译失败，请检查上面的错误"
  exit 1
fi

echo "[4/7] 打 dex (d8)"
( cd "$BLD/classes" && jar cf "$BLD/classes.jar" . )
"$BT/d8" --lib "$PLAT" --output "$BLD/dex" "$BLD/classes.jar"

echo "[5/7] 合并 classes.dex 进 apk"
( cd "$BLD/dex" && zip -j -q "$BLD/base.apk" classes.dex )

echo "[6/7] zipalign"
"$BT/zipalign" -f 4 "$BLD/base.apk" "$BLD/aligned.apk"

echo "[7/7] 生成 keystore 并签名 (apksigner)"
keytool -genkeypair -keystore "$BLD/debug.keystore" -storepass android \
  -alias androiddebugkey -keypass android \
  -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 2>/dev/null || true
"$BT/apksigner" sign \
  --ks "$BLD/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$PROJ/WaterWidget.apk" "$BLD/aligned.apk"

echo ""
echo "=== 验证签名 ==="
"$BT/apksigner" verify --print-certs "$PROJ/WaterWidget.apk" | head -5
echo ""
echo "=== 完成 ==="
ls -la "$PROJ/WaterWidget.apk"
echo "包信息："
"$BT/aapt2" dump packagename "$PROJ/WaterWidget.apk" 2>/dev/null || true
echo "BADGING:"
"$BT/aapt2" dump badging "$PROJ/WaterWidget.apk" 2>/dev/null | head -8 || true
