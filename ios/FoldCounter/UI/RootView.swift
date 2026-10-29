import SwiftUI

struct RootView: View {
    @EnvironmentObject var app: AppState

    var body: some View {
        GeometryReader { geo in
            Group {
                if app.setupDone { CounterView() } else { SetupView() }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
            .onAppear {
                app.start()
                app.reportWindowSize(geo.size)
            }
            .onChange(of: geo.size) { _, s in app.reportWindowSize(s) }
        }
        .ignoresSafeArea()
    }
}
