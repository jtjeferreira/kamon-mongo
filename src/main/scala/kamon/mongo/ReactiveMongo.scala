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

package kamon
package mongo

import com.typesafe.config.Config
import kamon.util.DynamicAccess
import reactivemongo.api.Cursor

object ReactiveMongo {
  @volatile private var nameGenerator: NameGenerator = new DefaultNameGenerator()

  loadConfiguration(Kamon.config())

  def generateOperationName(cursor: Cursor[_], collectionName: String): String =
    nameGenerator.generateOperationName(cursor, collectionName)

  private def loadConfiguration(config: Config): Unit = {
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val nameGeneratorFQCN = config.getString("kamon.mongo.name-generator")
    nameGenerator =  dynamic.createInstanceFor[NameGenerator](nameGeneratorFQCN, Nil).get
  }

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit =
      ReactiveMongo.loadConfiguration(newConfig)
  })
}

trait NameGenerator {
  def generateOperationName(cursor: Cursor[_], collectionName: String): String
}

class DefaultNameGenerator extends NameGenerator {
  override def generateOperationName(cursor: Cursor[_], collectionName: String): String = s"cursor_$collectionName"
}
