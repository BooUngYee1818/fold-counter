import Foundation

/// 앱 설정과 카운트 저장. 모두 이 폰 안에만 저장됩니다.
final class Prefs {
    static let shared = Prefs()
    private let d = UserDefaults.standard
    private init() {}

    /// 앱 설치 시 만들어지는 무작위 ID. 기기 고유 식별자는 쓰지 않습니다.
    var deviceId: String {
        if let v = d.string(forKey: "deviceId") { return v }
        let v = UUID().uuidString
        d.set(v, forKey: "deviceId")
        return v
    }

    var deviceName: String {
        get { d.string(forKey: "deviceName") ?? "" }
        set { d.set(newValue, forKey: "deviceName") }
    }
    var setupDone: Bool {
        get { d.bool(forKey: "setupDone") }
        set { d.set(newValue, forKey: "setupDone") }
    }
    var count: Int {
        get { d.integer(forKey: "count") }
        set { d.set(newValue, forKey: "count"); d.synchronize() }
    }
    var pendingFolds: Int {
        get { d.integer(forKey: "pendingFolds") }
        set { d.set(newValue, forKey: "pendingFolds"); d.synchronize() }
    }
    var hubMode: String {   // auto | manual
        get { d.string(forKey: "hubMode") ?? "auto" }
        set { d.set(newValue, forKey: "hubMode") }
    }
    var hubHost: String {
        get { d.string(forKey: "hubHost") ?? "" }
        set { d.set(newValue, forKey: "hubHost") }
    }
    var hubPort: Int {
        get { let p = d.integer(forKey: "hubPort"); return p == 0 ? 8787 : p }
        set { d.set(newValue, forKey: "hubPort") }
    }
    var lastHubUrl: String? {
        get { d.string(forKey: "lastHubUrl") }
        set { d.set(newValue, forKey: "lastHubUrl") }
    }
    /// 앱이 잠들었다(접힘) 깨어나면 1회로 셀지
    var backgroundIsFold: Bool {
        get { d.object(forKey: "backgroundIsFold") == nil ? true : d.bool(forKey: "backgroundIsFold") }
        set { d.set(newValue, forKey: "backgroundIsFold") }
    }
    var showName: Bool {
        get { d.object(forKey: "showName") == nil ? true : d.bool(forKey: "showName") }
        set { d.set(newValue, forKey: "showName") }
    }
    var idleMinutes: Int {
        get { d.object(forKey: "idleMinutes") == nil ? 3 : d.integer(forKey: "idleMinutes") }
        set { d.set(newValue, forKey: "idleMinutes") }
    }
    var creditText: String {
        get { d.string(forKey: "creditText") ?? "" }
        set { d.set(newValue, forKey: "creditText") }
    }
    /// 지금까지 본 가장 큰 화면 폭 (내부 화면 기준값)
    var innerWidth: Double {
        get { d.double(forKey: "innerWidth") }
        set { d.set(newValue, forKey: "innerWidth") }
    }
}
