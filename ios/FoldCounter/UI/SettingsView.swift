import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    private let prefs = Prefs.shared

    @State private var name = Prefs.shared.deviceName
    @State private var manual = Prefs.shared.hubMode == "manual"
    @State private var host = Prefs.shared.hubHost
    @State private var port = String(Prefs.shared.hubPort)
    @State private var backgroundIsFold = Prefs.shared.backgroundIsFold
    @State private var showName = Prefs.shared.showName
    @State private var idleMinutes = Prefs.shared.idleMinutes
    @State private var credit = Prefs.shared.creditText.isEmpty ? String(localized: "credit_default") : Prefs.shared.creditText

    var body: some View {
        NavigationStack {
            Form {
                Section("settings_name") {
                    TextField("", text: $name)
                }
                Section("settings_hub") {
                    Picker("", selection: $manual) {
                        Text("settings_hub_auto").tag(false)
                        Text("settings_hub_manual").tag(true)
                    }
                    .pickerStyle(.inline).labelsHidden()
                    if manual {
                        TextField(String(localized: "setup_host_hint"), text: $host).keyboardType(.numbersAndPunctuation).autocorrectionDisabled()
                        TextField(String(localized: "setup_port_hint"), text: $port).keyboardType(.numberPad)
                    }
                }
                Section {
                    Toggle("settings_background_fold", isOn: $backgroundIsFold)
                    Text("settings_background_fold_note").font(.footnote).foregroundStyle(.secondary)
                } header: {
                    Text("settings_detection")
                }
                Section("settings_display") {
                    Toggle("settings_show_name", isOn: $showName)
                    Stepper("\(String(localized: "settings_idle")): \(idleMinutes)", value: $idleMinutes, in: 0...600)
                    TextField(String(localized: "settings_credit"), text: $credit)
                }
            }
            .navigationTitle("settings_title")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("save") { save() } }
            }
        }
    }

    private func save() {
        let n = name.trimmingCharacters(in: .whitespaces)
        if !n.isEmpty { prefs.deviceName = n }
        prefs.hubMode = manual ? "manual" : "auto"
        prefs.hubHost = host.trimmingCharacters(in: .whitespaces)
        prefs.hubPort = min(65535, max(1, Int(port) ?? 8787))
        prefs.backgroundIsFold = backgroundIsFold
        prefs.showName = showName
        prefs.idleMinutes = idleMinutes
        prefs.creditText = credit.trimmingCharacters(in: .whitespaces)
        app.settingsChanged()
        dismiss()
    }
}
