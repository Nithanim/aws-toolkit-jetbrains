// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.ui.components.JBTextArea
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream
import software.aws.toolkits.resources.message
import java.awt.Component
import javax.swing.JTable
import javax.swing.SortOrder
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableRowSorter

class CloudWatchLogsStreamsColumn : ColumnInfo<LogStream, String>(message("cloudwatch.logs.log_streams")) {
    override fun valueOf(item: LogStream?): String? = item?.logStreamName()
}

class CloudWatchLogsStreamsColumnDate : ColumnInfo<LogStream, String>(message("cloudwatch.logs.last_event_time")) {
    override fun valueOf(item: LogStream?): String? {
        val timestamp = item?.lastEventTimestamp() ?: return null
        val date = DateFormatUtil.getDateFormat().format(timestamp)
        val time = DateFormatUtil.getTimeFormat().format(timestamp)
        return "$date $time"
    }
}

class LogGroupTableSorter(model: ListTableModel<LogStream>) : TableRowSorter<ListTableModel<LogStream>>(model) {
    init {
        sortKeys = listOf(SortKey(1, SortOrder.DESCENDING))
        setSortable(0, false)
        setSortable(1, false)
    }
}

object WrapCellRenderer : JBTextArea(), TableCellRenderer {
    init {
        lineWrap = true
        wrapStyleWord = true
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        text = value.toString().trimEnd()
        setSize(table.columnModel.getColumn(column).width, preferredSize.height)
        if (table.getRowHeight(row) != preferredSize.height) {
            table.setRowHeight(row, preferredSize.height)
        }
        return this
    }
}

