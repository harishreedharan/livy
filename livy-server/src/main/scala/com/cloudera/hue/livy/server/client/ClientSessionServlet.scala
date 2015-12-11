/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.hue.livy.server.client

import java.io.{ObjectInputStream, ByteArrayInputStream}
import java.util.Base64

import com.cloudera.hue.livy.client.Job
import com.cloudera.hue.livy.server.SessionServlet
import com.cloudera.hue.livy.sessions.SessionManager
import com.cloudera.hue.livy.spark.client._
import org.json4s.JValue
import org.json4s.JsonAST.JNull

class ClientSessionServlet(sessionManager: SessionManager[ClientSession])
  extends SessionServlet[ClientSession](sessionManager) {

  val base64Decoder = Base64.getDecoder

  private def getJob[Serializable](jobString: String): Job[Serializable] = {
    val bais = new ByteArrayInputStream(base64Decoder.decode(jobString))
    new ObjectInputStream(bais).readObject().asInstanceOf[Job[Serializable]]
  }

  post("/:id/submit-job") {
    sessionManager.get(params("id").toInt) match {
      case Some(session) =>
        session.submitJob(getJob(parsedBody.extract[SubmitJob].job))
      case None =>
    }
  }

  post("/:id/run-job") {
    sessionManager.get(params("id").toInt) match {
      case Some(session) =>
        val jobId = session.runJob(getJob(parsedBody.extract[RunJob].job))
        JobSubmitted(jobId)
      case None =>
    }
  }

  post("/:id/add-jar") {
    sessionManager.get(params("id").toInt) match {
      case Some(session) =>
        session.addJar(parsedBody.extract[AddJar].uri)
      case None =>
    }
  }

  post("/:id/add-file") {
    sessionManager.get(params("id").toInt) match {
      case Some(session) =>
        session.addFile(parsedBody.extract[AddFile].uri)
      case None =>
    }
  }

  post("/:id/job-status") {
    sessionManager.get(params("id").toInt) match {
      case Some(session) =>
      case None =>
    }
  }

  override protected def serializeSession(session: ClientSession): JValue = {
    JNull
  }
}
