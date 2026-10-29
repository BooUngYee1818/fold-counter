import SwiftUI
import UIKit

/// 검은 화면에 숫자만. 메뉴는 화면을 빠르게 5번 탭하면 열립니다.
/// iOS 는 앱이 버튼을 막을 수 없으므로 사용법 유도(Guided Access)가 꺼져 있으면 안내를 띄우고, 켜지면 안내가 사라집니다.
struct CounterView: View {
    @EnvironmentObject var app: AppState
    @StateObject private var idle = IdleWatcher()
    @State private var taps: [Date] = []
    @State private var showMenu = false
    @State private var showReset = false
    @State private var showSettings = false
    @State private var showDiag = false
    @State private var showCredit = false
    @State private var hintVisible = true
    private let prefs = Prefs.shared

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Text("\(app.count)")
                .font(.system(size: 420, weight: .black, design: .rounded))
                .minimumScaleFactor(0.08)
                .lineLimit(1)
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 70)

            VStack(spacing: 0) {
                if showCredit {
                    creditPanel.padding(.top, 24).transition(.opacity)
                } else if prefs.showName {
                    Text(app.deviceName).font(.title3).foregroundStyle(.gray).padding(.top, 28).lineLimit(1)
                }
                Spacer()
                if !app.guidedAccess { guidedBanner }
                HStack(spacing: 8) {
                    Circle().fill(statusColor).frame(width: 10, height: 10)
                    Text(statusText).font(.caption2).foregroundStyle(Color(white: 0.4)).lineLimit(1)
                    Spacer()
                    if hintVisible { Text("counter_hint").font(.caption2).foregroundStyle(Color(white: 0.4)) }
                }
                .padding(20)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { idle.activity(); onTap() }
        .confirmationDialog("app_name", isPresented: $showMenu, titleVisibility: .visible) {
            Button("menu_diagnostics") { showDiag = true }
            Button("menu_settings") { showSettings = true }
            Button("menu_test_fold") { app.simulateFold() }
            Button("menu_reset", role: .destructive) { showReset = true }
            Button("cancel", role: .cancel) {}
        }
        .alert("reset_title", isPresented: $showReset) {
            Button("yes", role: .destructive) { app.reset() }
            Button("cancel", role: .cancel) {}
        } message: {
            Text("reset_msg")
        }
        .sheet(isPresented: $showSettings) { SettingsView() }
        .sheet(isPresented: $showDiag) { DiagnosticsView() }
        .onAppear {
            idle.start(timeout: Double(prefs.idleMinutes) * 60,
                       onIdle: { withAnimation(.easeInOut(duration: 0.5)) { showCredit = true } },
                       onActive: { withAnimation(.easeInOut(duration: 0.3)) { showCredit = false } })
            DispatchQueue.main.asyncAfter(deadline: .now() + 6) { withAnimation { hintVisible = false } }
        }
        .onDisappear { idle.stop() }
        .onChange(of: app.count) { _, _ in idle.activity() }
    }

    // 가만히 두면 위쪽에 뜨는 제작자 표시 (프로그램명 위, 제작자 아래)
    private var creditPanel: some View {
        VStack(spacing: 6) {
            if let logo = LogoLoader.image {
                Image(uiImage: logo).resizable().scaledToFit().frame(height: 90)
            }
            Text("app_name").font(.title3.bold()).foregroundStyle(.white)
            Text(prefs.creditText.isEmpty ? String(localized: "credit_default") : prefs.creditText)
                .font(.subheadline).foregroundStyle(.gray)
        }
        .padding(18)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(white: 0.07).opacity(0.85)))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.2), lineWidth: 1))
    }

    private var guidedBanner: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "lock.open").foregroundStyle(.yellow)
                Text("guided_banner").font(.footnote).foregroundStyle(.yellow)
            }
            HStack {
                Text("guided_first_time").font(.caption2).foregroundStyle(Color(white: 0.6))
                Spacer()
                Button("open_settings") { openSettings() }.font(.caption).buttonStyle(.bordered)
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.yellow.opacity(0.08)))
        .padding(.horizontal, 16)
    }

    private var statusColor: Color {
        switch app.hubStatus {
        case .connected: return .green
        case .searching: return .yellow
        case .error: return .red
        }
    }

    private var statusText: String {
        var s = app.localizedHubStatus + "  ·  " + String(localized: "detector_window")
        if app.guidedAccess { s += "  ·  " + String(localized: "guided_on") }
        if app.pending > 0 { s += "  ·  +\(app.pending)" }
        return s
    }

    private func onTap() {
        let now = Date()
        taps.append(now)
        if taps.count > 5 { taps.removeFirst(taps.count - 5) }
        if taps.count == 5, now.timeIntervalSince(taps[0]) < 1.5 {
            taps.removeAll()
            showMenu = true
        }
    }

    private func openSettings() {
        // 손쉬운 사용 페이지로 바로 가는 주소는 공식 지원이 아니라 안 될 수 있어 설정 앱 열기로 대체합니다.
        if let u = URL(string: "App-prefs:ACCESSIBILITY"), UIApplication.shared.canOpenURL(u) {
            UIApplication.shared.open(u)
        } else if let u = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(u)
        }
    }
}
