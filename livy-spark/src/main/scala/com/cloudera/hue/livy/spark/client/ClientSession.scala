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
package com.cloudera.hue.livy.spark.client

import java.io.Serializable
import java.net.URI
import java.util.concurrent
import java.util.concurrent.{ExecutionException, TimeUnit}
import java.util.concurrent.atomic.AtomicLong

import com.cloudera.hue.livy.client.{Job, SparkClient}
import com.cloudera.hue.livy.sessions.{Session, SessionState}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

class ClientSession(val sessionId: Int, createRequest: CreateClientRequest) extends Session {
  implicit val executionContext = ExecutionContext.global
  var sessionState: SessionState = SessionState.Starting()
  SessionClientTracker.createClient(
    sessionId, createRequest.sparkConf.asJava, createRequest.timeout)
  sessionState = SessionState.Running()

  private val operations = scala.collection.mutable.Map[Long, java.util.concurrent.Future[Serializable]]()
  private val operationCounter = new AtomicLong(0)

  def getClient: Option[SparkClient] = {
    SessionClientTracker.getClient(id)
  }

  def runJob(job: Job[Serializable]): Long = {
    performOperation(client => client.run(job))
  }

  def submitJob(job: Job[Serializable]): Long = {
    performOperation(client => client.submit(job))
  }

  def addFile(uri: URI): Unit = {
    getClient.foreach(_.addFile(uri))
  }

  def addJar(uri: URI): Unit = {
    getClient.foreach(_.addJar(uri))
  }

  def jobStatus(id: Long) = {
    val future = operations(id)
    if (future.isDone) {
      JobCompleted
    } else {
      try {
        JobResult(id, future.get(1, TimeUnit.SECONDS))
      } catch {
        case NonFatal(e) =>
          JobFailed(id)
      }
    }
  }

  private def performOperation(m: (SparkClient => concurrent.Future[Serializable])): Long = {
    getClient.foreach { client =>
      val future = m(client)
      val opId = operationCounter.incrementAndGet()
      operations(opId) = future
      opId
    }
    -1
  }

  override def id: Int = sessionId

  override def stop(): Future[Unit] = {
    Future {
      sessionState = SessionState.ShuttingDown()
      SessionClientTracker.closeSession(id)
      sessionState = SessionState.Dead()
    }
  }

  // TODO: Add support for this in RSC
  override def logLines(): IndexedSeq[String] = {
    null
  }

  override def state: SessionState = sessionState
}
