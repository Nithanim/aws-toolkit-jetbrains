// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindowManager
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindowType
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor.CloudWatchLogGroup
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor.CloudWatchLogStream
import software.aws.toolkits.resources.message

class CloudWatchLogWindow(private val project: Project) {
    private val toolWindow = ToolkitToolWindowManager.getInstance(project, CW_LOGS_TOOL_WINDOW)

    fun showLogGroup(logGroup: String) {
        val existingWindow = toolWindow.find(logGroup)
        if (existingWindow != null) {
            runInEdt {
                existingWindow.show()
            }
            return
        }
        val group = CloudWatchLogGroup(project, logGroup)
        runInEdt {
            toolWindow.addTab(title = group.title, component = group.content, activate = true, id = logGroup)
        }
    }

    fun showLogStream(logGroup: String, logStream: String, fromHead: Boolean = true, title: String? = null) {
        val client = project.awsClient<CloudWatchLogsClient>()
        //val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).apply { setViewer(true) }.console
        val id = "$logGroup/$logStream"
        val displayName = title ?: id
        // dispose existing window if it exists to update. TODO fix this massive hack
        val existingWindow = toolWindow.find(id)
        if (existingWindow != null) {
            runInEdt {
                existingWindow.dispose()
            }
        }
        val group = CloudWatchLogStream(client, logGroup, logStream, fromHead)
        runInEdt {
            toolWindow.addTab(displayName, group.content, activate = true, id = id, disposer = group)
        }
    }

    fun showLogStreamAround(logGroup: String, logStream: String, startTime: Long, timeScale: Long) {
        val client = project.awsClient<CloudWatchLogsClient>()
        val id = "$logGroup/$logStream $startTime$timeScale"
        // dispose existing window if it exists to update. TODO fix this massive hack
        val existingWindow = toolWindow.find(id)
        if (existingWindow != null) {
            runInEdt {
                existingWindow.dispose()
            }
        }
        val group = CloudWatchLogStream(client, logGroup, logStream, false, startTime, timeScale)
        runInEdt {
            toolWindow.addTab(id, group.content, activate = true, id = id, disposer = group)
        }
    }

    companion object {
        private val CW_LOGS_TOOL_WINDOW = ToolkitToolWindowType("AWS.CloudWatchLog", message("cloudwatch.logs.toolwindow"))
        fun getInstance(project: Project) = ServiceManager.getService(project, CloudWatchLogWindow::class.java)
    }
}
