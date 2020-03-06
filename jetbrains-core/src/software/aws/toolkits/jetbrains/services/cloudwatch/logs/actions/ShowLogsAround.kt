// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.table.TableView
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogWindow

class ShowLogsAround(
    private val logGroup: String,
    private val logStream: String,
    private val treeTable: TableView<OutputLogEvent>
) :
    AnAction("Show logs around <LOCALIZE THIS>", "abc <localize this>", AllIcons.Ide.Link), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val window = CloudWatchLogWindow.getInstance(project)
        val selectedObject = treeTable.selectedObject ?: return
        // 1 minute for now
        window.showLogStream(logGroup, logStream, true, null, selectedObject.timestamp(), 60 * 1000)
    }
}
