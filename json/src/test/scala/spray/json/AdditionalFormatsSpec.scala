/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.json

import org.scalatest._
import Matchers._

class AdditionalFormatsSpec extends WordSpec {

  case class Container[A](inner: Option[A])

  object ReaderProtocol extends DefaultJsonProtocol {
    implicit def containerReader[T: JsonFormat] = lift {
      new JsonReader[Container[T]] {
        def read(value: JsValue) = value match {
          case JsObject(fields) if fields.contains("content") => Container(Some(JsonReader[T].read(fields("content"))))
          case _ => deserializationError("Unexpected format: " + value.toString)
        }
      }
    }
  }

  object WriterProtocol extends DefaultJsonProtocol {
    implicit def containerWriter[T: JsonFormat] = lift {
      new JsonWriter[Container[T]] {
        def write(obj: Container[T]) = JsObject("content" -> obj.inner.toJson)
      }
    }
  }

  "The liftJsonWriter" should {
    val obj = Container(Some(Container(Some(List(1, 2, 3)))))

    "properly write a Container[Container[List[Int]]] to JSON" in {
      import WriterProtocol._
      obj.toJson.toString shouldEqual """{"content":{"content":[1,2,3]}}"""
    }

    "properly read a Container[Container[List[Int]]] from JSON" in {
      import ReaderProtocol._
      """{"content":{"content":[1,2,3]}}""".parseJson.convertTo[Container[Container[List[Int]]]] shouldEqual obj
    }
  }

}
