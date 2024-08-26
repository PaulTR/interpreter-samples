
import Foundation

enum Model: String, CaseIterable {
  case Yamnet = "YAMNet"
  case speechCommand = "Speech Command"

  var modelPath: String? {
      switch self {
      case .Yamnet:
          return Bundle.main.path(
              forResource: "yamnet", ofType: "tflite")
      case .speechCommand:
          return Bundle.main.path(
              forResource: "speech_commands", ofType: "tflite")
      }
  }
}


struct DefaultConstants {
  static var model: Model = .Yamnet
  static var overLap: Double = 0.5
  static var maxResults: Int = 3
  static var threshold: Float = 0.3
  static var threadCount: Int = 2
}
