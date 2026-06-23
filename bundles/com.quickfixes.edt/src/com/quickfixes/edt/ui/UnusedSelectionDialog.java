package com.quickfixes.edt.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import com.quickfixes.edt.core.UnusedCollector.UnusedItem;

/**
 * Lets the user pick which unused methods/variables to delete: a checkbox table (all checked by
 * default) with "select all / deselect all" buttons. The checked items are available via
 * {@link #getSelected()} after OK.
 */
public class UnusedSelectionDialog extends TitleAreaDialog
{
    private static final int SELECT_ALL_ID = IDialogConstants.CLIENT_ID + 1;
    private static final int DESELECT_ALL_ID = IDialogConstants.CLIENT_ID + 2;

    private final List<UnusedItem> items;
    private CheckboxTableViewer viewer;
    private List<UnusedItem> selected = Collections.emptyList();

    /**
     * @param shell parent shell
     * @param items the unused items to offer
     */
    public UnusedSelectionDialog(Shell shell, List<UnusedItem> items)
    {
        super(shell);
        this.items = items;
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
        setTitle("Удаление неиспользуемого"); //$NON-NLS-1$
        setMessage("Отметьте, что удалить (по умолчанию отмечено всё)."); //$NON-NLS-1$

        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        container.setLayout(layout);

        Table table =
            new Table(container, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = 320;
        tableData.widthHint = 560;
        table.setLayoutData(tableData);

        viewer = new CheckboxTableViewer(table);

        TableViewerColumn kindColumn = new TableViewerColumn(viewer, SWT.LEFT);
        kindColumn.getColumn().setText("Тип"); //$NON-NLS-1$
        kindColumn.getColumn().setWidth(110);
        kindColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((UnusedItem)element).kind;
            }
        });

        TableViewerColumn nameColumn = new TableViewerColumn(viewer, SWT.LEFT);
        nameColumn.getColumn().setText("Имя"); //$NON-NLS-1$
        nameColumn.getColumn().setWidth(330);
        nameColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((UnusedItem)element).name;
            }
        });

        TableViewerColumn lineColumn = new TableViewerColumn(viewer, SWT.RIGHT);
        lineColumn.getColumn().setText("Строка"); //$NON-NLS-1$
        lineColumn.getColumn().setWidth(80);
        lineColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return String.valueOf(((UnusedItem)element).line);
            }
        });

        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setInput(items);
        viewer.setAllChecked(true);
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, SELECT_ALL_ID, "Выделить все", false); //$NON-NLS-1$
        createButton(parent, DESELECT_ALL_ID, "Снять все", false); //$NON-NLS-1$
        createButton(parent, IDialogConstants.OK_ID, "Удалить", true); //$NON-NLS-1$
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == SELECT_ALL_ID)
        {
            viewer.setAllChecked(true);
            return;
        }
        if (buttonId == DESELECT_ALL_ID)
        {
            viewer.setAllChecked(false);
            return;
        }
        super.buttonPressed(buttonId);
    }

    @Override
    protected void okPressed()
    {
        List<UnusedItem> result = new ArrayList<>();
        for (Object checked : viewer.getCheckedElements())
        {
            result.add((UnusedItem)checked);
        }
        selected = result;
        super.okPressed();
    }

    /** @return the items the user checked (empty until OK). */
    public List<UnusedItem> getSelected()
    {
        return selected;
    }
}
