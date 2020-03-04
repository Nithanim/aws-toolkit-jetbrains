// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.ColumnInfo
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream
import software.aws.toolkits.resources.message
import java.awt.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

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


class CloudWatchLogsStreamsColumn : ColumnInfo<LogStream, String>("log streams <change this is not localized>") {
    override fun valueOf(item: LogStream?): String? = item?.logStreamName()
}

class CloudWatchLogsStreamsColumnDate : ColumnInfo<LogStream, String>(message("general.time")) {
    override fun valueOf(item: LogStream?): String? {
        item ?: return null
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(item.lastEventTimestamp()).atOffset(ZoneOffset.UTC))
    }
}

