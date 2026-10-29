import SwiftUI
import UIKit

/// 서비스 역할 + 화면 상태. 접힘(closed) 뒤에 펼침(open)이 오면 1회로 셉니다.
@MainActor
final class AppState: ObservableObject {
    static let shared = AppState()

    enum Posture: String { case unknown, open, half, closed }
    enum HubStatus { case searching, connected, error }

    @Published var count: Int
    @Published var deviceName: String
    @Published var posture: Posture = .unknown
    @Published var detectorDetail = ""
    @Published var hubStatus: HubStatus = .searching
    @Published var hubUrl: String? = nil
    @Published var pending: Int
    @Published var log: [String] = []
    @Published var guidedAccess: Bool = UIAccessibility.isGuidedAccessEnabled
    @Published var setupDone: Bool

    let prefs = Prefs.shared
    let hub: HubClient
    private let monitor = WindowFoldMonitor()
    private var armed = false
    private var lastCountAt: TimeInterval = 0
    private var started = false

    private init() {
        count = prefs.count
        deviceName = prefs.deviceName
        pending = prefs.pendingFolds
        setupDone = prefs.setupDone
        hub = HubClient(osVersion: UIDevice.current.systemVersion, model: DeviceNames.modelIdentifier)
        monitor.onPosture = { [weak self] p, detail in
            Task { @MainActor in self?.onPosture(p, detail) }
        }
        NotificationCenter.default.addObserver(forName: UIAccessibility.guidedAccessStatusDidChangeNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.guidedAccess = UIAccessibility.isGuidedAccessEnabled }
        }
        addLog(String(format: String(localized: "log_detector"), String(localized: "detector_window")))
    }

    func start() {
        guard !started else { return }
        started = true
        Task { await hub.start() }
    }

    func reportWindowSize(_ size: CGSize) { monitor.windowSize(size) }
    func scenePhaseChanged(_ phase: ScenePhase) { monitor.scenePhase(phase) }

    func onPosture(_ p: Posture, _ detail: String) {
        posture = p
        detectorDetail = detail
        switch p {
        case .closed:
            if !armed {
                armed = true
                addLog(String(localized: "log_closed") + " (\(detail))")
            }
        case .open:
            if armed {
                let now = Date().timeIntervalSince1970
                if now - lastCountAt >= 0.5 {
                    armed = false
                    lastCountAt = now
                    increment()
                    Task { await hub.onFold() }
                    addLog(String(format: String(localized: "log_unfold"), count) + " (\(detail))")
                }
            }
        default:
            break
        }
    }

    func increment() { count += 1; prefs.count = count }
    func setCount(_ n: Int) { count = n; prefs.count = n }

    func reset() {
        setCount(0)
        Task { await hub.pushCount(0) }
        addLog(String(localized: "log_reset"))
    }

    func simulateFold() {
        onPosture(.closed, "simulated")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [weak self] in self?.onPosture(.open, "simulated") }
    }

    func finishSetup(name: String) {
        prefs.deviceName = name
        prefs.setupDone = true
        deviceName = name
        setupDone = true
        Task { await hub.renamed() }
    }

    func settingsChanged() {
        deviceName = prefs.deviceName
        monitor.reset()
        Task { await hub.renamed(); await hub.resetConnection() }
    }

    func reconnect() { Task { await hub.resetConnection() } }

    func addLog(_ s: String) {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        log.insert("\(f.string(from: Date()))  \(s)", at: 0)
        if log.count > 40 { log.removeLast() }
    }

    func setHub(_ status: HubStatus, _ url: String?) { hubStatus = status; hubUrl = url }
    func setPending(_ n: Int) { pending = n }
    func adoptFromHub(count c: Int?, displayName: String?) {
        if let c, c >= 0, c != count { setCount(c) }
        if let n = displayName, !n.isEmpty { deviceName = n }
    }
    var localizedHubStatus: String {
        switch hubStatus {
        case .connected: return String(format: String(localized: "hub_connected"), hubUrl ?? "")
        case .searching: return String(localized: "hub_searching")
        case .error: return String(localized: "hub_error")
        }
    }
    var localizedPosture: String {
        switch posture {
        case .open: return String(localized: "posture_open")
        case .half: return String(localized: "posture_half")
        case .closed: return String(localized: "posture_closed")
        case .unknown: return String(localized: "posture_unknown")
        }
    }
}
