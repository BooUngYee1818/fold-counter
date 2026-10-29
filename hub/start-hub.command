#!/bin/bash
# macOS: 더블클릭하면 허브가 실행됩니다. (최초 1회: 터미널에서 chmod +x start-hub.command)
cd "$(dirname "$0")"
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 가 설치되어 있지 않습니다. https://nodejs.org 에서 LTS 버전을 설치해주세요."
  read -r -p "엔터를 누르면 닫힙니다."
  exit 1
fi
node server.js
read -r -p "허브가 종료되었습니다. 엔터를 누르면 닫힙니다."
