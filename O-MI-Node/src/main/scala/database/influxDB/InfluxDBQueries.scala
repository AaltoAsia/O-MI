package database
package influxDB

import java.sql.Timestamp

trait InfluxQuery{
  def query: String
}
case class CreateDB( name: String ) extends InfluxQuery  {
  def query: String = s"CREATE DATABASE $name"
}
case object ShowDBs extends InfluxQuery {
  def query: String = s"SHOW DATABASES"
}
case class DropMeasurement( name: String)  extends InfluxQuery {
  def query: String = s"""DROP MEASUREMENT "$name""""
}

case class SelectValue(
  measurement: String,
  whereClause: Option[WhereClause],
  orderByClause: Option[OrderByClause],
  limitClause: Option[LimitClause]
) extends InfluxQuery {
  def query: String = {
    val select = s"""SELECT value FROM "$measurement"""" 
    val selectWhere = whereClause.map{
      clause => 
        select + " " + clause.clause
    }.getOrElse( select )
    val selectWhereOrdered = orderByClause.map{
      clause => 
        selectWhere + " " + clause.clause
    }.getOrElse( selectWhere )
    limitClause.map{
      clause => 
        selectWhereOrdered + " " + clause.clause
    }.getOrElse( selectWhereOrdered )

  }
}

trait Clause {
  def clause: String
}
case class WhereClause(
                        expressions: Seq[Expression]
) extends Clause {
  def clause: String = {
    if( expressions.nonEmpty ) "WHERE "+ expressions.map(_.expression).mkString( " AND " )
    else ""
  }
}
case class LimitClause(
                        n: Int
) extends Clause {
  def clause: String = s"LIMIT $n"
}
trait OrderByClause extends Clause {
  def by: String
  def direction: String
  final def clause: String = s"ORDER BY $by $direction"
}
trait DescOrderByClause extends OrderByClause {
  final val direction: String = "DESC"
}
case class DescTimeOrderByClause() extends DescOrderByClause {
  final def by: String = "time"
}

trait Expression {
  def expression: String
}
case class UpperTimeBoundExpression( end: Timestamp ) extends Expression {
  def expression: String = s"time <= '$end'"
}
case class LowerTimeBoundExpression( begin: Timestamp ) extends Expression {
  def expression: String = s"time >= '$begin'"
}

