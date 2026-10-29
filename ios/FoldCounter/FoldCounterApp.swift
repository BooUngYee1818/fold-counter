import SwiftUI

@main
struct FoldCounterApp: App {
    @StateObject private var app = AppState.shared
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .preferredColorScheme(.dark)
                .statusBarHidden(true)
                .persistentSystemOverlays(.hidden)
                .onAppear { UIApplication.shared.isIdleTimerDisabled = true } // 화면 꺼짐 방지
        }
        .onChange(of: scenePhase) { _, phase in
            app.scenePhaseChanged(phase)
        }
    }
}
