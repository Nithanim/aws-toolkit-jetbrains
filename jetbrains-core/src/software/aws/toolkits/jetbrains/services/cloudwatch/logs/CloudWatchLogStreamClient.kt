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

    private fun load(
        request: GetLogEventsRequest,
        saveForwardToken: Boolean,
        saveBackwardToken: Boolean,
        callback: ((List<OutputLogEvent>) -> Unit)
    ) = launch {
        val response = client.getLogEvents(request)
        val events = response.events().filterNotNull()
        if (saveForwardToken) {
            nextForwardToken = response.nextForwardToken()
        }
        if (saveBackwardToken) {
            nextBackwardToken = response.nextBackwardToken()
        }
        callback(events)
    }

    fun loadInitialAround(startTime: Long, timeScale: Long, callback: ((List<OutputLogEvent>) -> Unit)) {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startTime(startTime - timeScale)
            .endTime(startTime + timeScale)
            .build()
        load(request, saveForwardToken = true, saveBackwardToken = true, callback = callback)
    }

    fun loadInitial(fromHead: Boolean, callback: ((List<OutputLogEvent>) -> Unit)) {
        launch {
            val request = GetLogEventsRequest.builder().logGroupName(logGroup).logStreamName(logStream).startFromHead(fromHead).build()
            load(request, saveForwardToken = true, saveBackwardToken = true, callback = callback)
        }
    }

    // TODO fix this coroutineContext hack
    fun loadMoreForward(callback: (List<OutputLogEvent>) -> Unit) {
        if (coroutineContext[Job]?.children?.firstOrNull() == null) {
            val request = GetLogEventsRequest
                .builder()
                .logGroupName(logGroup)
                .logStreamName(logStream)
                .startFromHead(true)
                .nextToken(nextForwardToken)
                .build()
            load(request, saveForwardToken = true, saveBackwardToken = false, callback = callback)
        }
    }

    // TODO fix this coroutineContext hack
    fun loadMoreBackward(callback: (List<OutputLogEvent>) -> Unit) {
        if (coroutineContext[Job]?.children?.firstOrNull() == null) {
            val request = GetLogEventsRequest
                .builder()
                .logGroupName(logGroup)
                .logStreamName(logStream)
                .startFromHead(true)
                .nextToken(nextBackwardToken)
                .build()
            load(request, saveForwardToken = false, saveBackwardToken = true, callback = callback)
        }
    }

    fun startStreaming(callback: ((List<OutputLogEvent>) -> Unit)) {
        if (coroutineContext[Job]?.children?.firstOrNull() == null) {
            launch {
                while (true) {
                    loadMoreForward(callback)
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

    override fun dispose() {
        pauseStreaming()
        cancel()
    }
}
