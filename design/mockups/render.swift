// Renders design/mockups/src/*.html → design/mockups/*.png with WebKit (no browser needed).
// Usage: swift design/mockups/render.swift <srcDir> <outDir>
import Cocoa
import WebKit

let args = CommandLine.arguments
let src = URL(fileURLWithPath: args[1], isDirectory: true)
let out = URL(fileURLWithPath: args[2], isDirectory: true)
let W: CGFloat = 393, H: CGFloat = 873, SCALE: CGFloat = 3

var pages = (try! FileManager.default.contentsOfDirectory(at: src, includingPropertiesForKeys: nil))
  .filter { $0.pathExtension == "html" }
  .sorted { $0.lastPathComponent < $1.lastPathComponent }

final class Renderer: NSObject, WKNavigationDelegate {
  let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: W * SCALE, height: H * SCALE), styleMask: [.borderless], backing: .buffered, defer: false)
  let web = WKWebView(frame: NSRect(x: 0, y: 0, width: W * SCALE, height: H * SCALE))
  var current: URL?

  override init() {
    super.init()
    web.navigationDelegate = self
    web.pageZoom = SCALE
    window.contentView = web
    window.setFrameOrigin(NSPoint(x: -5000, y: -5000))
    window.orderBack(nil)
  }

  func next() {
    guard !pages.isEmpty else { NSApp.terminate(nil); return }
    let page = pages.removeFirst()
    current = page
    web.loadFileURL(page, allowingReadAccessTo: src)
  }

  func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
    webView.callAsyncJavaScript("await document.fonts.ready; return document.fonts.size", arguments: [:], in: nil, in: .page) { _ in
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { self.snap() }
    }
  }

  func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
    print("FAIL \(current!.lastPathComponent): \(error)"); next()
  }

  func snap() {
    let cfg = WKSnapshotConfiguration()
    cfg.rect = NSRect(x: 0, y: 0, width: W * SCALE, height: H * SCALE)
    web.takeSnapshot(with: cfg) { image, err in
      guard let image = image, let cg = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
        print("SNAP FAIL \(self.current!.lastPathComponent): \(String(describing: err))"); self.next(); return
      }
      let name = self.current!.deletingPathExtension().lastPathComponent + ".png"
      let rep = NSBitmapImageRep(cgImage: cg)
      try! rep.representation(using: .png, properties: [:])!.write(to: out.appendingPathComponent(name))
      print("OK \(name) \(cg.width)x\(cg.height)")
      self.next()
    }
  }
}

let app = NSApplication.shared
app.setActivationPolicy(.prohibited)
let r = Renderer()
DispatchQueue.main.async { r.next() }
app.run()
