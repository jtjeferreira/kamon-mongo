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

package kamon.mongo.instrumentation

import kamon.Kamon
import kamon.trace.{Span, SpanCustomizer}
import kamon.util.CallingThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Aspect, Pointcut}
import reactivemongo.core.protocol.{CheckedWriteRequest, RequestMaker}

import scala.concurrent.{ExecutionContext, Future}

@Aspect
class Instrumentation2 {

  @Pointcut("execution(* reactivemongo.api.Cursor+.headOption(..))")
  def onCursorHeadOption(): Unit = {}

  @Around("onCursorHeadOption()")
  def aroundCursorHeadOption(pjp: ProceedingJoinPoint): Any = {
    a(pjp)
  }

  @Pointcut("execution(* reactivemongo.api.Cursor+.collect(..))")
  def onCursorCollect(): Unit = {}

  @Around("onCursorCollect()")
  def aroundCursorCollect(pjp: ProceedingJoinPoint): Any = {
    a(pjp)
  }

  private def a(pjp: ProceedingJoinPoint): Any = {
    val currentContext = Kamon.currentContext()
    val clientSpan = currentContext.get(Span.ContextKey)

    if (clientSpan.isEmpty()) {
      println("no span")
      pjp.proceed()
    }
    else {
      println("before")
      val clientSpanBuilder = Kamon.buildSpan(""/*Play.generateHttpClientOperationName(request)*/)
        .asChildOf(clientSpan)
        .withTag("span.kind", "client")
      //        .withTag("http.method", request.method)
      //        .withTag("http.url", request.uri.toString)

      val clientRequestSpan = currentContext.get(SpanCustomizer.ContextKey)
        .customize(clientSpanBuilder)
        .start()

      val newContext = currentContext.withKey(Span.ContextKey, clientRequestSpan)
      val responseFuture = pjp.proceed().asInstanceOf[Future[_]]

      responseFuture.transform(
        s = response => {
          //          if(isError(response.status))
          //            clientRequestSpan.addError("error")
          //
          //          if(response.status == StatusCodes.NotFound)
          //            clientRequestSpan.setOperationName("not-found")

          println("after")

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
