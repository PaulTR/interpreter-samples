
import UIKit
import Sketch

class ViewController: UIViewController {

  @IBOutlet weak var resultLabel: UILabel!
  @IBOutlet weak var sketchView: SketchView!

  var interpreterHelper: InterpreterHelper?

  override func viewDidLoad() {
    super.viewDidLoad()
    setupSketchView()
    interpreterHelper = InterpreterHelper(modelPath: DefaultConstants.modelPath)
  }

  func setupSketchView() {
    sketchView.lineWidth = 30
    sketchView.backgroundColor = UIColor.black
    sketchView.lineColor = UIColor.white
    sketchView.sketchViewDelegate = self
  }

  private func classifyDrawing() {
    guard let interpreterHelper = interpreterHelper else { return }

    // Capture drawing to RGB file.
    UIGraphicsBeginImageContext(sketchView.frame.size)
    sketchView.layer.render(in: UIGraphicsGetCurrentContext()!)
    let drawing = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext();

    guard let image = drawing else {
      resultLabel.text = "Invalid drawing."
      return
    }

    // Run digit classifier.
    guard let result = interpreterHelper.proccess(image: image) else {
      resultLabel.text = "Failed to classify drawing."
      return
    }
    resultLabel.text = String(format: "Digit: %d  Inference: %.2fms", result.digit, result.inferenceTime)
  }

  /// Clear drawing canvas and result text when tapping Clear button.
  @IBAction func clearButtonTouchupInside(_ sender: Any) {
    sketchView.clear()
    resultLabel.text = "Please draw a digit."
  }
}

extension ViewController: SketchViewDelegate {
  func drawView(_ view: SketchView, didEndDrawUsingTool tool: AnyObject) {
    classifyDrawing()
  }
}

