/*
 * =========================================================================================
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

package reactivemongo.kamon.instrumentation

import kamon.Kamon
import kamon.mongo.ReactiveMongo
import kamon.trace.{Span, SpanCustomizer}
import kamon.util.CallingThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Aspect, Pointcut}
import reactivemongo.api.commands.CommandWithResult
import reactivemongo.api.Collection

import scala.concurrent.Future

@Aspect
class Instrumentation {

  //minimal set of cursor operations (head or headOption or foldResponses or foldResponsesM)
  @Pointcut("(" +
    "execution(* reactivemongo.api.DefaultCursor.Impl.head(..)) || " +
    "execution(* reactivemongo.api.DefaultCursor.Impl.headOption(..)) || " +
    "execution(* reactivemongo.api.DefaultCursor.Impl.foldResponses(..)) ||" +
    "execution(* reactivemongo.api.DefaultCursor.Impl.foldResponsesM(..))" +
    ") && this(cursor)")
  def onCursorMethods(cursor: reactivemongo.api.DefaultCursor.Impl[_]): Unit = {}

  @Around("onCursorMethods(cursor)")
  def aroundCursorMethods(pjp: ProceedingJoinPoint, cursor: reactivemongo.api.DefaultCursor.Impl[_]): Any = {
    val collectionName = cursor.fullCollectionName
    track(pjp, collectionName, ReactiveMongo.generateOperationName(cursor, collectionName))
  }

  //TODO tag nextRequest and kill cursor

  //commands
  @Pointcut("execution(* reactivemongo.api.commands.Command.CommandWithPackRunner.apply(..)) && args(collection, command, ..)")
  def onCommandWithPackRunnerApply(collection: Collection, command: CommandWithResult[_]): Unit = {}

  @Around("onCommandWithPackRunnerApply(collection, command)")
  def aroundCommandWithPackRunnerApply(pjp: ProceedingJoinPoint, collection: Collection, command: CommandWithResult[_]): Any = {
    val collectionName = collection.fullCollectionName
    track(pjp, collectionName, ReactiveMongo.generateOperationName(collection, command))
  }

  private def track(pjp: ProceedingJoinPoint, collectionName: String, generateOperationName: => String): Future[_] = {
    val currentContext = Kamon.currentContext()
    val clientSpan = currentContext.get(Span.ContextKey)

    if (clientSpan.isEmpty()) {
//      println(s"no span ${pjp.getSignature}")
      pjp.proceed().asInstanceOf[Future[_]]
    }
    else {
//      println(s"before $collectionName ${pjp.getSignature}")
      val clientSpanBuilder = Kamon.buildSpan(generateOperationName)
        .asChildOf(clientSpan)
        .withTag("span.kind", "client")
        .withTag("component", "reactivemongo")
        .withTag("reactivemongo.collection", collectionName)

      val clientRequestSpan = currentContext.get(SpanCustomizer.ContextKey)
        .customize(clientSpanBuilder)
        .start()

      val responseFuture = pjp.proceed().asInstanceOf[Future[_]]

      responseFuture.transform(
        s = response => {
//        println("after")

          clientRequestSpan.finish()
          response
        },
        f = error => {
          clientRequestSpan.addError("error.object", error)
          clientRequestSpan.finish()
          error
        }
      )(CallingThreadExecutionContext)
    }
  }
}
