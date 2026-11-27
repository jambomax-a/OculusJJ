#!/bin/sh
# Linux 自己完結配布物パッケージングスクリプト
# 使用法: ./scripts/package-linux.sh

set -e

BUILD_DIR="build"
DIST_DIR="${BUILD_DIR}/linux-dist"
APP_DIR="${DIST_DIR}/OculusJJ"
LIB_DIR="${APP_DIR}/lib"
JRE_DIR="${APP_DIR}/jre"

# クリーン
rm -rf "${DIST_DIR}"
mkdir -p "${LIB_DIR}" "${JRE_DIR}"

# JDK ホームを jre/ にコピー（Java インストール不要）
JDK_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
cp -r "${JDK_HOME}"/* "${JRE_DIR}/"

# アプリ JAR + 依存 JAR（runtime classpath）を lib/ にコピー
# Gradle が依存 JAR を解決して渡す
for jar in "$@"; do
    cp "${jar}" "${LIB_DIR}/"
done

# 起動スクリプトを生成
cat > "${APP_DIR}/OculusJJ" << 'SCRIPT_EOF'
#!/bin/sh
#
# OculusJJ Linux 自己完結ランチャー（JRE バンドル済み）
#

SCRIPT_DIR="$(CD "$(dirname "$0")" && pwd)"
JRE_BIN="${SCRIPT_DIR}/jre/bin/java"
LIB_DIR="${SCRIPT_DIR}/lib"

if [ ! -x "${JRE_BIN}" ]; then
    echo "エラー: バンドル JRE が見つかりません: ${JRE_BIN}" >&2
    exit 1
fi

"${JRE_BIN}" \
    -Dawt.headless=true \
    -Dsun.java2d.d3d=false \
    --add-opens=java.desktop/javax.imageio.stream=ALL-UNNAMED \
    --add-opens=java.base/java.io=ALL-UNNAMED \
    --enable-native-access=ALL-UNNAMED \
    -cp "${LIB_DIR}/*" \
    com.oculus.jj.OculusJJApp "$@"
SCRIPT_EOF

chmod +x "${APP_DIR}/OculusJJ"

# アプリアイコンと .desktop ファイルを同梱
if [ -f "src/main/resources/icons/app_icon_256.png" ]; then
    cp "src/main/resources/icons/app_icon_256.png" "${APP_DIR}/OculusJJ.png"
fi

cat > "${APP_DIR}/OculusJJ.desktop" << 'DESKTOP_EOF'
[Desktop Entry]
Type=Application
Name=OculusJJ
Comment=Veterinary DICOM & 3D Workstation
Exec=./OculusJJ %F
Icon=./OculusJJ.png
Terminal=false
Categories=Science;MedicalSoftware;Graphics;
StartupWMClass=com-oculus-jj-OculusJJApp
DESKTOP_EOF

echo "パッケージ完成: ${APP_DIR}/OculusJJ"
