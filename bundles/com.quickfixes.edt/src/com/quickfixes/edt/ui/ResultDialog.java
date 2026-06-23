package com.quickfixes.edt.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import com.quickfixes.edt.core.OpResult;

/**
 * Read-only summary dialog: a table of successful items (green check + detail) and failed/skipped
 * items (red cross + reason). Reused by every Quick Fix that reports an {@link OpResult}.
 */
public class ResultDialog extends TitleAreaDialog
{
    private static final String CHECK = "✓"; //$NON-NLS-1$
    private static final String CROSS = "✗"; //$NON-NLS-1$

    private final String title;
    private final String message;
    private final String nameHeader;
    private final String detailHeader;
    private final OpResult result;

    private static final class Row
    {
        final boolean ok;
        final String name;
        final String detail;

        Row(boolean ok, String name, String detail)
        {
            this.ok = ok;
            this.name = name;
            this.detail = detail;
        }
    }

    /**
     * @param shell parent shell
     * @param title title-area heading
     * @param message title-area message (summary)
     * @param nameHeader header of the subject column
     * @param detailHeader header of the detail/reason column
     * @param result the outcome to display
     */
    public ResultDialog(Shell shell, String title, String message, String nameHeader, String detailHeader,
        OpResult result)
    {
        super(shell);
        this.title = title;
        this.message = message;
        this.nameHeader = nameHeader;
        this.detailHeader = detailHeader;
        this.result = result;
    }

    @Override
    protected boolean isResizable()
    {
        return true;
    }

    @Override
    protected void configureShell(Shell newShell)
    {
        super.configureShell(newShell);
        newShell.setText("Quick Fixes"); //$NON-NLS-1$
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite area = (Composite)super.createDialogArea(parent);
        setTitle(title);
        setMessage(message);

        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        container.setLayout(layout);

        TableViewer viewer =
            new TableViewer(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        Table table = viewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = 320;
        tableData.widthHint = 600;
        table.setLayoutData(tableData);

        final Color green = parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);
        final Color red = parent.getDisplay().getSystemColor(SWT.COLOR_RED);

        TableViewerColumn statusColumn = new TableViewerColumn(viewer, SWT.CENTER);
        statusColumn.getColumn().setText(""); //$NON-NLS-1$
        statusColumn.getColumn().setWidth(36);
        statusColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((Row)element).ok ? CHECK : CROSS;
            }

            @Override
            public Color getForeground(Object element)
            {
                return ((Row)element).ok ? green : red;
            }
        });

        TableViewerColumn nameColumn = new TableViewerColumn(viewer, SWT.LEFT);
        nameColumn.getColumn().setText(nameHeader);
        nameColumn.getColumn().setWidth(250);
        nameColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((Row)element).name;
            }
        });

        TableViewerColumn detailColumn = new TableViewerColumn(viewer, SWT.LEFT);
        detailColumn.getColumn().setText(detailHeader);
        detailColumn.getColumn().setWidth(300);
        detailColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((Row)element).detail;
            }
        });

        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setInput(buildRows());
        return area;
    }

    private List<Row> buildRows()
    {
        List<Row> rows = new ArrayList<>();
        for (OpResult.Entry entry : result.getSuccesses())
        {
            rows.add(new Row(true, entry.name, entry.detail));
        }
        for (OpResult.Entry entry : result.getFailures())
        {
            rows.add(new Row(false, entry.name, entry.reason));
        }
        return rows;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    }
}
