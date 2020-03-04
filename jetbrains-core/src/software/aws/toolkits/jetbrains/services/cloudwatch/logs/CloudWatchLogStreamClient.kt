// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs

import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent

class CloudWatchLogStreamClient(
    private val client: CloudWatchLogsClient,
    private val logGroup: String,
    private val logStream: String
) : CoroutineScope by CoroutineScope(CoroutineName("CloudWatchLogsStream")), Disposable {
    private var nextBackwardToken: String? = null
    private var nextForwardToken: String? = null

    private fun loadInitial(request: GetLogEventsRequest, callback: ((List<OutputLogEvent>) -> Unit)) {
        launch {
            val response = client.getLogEvents(request)
            val events = response.events().filterNotNull()
            nextForwardToken = response.nextForwardToken()
            nextBackwardToken = response.nextBackwardToken()
            callback(events)
        }
    }

    fun loadInitialAround(startTime: Long, timeScale: Long, callback: ((List<OutputLogEvent>) -> Unit)) {
        loadInitial(
            GetLogEventsRequest
                .builder()
                .logGroupName(logGroup)
                .logStreamName(logStream)
                .startTime(startTime - timeScale)
                .endTime(startTime + timeScale).build(),
            callback
        )
    }

    fun loadInitial(fromHead: Boolean, callback: ((List<OutputLogEvent>) -> Unit)) {
        loadInitial(GetLogEventsRequest.builder().logGroupName(logGroup).logStreamName(logStream).startFromHead(fromHead).build(), callback)
    }

    // TODO implement
    fun loadMoreForward(callback: (List<OutputLogEvent>) -> Unit) {
        if (coroutineContext[Job]?.children?.firstOrNull() == null) {
            launch {
                streamMore(callback)
            }
        }
    }

    // TODO implement
    fun loadMoreBackward(callback: (List<OutputLogEvent>) -> Unit) {
        if (coroutineContext[Job]?.children?.firstOrNull() == null) {
            launch {
                streamMore(callback)
            }
        }
    }

    fun startStreaming(callback: ((List<OutputLogEvent>) -> Unit)) {
        if (coroutineContext[Job]?.children?.firstOrNull() == null) {
            launch {
                while (true) {
                    streamMore(callback)
                    delay(1000L)
                }
            }
        }
    }

    fun pauseStreaming() {
        if (coroutineContext[Job]?.children?.firstOrNull() != null) {
            coroutineContext[Job]?.cancelChildren()
        }
    }

    private fun streamMore(callback: ((List<OutputLogEvent>) -> Unit)) {
        val response = client.getLogEvents {
            it
                .logGroupName(logGroup)
                .logStreamName(logStream)
                // required by nextToken
                .startFromHead(true)
                .nextToken(nextForwardToken)
                .build()
        }
        val newEvents = response.events().filterNotNull()
        // Streaming is a forward event
        nextForwardToken = response.nextForwardToken()
        callback(newEvents)
    }

    override fun dispose() {
        pauseStreaming()
        cancel()
    }
}
