#!/bin/sh
# Linux 自己完結配布物パッケージングスクリプト
# 使用法: ./scripts/build-linux-dist.sh <DIST_DIR>
# 例: ./scripts/build-linux-dist.sh build/linux-dist

set -e

DIST_DIR="$1"
APP_DIR="${DIST_DIR}/OculusJJ"
LIB_DIR="${APP_DIR}/lib"
JRE_DIR="${APP_DIR}/jre"

# ディレクトリを作成
mkdir -p "${LIB_DIR}" "${JRE_DIR}"

# JDK から jlink で最小 JRE を生成（シンボリックリンク問題回避・配布サイズ削減）
JDK_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
JLINK_TEMP="${JRE_DIR}.tmp.$$"
rm -rf "${JLINK_TEMP}"

# jlink で最小ランタイムを構築
# アプリはモジュール化されていないため必要モジュールを明示
# --compress=2 で軽量化、--strip-debug でデバッグ情報を除去
jlink \
  --module-path "${JDK_HOME}/jmods" \
  --add-modules java.base,java.desktop,java.logging,java.xml,java.sql,java.security.jgss,java.naming,java.datatransfer,java.prefs,java.scripting,java.management,jdk.unsupported,jdk.unsupported.desktop \
  --output "${JLINK_TEMP}" \
  --compress=2 \
  --strip-debug \
  --no-man-pages \
  --no-header-files

# 生成結果を jre/ に移動
rm -rf "${JRE_DIR}"
mv "${JLINK_TEMP}" "${JRE_DIR}"

# 起動スクリプトを生成（依存 JAR は Gradle が lib/ にコピー済み）
cat > "${APP_DIR}/OculusJJ" << 'SCRIPT_EOF'
#!/bin/sh
#
# OculusJJ Linux 自己完結ランチャー（JRE バンドル済み）
#

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
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
