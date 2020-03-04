// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogStreamClient
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions.ShowLogsAround
import software.aws.toolkits.resources.message
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.table.TableCellRenderer

class CloudWatchLogStream(
    client: CloudWatchLogsClient,
    private val logGroup: String,
    private val logStream: String,
    fromHead: Boolean,
    startTime: Long? = null,
    timeScale: Long? = null
) : SimpleToolWindowPanel(false, false), Disposable {
    lateinit var content: JPanel
    lateinit var logsPanel: JPanel
    lateinit var searchLabel: JLabel
    lateinit var searchField: JTextField
    lateinit var showAsButton: JButton
    lateinit var unwrapButton: JButton
    lateinit var streamLogsOn: JButton
    lateinit var streamLogsOff: JButton
    private val defaultModel = ListTableModel<OutputLogEvent>(
        object : ColumnInfo<OutputLogEvent, String>(message("general.time")) {
            override fun valueOf(item: OutputLogEvent?): String? = DateTimeFormatter
                .ISO_LOCAL_DATE_TIME
                .format(Instant.ofEpochMilli(item?.timestamp() ?: 0).atOffset(ZoneOffset.UTC))
        },
        object : ColumnInfo<OutputLogEvent, String>("message <change this is not localized>") {
            override fun valueOf(item: OutputLogEvent?): String? = item?.message()
        }
    )
    private val wrappingModel = ListTableModel<OutputLogEvent>(defaultModel.columnInfos[0],
        object : ColumnInfo<OutputLogEvent, String>("message <change this is not localized>") {
            override fun valueOf(item: OutputLogEvent?): String? = item?.message()

            override fun getRenderer(item: OutputLogEvent?): TableCellRenderer? = WrapCellRenderer
        })
    private var logsTableView: TableView<OutputLogEvent> = TableView<OutputLogEvent>(defaultModel)
    private val logStreamClient = CloudWatchLogStreamClient(client, logGroup, logStream)

    init {
        // dispose logStreamClient when this is disposed
        Disposer.register(this, logStreamClient)
        logsTableView.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        val logsScrollPane = ScrollPaneFactory.createScrollPane(logsTableView)
        var ignoreNext = false
        logsScrollPane.verticalScrollBar.addAdjustmentListener {
            if (logsTableView.model.rowCount == 0) {
                return@addAdjustmentListener
            }
            if (ignoreNext) {
                ignoreNext = false
                return@addAdjustmentListener
            }
            if (logsScrollPane.verticalScrollBar.isAtBottom()) {
                logStreamClient.loadMoreForward {
                    if (it.isNotEmpty()) {
                        val events = logsTableView.tableViewModel.items.plus(it)
                        runInEdt { logsTableView.tableViewModel.items = events }
                    }
                }
            } else if (logsScrollPane.verticalScrollBar.isAtTop()) {
                logStreamClient.loadMoreBackward {
                    if (it.isNotEmpty()) {
                        val events = it.plus(logsTableView.tableViewModel.items)
                        runInEdt {
                            logsTableView.tableViewModel.items = events
                        }
                    }
                }
            }
        }
        logsPanel.add(logsScrollPane)
        if (startTime != null && timeScale != null) {
            logStreamClient.loadInitialAround(startTime, timeScale) {
                runInEdt {
                    logsTableView.tableViewModel.items = it
                    ignoreNext = true
                    // TODO remove this ridiculous hack
                    GlobalScope.launch {
                        delay(100)
                        logsScrollPane.verticalScrollBar.value = logsScrollPane.verticalScrollBar.maximum
                    }
                }
            }
        } else {
            logStreamClient.loadInitial(fromHead) { runInEdt { logsTableView.tableViewModel.items = it } }
        }
        setUpTemporaryButtons()
        addActions()
    }

    private fun setUpTemporaryButtons() {
        showAsButton.addActionListener {
            wrappingModel.items = logsTableView.tableViewModel.items
            logsTableView.setModelAndUpdateColumns(wrappingModel)
        }
        unwrapButton.addActionListener {
            defaultModel.items = logsTableView.tableViewModel.items
            logsTableView.setModelAndUpdateColumns(defaultModel)
        }
        streamLogsOn.addActionListener {
            //remove load more
            // launch thing
            logStreamClient.startStreaming {
                if (it.isNotEmpty()) {
                    val events = logsTableView.tableViewModel.items.plus(it)
                    runInEdt { logsTableView.tableViewModel.items = events }
                }
            }
        }
        streamLogsOff.addActionListener {
            logStreamClient.pauseStreaming()
        }
    }

    private fun addActions() {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(ShowLogsAround(logGroup, logStream, logsTableView))
        PopupHandler.installPopupHandler(
            logsTableView,
            actionGroup,
            ActionPlaces.EDITOR_POPUP,
            ActionManager.getInstance()
        )
    }

    override fun dispose() {}

    private fun JScrollBar.isAtBottom(): Boolean = value == (maximum - visibleAmount)
    private fun JScrollBar.isAtTop(): Boolean = value == minimum
}

