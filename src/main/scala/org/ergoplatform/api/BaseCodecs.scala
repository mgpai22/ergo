package org.ergoplatform.api

import cats.syntax.either._
import io.circe._
import io.circe.syntax._
import org.ergoplatform.settings.Algos
import scorex.core.ModifierId
import scorex.crypto.authds.ADKey
import sigmastate.Values.{EvaluatedValue, Value}
import sigmastate.serialization.ValueSerializer
import sigmastate.{SBoolean, SType}

import scala.util.Try

trait BaseCodecs {

  def fromTry[T](tryResult: Try[T], cursor: ACursor): Either[DecodingFailure, T] = {
    tryResult.fold(e => Left(DecodingFailure(e.toString, cursor.history)), Right.apply)
  }

  def fromOption[T](maybeResult: Option[T], cursor: ACursor, msg: => String): Either[DecodingFailure, T] = {
    maybeResult.fold[Either[DecodingFailure, T]](Left(DecodingFailure(msg, cursor.history)))(Right.apply)
  }

  def fromThrows[T](throwsResult: T, cursor: ACursor): Either[DecodingFailure, T] = {
    Either.catchNonFatal(throwsResult).leftMap(e => DecodingFailure(e.toString, cursor.history))
  }

  implicit val bytesEncoder: Encoder[Array[Byte]] =  Algos.encode(_).asJson

  implicit val bytesDecoder: Decoder[Array[Byte]] = bytesDecoder(x => x)

  implicit val byteSeqEncoder: Encoder[IndexedSeq[Byte]] = Algos.encode(_).asJson

  implicit val modifierIdEncoder: Encoder[ModifierId] = _.array.asJson

  implicit val modifierIdDecoder: Decoder[ModifierId] = bytesDecoder(ModifierId @@ _)

  implicit val adKeyEncoder: Encoder[ADKey] = _.array.asJson

  implicit val adKeyDecoder: Decoder[ADKey] = bytesDecoder(ADKey @@  _)

  def bytesDecoder[T](transform: Array[Byte] => T): Decoder[T] = { cursor =>
    for {
      str <- cursor.as[String]
      bytes <- fromTry(Algos.decode(str), cursor)
    } yield transform(bytes)
  }

  implicit val valueEncoder: Encoder[Value[SType]] = { value =>
    ValueSerializer.serialize(value).asJson
  }

  implicit val booleanValueEncoder: Encoder[Value[SBoolean.type]] = { value =>
    valueEncoder(value)
  }

  implicit val booleanValueDecoder: Decoder[Value[SBoolean.type]] = {
    valueDecoder(_.asInstanceOf[Value[SBoolean.type]])
  }

  implicit val evaluatedValueDecoder: Decoder[EvaluatedValue[SType]] = {
    valueDecoder(_.asInstanceOf[EvaluatedValue[SType]])
  }

  def valueDecoder[T](transform: Value[SType] => T): Decoder[T]  = { cursor: ACursor =>
    cursor.as[Array[Byte]] flatMap { bytes =>
      fromThrows(transform(ValueSerializer.deserialize(bytes)), cursor)
    }
  }

}
