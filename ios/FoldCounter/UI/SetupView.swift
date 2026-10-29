import SwiftUI

/// 첫 실행: 기기 이름 확인, 감지 방식 안내, 허브 연결 확인 후 카운터 화면으로.
struct SetupView: View {
    @EnvironmentObject var app: AppState
    @State private var name = Prefs.shared.deviceName.isEmpty ? DeviceNames.detect() : Prefs.shared.deviceName
    @State private var host = Prefs.shared.hubHost
    @State private var port = String(Prefs.shared.hubPort)
    private let prefs = Prefs.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("app_name").font(.system(size: 30, weight: .bold)).foregroundStyle(.white)
                Text("\(String(localized: "credit_prefix")) \(prefs.creditText.isEmpty ? String(localized: "credit_default") : prefs.creditText)")
                    .font(.footnote).foregroundStyle(.gray)
                Text("setup_desc").font(.subheadline).foregroundStyle(.gray).padding(.top, 6)

                label("setup_name_label")
                TextField("", text: $name)
                    .font(.title3).foregroundStyle(.white)
                    .padding(10).background(Color(white: 0.12)).cornerRadius(8)

                label("setup_detector_label")
                Text("detector_window").foregroundStyle(.white)
                Text("setup_detector_note").font(.footnote).foregroundStyle(.yellow)

                label("setup_hub_label")
                HStack(spacing: 8) {
                    Circle().fill(statusColor).frame(width: 10, height: 10)
                    Text(app.localizedHubStatus).foregroundStyle(.white)
                }

                label("setup_manual_title")
                HStack {
                    TextField(String(localized: "setup_host_hint"), text: $host)
                        .keyboardType(.numbersAndPunctuation).autocorrectionDisabled()
                        .padding(10).background(Color(white: 0.12)).cornerRadius(8)
                    TextField(String(localized: "setup_port_hint"), text: $port)
                        .keyboardType(.numberPad).frame(width: 90)
                        .padding(10).background(Color(white: 0.12)).cornerRadius(8)
                }
                HStack {
                    Button("setup_manual_connect") {
                        let h = host.trimmingCharacters(in: .whitespaces)
                        guard !h.isEmpty else { return }
                        prefs.hubMode = "manual"; prefs.hubHost = h; prefs.hubPort = Int(port) ?? 8787
                        app.reconnect()
                    }.buttonStyle(.bordered)
                    Button("setup_auto_connect") { prefs.hubMode = "auto"; app.reconnect() }.buttonStyle(.bordered)
                }

                Text("setup_privacy").font(.caption).foregroundStyle(.gray).padding(.top, 8)

                Button {
                    let n = name.trimmingCharacters(in: .whitespaces)
                    app.finishSetup(name: n.isEmpty ? DeviceNames.detect() : n)
                } label: {
                    Text("setup_start").font(.title3.bold()).frame(maxWidth: .infinity).padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent).padding(.top, 12)
            }
            .padding(24)
            .padding(.top, 40)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func label(_ key: LocalizedStringKey) -> some View {
        Text(key).font(.footnote).foregroundStyle(.gray).padding(.top, 14)
    }

    private var statusColor: Color {
        switch app.hubStatus {
        case .connected: return .green
        case .searching: return .yellow
        case .error: return .red
        }
    }
}
