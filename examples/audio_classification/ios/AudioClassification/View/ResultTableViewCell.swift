
import UIKit
import TensorFlowLiteTaskAudio

/// TableViewCell to display the inference results. Each cell corresponds to a single category.
class ResultTableViewCell: UITableViewCell {

  @IBOutlet weak var nameLabel: UILabel!
  @IBOutlet weak var scoreWidthLayoutConstraint: NSLayoutConstraint!

  func setData(_ data: ClassificationCategory) {
    nameLabel.text = data.label
    if !data.score.isNaN {
      // score view width is equal 1/4 screen with
      scoreWidthLayoutConstraint.constant = UIScreen.main.bounds.width/4*CGFloat(data.score)
    } else {
      scoreWidthLayoutConstraint.constant = 0
    }
  }
}
