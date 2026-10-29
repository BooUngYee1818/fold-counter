import UIKit

/// 모델 식별자 → 제품명. iOS 16 부터 앱이 사용자 지정 기기 이름을 읽을 수 없어서 식별자 매핑을 씁니다.
enum DeviceNames {
    static var modelIdentifier: String {
        #if targetEnvironment(simulator)
        return ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] ?? "Simulator"
        #else
        var s = utsname()
        uname(&s)
        return withUnsafePointer(to: &s.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) { String(cString: $0) }
        }
        #endif
    }

    private static let names: [String: String] = [
        "iPhone14,2": "iPhone 13 Pro", "iPhone14,3": "iPhone 13 Pro Max", "iPhone14,4": "iPhone 13 mini", "iPhone14,5": "iPhone 13",
        "iPhone14,6": "iPhone SE (3rd gen)", "iPhone14,7": "iPhone 14", "iPhone14,8": "iPhone 14 Plus",
        "iPhone15,2": "iPhone 14 Pro", "iPhone15,3": "iPhone 14 Pro Max", "iPhone15,4": "iPhone 15", "iPhone15,5": "iPhone 15 Plus",
        "iPhone16,1": "iPhone 15 Pro", "iPhone16,2": "iPhone 15 Pro Max",
        "iPhone17,1": "iPhone 16 Pro", "iPhone17,2": "iPhone 16 Pro Max", "iPhone17,3": "iPhone 16", "iPhone17,4": "iPhone 16 Plus", "iPhone17,5": "iPhone 16e",
    ]

    /// 표에 없는 새 기종(폴더블 아이폰 등)은 "iPhone (식별자)" 로 표시되고, 허브 관리 페이지에서 이름을 바꿀 수 있습니다.
    static func detect() -> String {
        let id = modelIdentifier
        if let n = names[id] { return n }
        if id.hasPrefix("iPhone") || id.hasPrefix("iPad") { return "\(id.hasPrefix("iPad") ? "iPad" : "iPhone") (\(id))" }
        return "iPhone"
    }
}
