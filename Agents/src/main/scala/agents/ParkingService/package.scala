package agents

import java.sql.Timestamp
import java.util.Date

package object parkingService{

  def currentTime = new Timestamp( new Date().getTime / 1000)
} 
