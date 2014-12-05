package fi.vm.sade.omatsivut.json

import fi.vm.sade.omatsivut.tulokset.HakutoiveenValintatulosTila
import org.json4s._
import org.json4s.ext.EnumNameSerializer

object JsonFormats {
  val genericFormats = DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
  val jsonFormats: Formats = JsonFormats.genericFormats ++ List(new QuestionNodeSerializer, new HakemusMuutosSerializer, new EnumNameSerializer(HakutoiveenValintatulosTila), new HakuSerializer, new KohteenHakuaikaSerializer)
}

trait JsonFormats {
  implicit val jsonFormats: Formats = JsonFormats.jsonFormats
}

