import SwiftUI
import UIKit

/// 어떤 신호로 접힘을 판정하는지, 허브 연결 상태, 최근 이벤트를 바로 확인하는 화면.
struct DiagnosticsView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(body_)
                    .font(.system(.footnote, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
                    .padding()
            }
            .navigationTitle("diag_title")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("done") { dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    HStack {
                        Button("diag_reconnect") { app.reconnect() }
                        Button("diag_test") { app.simulateFold() }
                    }
                }
            }
        }
    }

    private var body_: String {
        let l = { (k: String) in String(localized: String.LocalizationValue(k)) }
        return """
        \(l("diag_detection")): \(l("detector_window"))
        \(l("diag_detail")): \(app.detectorDetail.isEmpty ? "-" : app.detectorDetail)
        \(l("diag_posture")): \(app.localizedPosture)
        \(l("guided_on")): \(app.guidedAccess)

        \(l("diag_hub")): \(app.localizedHubStatus)
        \(l("diag_pending")): \(app.pending)
        \(l("diag_count")): \(app.count)
        \(l("diag_device_id")): \(Prefs.shared.deviceId)
        \(l("diag_model")): Apple \(DeviceNames.modelIdentifier) · iOS \(UIDevice.current.systemVersion)

        \(l("diag_events")):
        \(app.log.isEmpty ? "-" : app.log.joined(separator: "\n"))
        """
    }
}
