package database
import java.sql.Timestamp
import parsing.Types.Path
/***
 * DataFormater holds methods for formating data to correct form
 */
object DataFormater {
  /***
   * Method to convert database "raw" data so that it corresponds to the data
   * that would've been sent if a callback address were provided.
   * Can have null values in the beginning if no data was received before the subscription 
   * 
   *
   * @param path sensors path as Path
   * @param starttime Timestamp for subscription start time
   * @param interval in seconds
   *
   * @return Array of DBSensors that represents the values that would've been
   * sent if callback address were provided 
   *
   */
def FormatSubData(path:Path,starttime:Timestamp,interval:Double):Array[DBSensor] =
{
  var rawdata = SQLite.getNBetween(path, Some(starttime), None, None,None)
  var deltaTime = new java.util.Date().getTime - starttime.getTime
  val intervalMillis = (1000*interval).toLong
  var formatedData = Array.ofDim[DBSensor]((deltaTime/intervalMillis).toInt)

  if(rawdata.isEmpty)
  {
    //found no data after subscription was set
    rawdata = SQLite.getNBetween(path, None, None, None,None)
    if(rawdata.isEmpty)
    {
      //found no data at all for this path
      return Array[DBSensor]()
    }
    //use the latest value since no new data has been recorded during subscription
    //and fill all data using that value
    var lastval = rawdata.last
    for(n <- 0 until formatedData.length)
    {
      formatedData(n) = new DBSensor(lastval.path,lastval.value,lastval.time)
    }
  }
  else
  {
   var formatedIndex = 0
   var compareTime = new Timestamp(starttime.getTime + intervalMillis)
   for(n <- 0 until rawdata.length)
    {
     //loop through all raw data and determine a correct position for it in the formated data
     //if multiple values fit in the same spot in the formatedData, use newer data.
      if(rawdata(n).time.getTime <= compareTime.getTime)
      {
        //found data that fits in the current spot
        formatedData(formatedIndex) = rawdata(n)
      }
      else
      { 
        //data is older than our current spot so move onwards
        if(formatedIndex < formatedData.length-1)
        {
        formatedIndex += 1
        }
        formatedData(formatedIndex) = rawdata(n)
        compareTime = new Timestamp(starttime.getTime + intervalMillis*(formatedIndex + 1).toLong)
      }
    }
  //Try to fill gaps in FormatedData
  //if gap(null) is found fill in using previous value
  //if first is null search for older values in database
  //if no older values, use null. Therefore returned data can start with 
  // one or more null values
  for(n<-0 until formatedData.length)
  {
    if(formatedData(n)==null)
    {
      if(n==0)
      {
        rawdata = SQLite.getNBetween(path, None, Some(starttime), None,None)
        if(!rawdata.isEmpty)
        {
          formatedData(n) = rawdata.last
        }
      }
      else
      {
        formatedData(n) = formatedData(n-1)
      }
    }
  }
  }
  formatedData
}
}
