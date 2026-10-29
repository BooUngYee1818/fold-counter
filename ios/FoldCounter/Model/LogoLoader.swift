import UIKit

/// 번들에 logo.png 가 있으면 녹색(크로마키) 배경을 투명하게 만들어 돌려줍니다. 없으면 nil (글자만 표시).
enum LogoLoader {
    static let image: UIImage? = {
        guard let src = UIImage(named: "logo"), let cg = src.cgImage else { return nil }
        return keyOutGreen(cg).map { UIImage(cgImage: $0, scale: src.scale, orientation: src.imageOrientation) }
    }()

    static func keyOutGreen(_ cg: CGImage) -> CGImage? {
        let w = cg.width, h = cg.height
        var px = [UInt8](repeating: 0, count: w * h * 4)
        let cs = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: cs,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var i = 0
        while i < px.count {
            let r = Int(px[i]), g = Int(px[i + 1]), b = Int(px[i + 2])
            let greenness = g - max(r, b)
            if greenness > 60 {
                px[i] = 0; px[i + 1] = 0; px[i + 2] = 0; px[i + 3] = 0
            } else if greenness > 20 {
                let a = max(0.0, 1.0 - Double(greenness - 20) / 40.0)
                px[i] = UInt8(Double(px[i]) * a); px[i + 1] = UInt8(Double(px[i + 1]) * a); px[i + 2] = UInt8(Double(px[i + 2]) * a)
                px[i + 3] = UInt8(Double(px[i + 3]) * a)
            }
            i += 4
        }
        guard let out = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: cs,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        return out.makeImage()
    }
}
