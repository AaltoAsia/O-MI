package types.omi

import types.odf._

trait OdfRequest {

  def msgFormat: DataFormat
  def odf: ODF
  def replaceOdf(other: ODF): OdfRequest
}

sealed trait DataFormat
case object JSON extends DataFormat
case object XML extends DataFormat
case object BIN extends DataFormat
