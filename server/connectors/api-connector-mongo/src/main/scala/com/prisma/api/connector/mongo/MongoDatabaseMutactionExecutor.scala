package com.prisma.api.connector.mongo

import com.prisma.api.connector._
import com.prisma.gc_values._
import org.mongodb.scala.bson.{BsonArray, BsonBoolean, BsonDateTime, BsonDouble, BsonInt32, BsonString, BsonTransformer, BsonValue}
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}

import scala.concurrent.{ExecutionContext, Future}
object GCBisonTransformer {

  implicit object GCValueBsonTransformer extends BsonTransformer[GCValue] {
    override def apply(value: GCValue): BsonValue = value match {
      case StringGCValue(v)   => BsonString(v)
      case IntGCValue(v)      => BsonInt32(v)
      case FloatGCValue(v)    => BsonDouble(v)
      case JsonGCValue(v)     => BsonString(v.toString())
      case EnumGCValue(v)     => BsonString(v)
      case CuidGCValue(v)     => BsonString(v)
      case UuidGCValue(v)     => BsonString(v.toString)
      case DateTimeGCValue(v) => BsonDateTime(v.getMillis)
      case BooleanGCValue(v)  => BsonBoolean(v)
      case ListGCValue(list)  => BsonArray(list.map(x => GCValueBsonTransformer(x)))
      case NullGCValue        => null
      case _: RootGCValue     => sys.error("not implemented")
    }
  }
}

class MongoDatabaseMutactionExecutor(database: MongoDatabase)(implicit ec: ExecutionContext) extends DatabaseMutactionExecutor {
  override def executeTransactionally(mutaction: TopLevelDatabaseMutaction): Future[MutactionResults] = {
    mutaction match {
      case create: TopLevelCreateNode => executeTopLevelMutaction(create)
      case _                          => sys.error("Not implemented")
    }
  }

  override def executeNonTransactionally(mutaction: TopLevelDatabaseMutaction): Future[MutactionResults] = ???

  def executeTopLevelMutaction(mutaction: TopLevelCreateNode) = {
    val model = mutaction.model

    val collection: MongoCollection[Document] = database.getCollection(model.name)

    import GCBisonTransformer._

    val id = CuidGCValue.random()

    val nonListValues = Document(
      model.scalarNonListFields
        .filter(field => mutaction.nonListArgs.hasArgFor(field) && mutaction.nonListArgs.getFieldValue(field.name).get != NullGCValue)
        .map(field => field.name -> mutaction.nonListArgs.getFieldValue(field).get) :+ "_id" -> id)

    collection.insertOne(nonListValues).toFuture().map(_ => MutactionResults(CreateNodeResult(id, mutaction), Vector.empty))
  }
}
