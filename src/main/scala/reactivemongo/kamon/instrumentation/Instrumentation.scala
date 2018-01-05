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
import kamon.context.Key
import kamon.mongo.ReactiveMongo
import kamon.trace.{Span, SpanCustomizer}
import kamon.util.CallingThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Aspect, Pointcut}
import reactivemongo.api.collections.{GenericCollection, InsertOps}
import reactivemongo.api.{Cursor, FlattenedCursor, SerializationPack, WrappedCursor}

import scala.concurrent.Future

@Aspect
class Instrumentation {

  private object A {
    private val keyName = "a"
    val ContextKey: Key[A.type] = Key.local(keyName, null)
  }

  //cursor operations
  @Pointcut("execution(scala.concurrent.Future reactivemongo.api.Cursor+.*(..)) && this(cursor)")
  def onCursorMethodThatReturnsFuture(cursor: Cursor[_]): Unit = {}

  @Around("onCursorMethodThatReturnsFuture(cursor)")
  def aroundCursorMethodThatReturnsFuture(pjp: ProceedingJoinPoint, cursor: Cursor[_]): Any = {
    val collectionName = cursorCollectionName(cursor)
    track(pjp, collectionName, ReactiveMongo.generateOperationName(cursor, collectionName))
  }

  //insert
  @Pointcut("call(* reactivemongo.api.collections.InsertOps$InsertBuilder$class.*execute(..)) && args(insertBuilder, ..)")
  def onInsertBuilderExecute[P <: SerializationPack with Singleton](insertBuilder: InsertOps[P]#InsertBuilder[_]): Unit = {}

  @Around("onInsertBuilderExecute(insertBuilder)")
  def aroundInsertBuilderExecute[P <: SerializationPack with Singleton](pjp: ProceedingJoinPoint, insertBuilder: InsertOps[P]#InsertBuilder[_]): Any = {
    val collectionName = insertBuilder.getClass().getDeclaredMethod("reactivemongo$api$collections$InsertOps$InsertBuilder$$$outer").invoke(insertBuilder).asInstanceOf[GenericCollection[_]].fullCollectionName
    track(pjp, collectionName, s"insert_$collectionName" /* ReactiveMongo.generateOperationName(insertBuilder, collectionName)*/)
  }

  private def track(pjp: ProceedingJoinPoint, collectionName: String, generateOperationName: => String): Future[_] = {
    val currentContext = Kamon.currentContext()
    val currentA = currentContext.get(A.ContextKey)
    val clientSpan = currentContext.get(Span.ContextKey)

    if(currentA != null) {
//      println(s"no A ${pjp.getSignature}")
      pjp.proceed().asInstanceOf[Future[_]]
    } else if (clientSpan.isEmpty()) {
//      println(s"no span ${pjp.getSignature}")
      pjp.proceed().asInstanceOf[Future[_]]
    }
    else {
      Kamon.withContext(currentContext.withKey(A.ContextKey, A)) {
//        println(s"before $collectionName ${pjp.getSignature}")
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
//            println("after")

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

  @scala.annotation.tailrec
  private def cursorCollectionName(cursor: Cursor[_]): String = {
    cursor match {
      case c: reactivemongo.api.DefaultCursor.Impl[_] => c.fullCollectionName
      case f: FlattenedCursor[_] => "FlattenedCursor unknown collection"
      case w: WrappedCursor[_] => cursorCollectionName(w.wrappee)
    }
  }
}
