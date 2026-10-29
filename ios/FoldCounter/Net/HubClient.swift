import Foundation

/// PC 허브와의 통신. 같은 네트워크 안에서만 동작하며 외부 서버는 없습니다. (안드로이드 앱과 같은 API)
actor HubClient {
    enum HubError: LocalizedError {
        case badUrl, http(Int, String)
        var errorDescription: String? {
            switch self {
            case .badUrl: return "bad url"
            case .http(let c, let t): return "HTTP \(c) \(t)"
            }
        }
    }

    private let prefs = Prefs.shared
    private let osVersion: String
    private let model: String
    private var baseUrl: String?
    private var registered = false
    private var pending: Int
    private var pendingSetCount: Int?
    private var kicked = false
    private var task: Task<Void, Never>?

    init(osVersion: String, model: String) {
        self.osVersion = osVersion
        self.model = model
        pending = Prefs.shared.pendingFolds
    }

    func start() {
        guard task == nil else { return }
        task = Task { await loop() }
    }

    func resetConnection() { baseUrl = nil; registered = false; kick() }
    func renamed() { registered = false; kick() }

    /// 접힘 1회. 끊겨 있어도 모아두었다가 연결되면 한꺼번에 보냅니다.
    func onFold() {
        pending += 1
        prefs.pendingFolds = pending
        let n = pending
        Task { @MainActor in AppState.shared.setPending(n) }
        kick()
    }

    func pushCount(_ n: Int) { pendingSetCount = n; kick() }

    private func kick() { kicked = true }

    private func waitKick(ms: Int) async {
        var left = ms
        while left > 0 && !kicked {
            try? await Task.sleep(for: .milliseconds(250))
            left -= 250
        }
        kicked = false
    }

    private func loop() async {
        while !Task.isCancelled {
            do {
                let url: String
                if let b = baseUrl {
                    url = b
                } else if let d = await discover() {
                    url = d
                } else {
                    await setStatus(.searching, nil)
                    await waitKick(ms: 3000)
                    continue
                }
                baseUrl = url
                if !registered { try await register(url) }
                if let n = pendingSetCount {
                    _ = try await http("POST", "\(url)/api/device/\(prefs.deviceId)", ["count": n])
                    pendingSetCount = nil
                }
                try await flush(url)
                try await heartbeat(url)
                await setStatus(.connected, url)
                await waitKick(ms: 5000)
            } catch {
                let msg = String(format: String(localized: "log_hub_error"), error.localizedDescription)
                await MainActor.run { AppState.shared.addLog(msg) }
                await setStatus(.error, baseUrl)
                baseUrl = nil
                registered = false
                await waitKick(ms: 2000)
            }
        }
    }

    private func setStatus(_ s: AppState.HubStatus, _ url: String?) async {
        await MainActor.run { AppState.shared.setHub(s, url) }
    }

    // MARK: 검색
    private func discover() async -> String? {
        if prefs.hubMode == "manual", !prefs.hubHost.trimmingCharacters(in: .whitespaces).isEmpty {
            let u = "http://\(prefs.hubHost.trimmingCharacters(in: .whitespaces)):\(prefs.hubPort)"
            return await ping(u) ? u : nil
        }
        if let last = prefs.lastHubUrl, await ping(last) { return last }
        if let found = await udpDiscover() {
            prefs.lastHubUrl = found
            return found
        }
        return nil
    }

    private func ping(_ url: String) async -> Bool {
        guard let r = try? await http("GET", "\(url)/api/ping", nil) else { return false }
        return (r["hub"] as? String) == "foldcounter"
    }

    private func udpDiscover() async -> String? {
        await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .utility).async { cont.resume(returning: UdpDiscovery.find()) }
        }
    }

    // MARK: API
    private func register(_ url: String) async throws {
        let count = await MainActor.run { AppState.shared.count }
        let name = prefs.deviceName
        let body: [String: Any] = [
            "deviceId": prefs.deviceId, "name": name, "model": model, "manufacturer": "Apple",
            "platform": "ios", "osVersion": osVersion, "detector": "window",
            "appVersion": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "", "count": count,
        ]
        let r = try await http("POST", "\(url)/api/register", body)
        registered = true
        await adopt(r["device"] as? [String: Any])
        let msg = String(format: String(localized: "log_hub_connected"), url)
        await MainActor.run { AppState.shared.addLog(msg) }
    }

    private func flush(_ url: String) async throws {
        let n = pending
        guard n > 0 else { return }
        let r = try await http("POST", "\(url)/api/fold", ["deviceId": prefs.deviceId, "delta": n])
        pending -= n
        prefs.pendingFolds = pending
        let left = pending
        await MainActor.run { AppState.shared.setPending(left) }
        await adopt(r["device"] as? [String: Any])
    }

    private func heartbeat(_ url: String) async throws {
        let posture = await MainActor.run { AppState.shared.posture.rawValue.uppercased() }
        let r = try await http("POST", "\(url)/api/heartbeat", ["deviceId": prefs.deviceId, "detector": "window", "posture": posture])
        await adopt(r["device"] as? [String: Any])
    }

    /// 허브가 기준입니다. 단, 아직 못 보낸 접힘이 있으면 덮어쓰지 않습니다.
    private func adopt(_ d: [String: Any]?) async {
        guard let d, pending == 0, pendingSetCount == nil else { return }
        let c = d["count"] as? Int
        let name = d["displayName"] as? String
        await MainActor.run { AppState.shared.adoptFromHub(count: c, displayName: name) }
    }

    private func http(_ method: String, _ url: String, _ body: [String: Any]?) async throws -> [String: Any] {
        guard let u = URL(string: url) else { throw HubError.badUrl }
        var req = URLRequest(url: u)
        req.httpMethod = method
        req.timeoutInterval = 3
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let b = body {
            req.httpBody = try JSONSerialization.data(withJSONObject: b)
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else { throw HubError.http(code, String(decoding: data, as: UTF8.self)) }
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
}

/// UDP 브로드캐스트로 허브를 찾습니다. (허브가 "FOLDCOUNTER_HUB <포트>" 로 응답)
enum UdpDiscovery {
    static let port: UInt16 = 47777
    static let magic = "FOLDCOUNTER_DISCOVER"
    static let reply = "FOLDCOUNTER_HUB"

    static func find() -> String? {
        let fd = socket(AF_INET, SOCK_DGRAM, 0)
        guard fd >= 0 else { return nil }
        defer { close(fd) }
        var yes: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &yes, socklen_t(MemoryLayout<Int32>.size))
        var tv = timeval(tv_sec: 1, tv_usec: 500_000)
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))

        let payload = Array(magic.utf8)
        var targets = broadcastAddresses()
        targets.append(in_addr(s_addr: inet_addr("255.255.255.255")))
        for ip in targets {
            var addr = sockaddr_in()
            addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
            addr.sin_family = sa_family_t(AF_INET)
            addr.sin_port = port.bigEndian
            addr.sin_addr = ip
            _ = withUnsafePointer(to: &addr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                    sendto(fd, payload, payload.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }
        }

        var buf = [UInt8](repeating: 0, count: 256)
        let deadline = Date().addingTimeInterval(1.5)
        while Date() < deadline {
            var from = sockaddr_in()
            var fromLen = socklen_t(MemoryLayout<sockaddr_in>.size)
            let n = withUnsafeMutablePointer(to: &from) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                    recvfrom(fd, &buf, buf.count, 0, $0, &fromLen)
                }
            }
            if n <= 0 { break }
            let msg = String(decoding: buf[0..<n], as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
            if msg.hasPrefix(reply) {
                let port = Int(msg.split(separator: " ").last ?? "") ?? 8787
                let ip = String(cString: inet_ntoa(from.sin_addr))
                return "http://\(ip):\(port)"
            }
        }
        return nil
    }

    private static func broadcastAddresses() -> [in_addr] {
        var out: [in_addr] = []
        var ifap: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifap) == 0, let first = ifap else { return out }
        defer { freeifaddrs(ifap) }
        var p: UnsafeMutablePointer<ifaddrs>? = first
        while let cur = p {
            let flags = Int32(cur.pointee.ifa_flags)
            if let addr = cur.pointee.ifa_addr, addr.pointee.sa_family == sa_family_t(AF_INET),
               (flags & IFF_BROADCAST) != 0, (flags & IFF_UP) != 0, let dst = cur.pointee.ifa_dstaddr {
                dst.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { out.append($0.pointee.sin_addr) }
            }
            p = cur.pointee.ifa_next
        }
        return out
    }
}
