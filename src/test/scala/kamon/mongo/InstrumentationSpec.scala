/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.mongo

import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import kamon.Kamon
import kamon.context.Context.create
import kamon.testkit._
import kamon.trace.Span
import kamon.trace.Span.TagValue
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, MustMatchers, OptionValues, WordSpec}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


class InstrumentationSpec extends WordSpec with MustMatchers
  with ScalaFutures
  with Eventually
  with IntegrationPatience
  with SpanSugar
  with BeforeAndAfterAll
  with MetricInspection
  with Reconfigure
  with OptionValues
  with SpanReporter
  with DockerTestKit
  with DockerKitSpotify
  with DockerMongodbService {

  import reactivemongo.api._

  val driver = MongoDriver.apply()
  lazy val db = driver.connection("mongodb://localhost:27017").get.database("test")
  def collection: Future[BSONCollection] = db.map(_.collection[BSONCollection]("collection"))
  def collectionName = "test.collection"

  override def beforeAll(): Unit = {
    super.beforeAll()

    collection.flatMap { c =>
      c.insert(BSONDocument("key" -> "value"))
    }.futureValue
  }

  "the instrumentation" should {
    "propagate the current context and generate a span inside a cursor headOption" in {
      val okSpan = Kamon.buildSpan("chupa").start()

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = collection.flatMap(x => x.find(BSONDocument.empty).cursor().headOption)
        Await.result(response, Duration.Inf) mustBe defined
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe s"cursor_$collectionName"
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("component") mustBe TagValue.String("reactivemongo")
        span.tags("reactivemongo.collection") mustBe TagValue.String(collectionName)
      }

      reporter.nextSpan() mustBe empty

    }

    "propagate the current context and generate a span inside a cursor head" in {
      val okSpan = Kamon.buildSpan("chupa").start()

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = collection.flatMap(x => x.find(BSONDocument.empty).cursor().head).map(Option.apply)
        Await.result(response, Duration.Inf) mustBe defined
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe s"cursor_$collectionName"
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("component") mustBe TagValue.String("reactivemongo")
        span.tags("reactivemongo.collection") mustBe TagValue.String(collectionName)
      }

      reporter.nextSpan() mustBe empty

    }

    "propagate the current context and generate a span inside a cursor collect" in {
      val okSpan = Kamon.buildSpan("chupa").start()

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = collection.flatMap(x => x.find(BSONDocument.empty).cursor().collect[List](maxDocs = 1, err =Cursor.FailOnError[List[BSONDocument]]()))
        Await.result(response, Duration.Inf).headOption mustBe defined
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe s"cursor_$collectionName"
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("component") mustBe TagValue.String("reactivemongo")
        span.tags("reactivemongo.collection") mustBe TagValue.String(collectionName)
      }

      reporter.nextSpan() mustBe empty
    }

    "propagate the current context and generate a span inside a cursor foldResponses" in {
      val okSpan = Kamon.buildSpan("chupa").start()

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = collection.flatMap(x => x.find(BSONDocument.empty).cursor().foldResponses(List.empty[BSONDocument], maxDocs = -1){
          case (docs, r) => Cursor.Cont(docs ++ reactivemongo.core.protocol.Response.parse(r).toList)
        })
        response.futureValue.headOption mustBe defined
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe s"cursor_$collectionName"
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("component") mustBe TagValue.String("reactivemongo")
        span.tags("reactivemongo.collection") mustBe TagValue.String(collectionName)

        span.marks.map(_.key) must contain("nextRequest")
      }

      reporter.nextSpan() mustBe empty
    }

    "propagate the current context and generate a span inside a cursor foldResponses with kill" in {
      val okSpan = Kamon.buildSpan("chupa").start()

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = collection.flatMap(x => x.find(BSONDocument.empty).cursor().foldResponses(List.empty[BSONDocument], maxDocs = -1){
          case (docs, r) => Cursor.Done(docs ++ reactivemongo.core.protocol.Response.parse(r).toList)
        })
        response.futureValue.headOption mustBe defined
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe s"cursor_$collectionName"
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("component") mustBe TagValue.String("reactivemongo")
        span.tags("reactivemongo.collection") mustBe TagValue.String(collectionName)

        span.marks.map(_.key) must contain("kill")
      }

      reporter.nextSpan() mustBe empty
    }

    "propagate the current context and generate a span inside an insert" in {
      val okSpan = Kamon.buildSpan("chupa").start()

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = collection.flatMap(x => x.insert(BSONDocument.empty)).map(_ => true).recover{case _ => false}
        response.futureValue
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe s"Insert_$collectionName"
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("component") mustBe TagValue.String("reactivemongo")
        span.tags("reactivemongo.collection") mustBe TagValue.String(collectionName)
      }

      reporter.nextSpan() mustBe empty
    }

    "propagate the current context and generate a span inside an update" in {
      val okSpan = Kamon.buildSpan("chupa").start()

      Kamon.withContext(create(Span.ContextKey, okSpan)) {
        val response = collection.flatMap(x => x.update(BSONDocument.empty, BSONDocument.empty)).map(_ => true).recover{case _ => false}
        response.futureValue
      }

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName mustBe s"Update_$collectionName"
        span.tags("span.kind") mustBe TagValue.String("client")
        span.tags("component") mustBe TagValue.String("reactivemongo")
        span.tags("reactivemongo.collection") mustBe TagValue.String(collectionName)
      }

      reporter.nextSpan() mustBe empty
    }

  }
}


