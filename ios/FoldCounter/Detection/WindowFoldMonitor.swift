import SwiftUI

/// iOS 에는 공개된 힌지 센서 API 가 없어서 두 가지 신호로 접힘을 판정합니다.
/// 1. 화면 폭: 커버 화면(작음) ↔ 내부 화면(큼) 전환. 지금까지 본 최대 폭의 80% 미만이면 접힘.
/// 2. 앱 상태: 접어서 화면이 꺼지면 앱이 잠듭니다(background). 설정에 따라 이를 접힘으로 간주.
/// 폴더블 아이폰 SDK 에 접힘 API 가 생기면 이 클래스만 바꾸면 됩니다.
final class WindowFoldMonitor {
    var onPosture: ((AppState.Posture, String) -> Void)?
    private var maxWidth: Double = Prefs.shared.innerWidth
    private var lastSize: CGSize = .zero
    private var inBackground = false

    func windowSize(_ s: CGSize) {
        lastSize = s
        evaluate()
    }

    func scenePhase(_ p: ScenePhase) {
        inBackground = (p == .background)
        evaluate()
    }

    func reset() {
        maxWidth = Prefs.shared.innerWidth
        evaluate()
    }

    private func evaluate() {
        let w = Double(min(lastSize.width, lastSize.height))
        guard w > 0 else { return }
        if w > maxWidth {
            maxWidth = w
            Prefs.shared.innerWidth = w
        }
        let small = maxWidth > 0 && w < maxWidth * 0.8
        let asleep = inBackground && Prefs.shared.backgroundIsFold
        let posture: AppState.Posture = (small || asleep) ? .closed : .open
        onPosture?(posture, "\(inBackground ? "asleep" : "awake"), width \(Int(w))pt / max \(Int(maxWidth))pt")
    }
}
