# iOS 앱 (폴더블 아이폰용) — 테스트 버전

**한국어** | [English](README.md)

SwiftUI, iOS 17 이상. `FoldCounter.xcodeproj` 를 Xcode 로 열면 됩니다. 안드로이드 앱과 같은 허브 API 를 쓰므로 같은 자막에 함께 표시됩니다.

## 빌드와 설치
- **Xcode 에서 실기기로**: 프로젝트 열기 → 왼쪽 FoldCounter 타깃 → Signing & Capabilities → Team 에서 본인 Apple ID 선택 → 아이폰 연결 → Run. 무료 계정은 7일마다 재설치, 유료 개발자 계정이면 TestFlight 배포 가능.
- **서명 전 IPA 만들기** (명령줄):
  ```bash
  cd ios && xcodebuild -project FoldCounter.xcodeproj -target FoldCounter -sdk iphoneos -configuration Release CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO SYMROOT="$(pwd)/build" build && rm -rf build/Payload && mkdir build/Payload && cp -R build/Release-iphoneos/FoldCounter.app build/Payload/ && (cd build && zip -qr ../FoldCounter-unsigned.ipa Payload)
  ```
  이 IPA 는 Sideloadly / AltStore 로 본인 Apple ID 서명을 거쳐 설치합니다. GitHub Actions 가 push 때마다 같은 IPA 를 만들어 줍니다.
- **시뮬레이터**: Xcode → Settings → Components 에서 iOS 시뮬레이터 런타임을 받은 뒤 아무 iPhone 시뮬레이터로 Run. 허브 주소는 Mac 에서 돌리는 허브라면 자동 검색이 되고, 안 되면 `127.0.0.1`.

## 현재 확인된 범위
- Xcode 26.6 / iOS 26.5 SDK 에서 컴파일 통과, 서명 전 IPA 생성 확인.
- 실기기와 시뮬레이터 실행 테스트는 아직입니다. 폴더블 아이폰 실기기 확인도 아직입니다.

## 접힘 감지 방식 (설계)

## 준비물
- App Store 에서 **Xcode** 설치 (무료, 용량이 큽니다)
- Apple ID. 무료 계정은 기기에 설치한 앱이 7일마다 재설치가 필요하고, 유료 개발자 계정($99/년)이면 TestFlight 로 배포할 수 있습니다.

## 구조 (안드로이드 앱과 1:1 대응, 구현 완료)
| 역할 | 안드로이드 | iOS 계획 |
|---|---|---|
| 접힘 감지 | 힌지 각도 센서 → 화면 상태 | `FoldMonitor` 프로토콜. 1) iOS SDK 에 접힘 상태 API 가 있으면 사용 (Xcode 설치 후 확인) 2) 없으면 커버 화면 ↔ 내부 화면 전환으로 생기는 윈도우 크기 변화(`UIWindowScene` 의 `coordinateSpace` / `traitCollection` 변경)로 접힘·펼침 판정 |
| 카운트 저장 | SharedPreferences (commit) | `UserDefaults` + 즉시 `synchronize` |
| 허브 통신 | HttpURLConnection + UDP 브로드캐스트 | `URLSession` + `Network` 프레임워크 `NWConnection`(UDP). 로컬 네트워크 권한 필요: `NSLocalNetworkUsageDescription` 추가, 첫 실행 때 iOS 가 묻는 "로컬 네트워크" 허용 |
| 버튼 차단 | 앱 고정 (Lock Task) | 사용법 유도(Guided Access). 앱이 켤 수는 없고 **켜졌는지 감지**만 가능: `UIAccessibility.isGuidedAccessEnabled`, `guidedAccessStatusDidChangeNotification`. 꺼져 있으면 "사이드 버튼 3번 클릭 → 시작" 안내를 화면에 띄우고, 켜지면 안내를 지움 |
| 화면 켜짐 유지 | keepScreenOn | `UIApplication.shared.isIdleTimerDisabled = true` |
| 기기 이름 | 모델 코드 매핑표 | `utsname.machine` (예: `iPhone18,3`) → 제품명 매핑표. iOS 16 부터 `UIDevice.name` 은 특별 권한 없이는 "iPhone" 만 반환하므로 매핑표 + 허브에서 이름 수정 |
| 가만히 두면 제작자 표시 | 가속도 센서 | `CoreMotion` `CMMotionManager.accelerometerData` + 터치 |
| 언어 | values / values-ko | `Localizable.strings` (en, ko). 기기 언어를 자동으로 따릅니다 |
| 메뉴 | 5번 탭 | 동일 (5번 탭) |

## 허브 API (안드로이드와 동일)
- `POST /api/register` `{deviceId, name, model, manufacturer, platform:"ios", osVersion, detector, appVersion, count}`
- `POST /api/fold` `{deviceId, delta}`
- `POST /api/heartbeat` `{deviceId, detector, posture}` 5초마다
- 자동 검색: UDP 47777 로 `FOLDCOUNTER_DISCOVER` 브로드캐스트 → `FOLDCOUNTER_HUB <port>` 응답

## 개인정보
안드로이드와 같습니다. 계정 없음, 분석 없음, 외부 서버 없음. 기기 ID 는 앱 설치 시 생성하는 무작위 UUID.

## 다음 단계
1. 시뮬레이터 또는 일반 아이폰에서 허브 연결·메뉴·테스트 접힘·사용법 유도 안내 확인
2. 폴더블 아이폰이 생기면 `Detection/WindowFoldMonitor.swift` 의 판정(화면 폭 80% 기준, 앱 잠듦)을 실기기에 맞게 조정. 애플이 접힘 API 를 공개하면 이 파일만 교체
