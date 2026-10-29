import CoreMotion
import Foundation

/// 기기를 일정 시간 만지지도 움직이지도 않으면 onIdle, 다시 움직이거나 터치하면 onActive.
final class IdleWatcher: ObservableObject {
    private let motion = CMMotionManager()
    private var last: CMAcceleration?
    private var idle = false
    private var work: DispatchWorkItem?
    private var timeout: TimeInterval = 180
    private var onIdle: (() -> Void)?
    private var onActive: (() -> Void)?

    func start(timeout: TimeInterval, onIdle: @escaping () -> Void, onActive: @escaping () -> Void) {
        self.timeout = timeout
        self.onIdle = onIdle
        self.onActive = onActive
        last = nil
        if motion.isAccelerometerAvailable {
            motion.accelerometerUpdateInterval = 0.2
            motion.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
                guard let self, let a = data?.acceleration else { return }
                if let l = self.last {
                    let diff = abs(a.x - l.x) + abs(a.y - l.y) + abs(a.z - l.z)
                    if diff > 0.1 { self.activity() } // 약 1 m/s² 변화
                }
                self.last = a
            }
        }
        reschedule()
    }

    func stop() {
        motion.stopAccelerometerUpdates()
        work?.cancel()
    }

    func activity() {
        if idle {
            idle = false
            onActive?()
        }
        reschedule()
    }

    private func reschedule() {
        work?.cancel()
        guard timeout > 0 else { return }
        let w = DispatchWorkItem { [weak self] in
            self?.idle = true
            self?.onIdle?()
        }
        work = w
        DispatchQueue.main.asyncAfter(deadline: .now() + timeout, execute: w)
    }
}
