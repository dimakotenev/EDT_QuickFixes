package com.quickfixes.edt.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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

/**
 * Generic single-column checkbox picker: shows a list of items (all checked by default) with
 * "select all / deselect all" buttons; the checked items are available via {@link #getSelected()}
 * after OK.
 *
 * @param <T> the item type
 */
public class CheckboxListDialog<T> extends TitleAreaDialog
{
    private static final int SELECT_ALL_ID = IDialogConstants.CLIENT_ID + 1;
    private static final int DESELECT_ALL_ID = IDialogConstants.CLIENT_ID + 2;

    private final String title;
    private final String message;
    private final String columnHeader;
    private final String okLabel;
    private final List<T> items;
    private final Function<T, String> labelProvider;

    private CheckboxTableViewer viewer;
    private List<T> selected = Collections.emptyList();

    /**
     * @param shell parent shell
     * @param title title-area heading
     * @param message title-area message
     * @param columnHeader the single column's header
     * @param items the items to offer
     * @param labelProvider item -&gt; display text
     * @param okLabel label for the confirm button
     */
    public CheckboxListDialog(Shell shell, String title, String message, String columnHeader, List<T> items,
        Function<T, String> labelProvider, String okLabel)
    {
        super(shell);
        this.title = title;
        this.message = message;
        this.columnHeader = columnHeader;
        this.items = items;
        this.labelProvider = labelProvider;
        this.okLabel = okLabel;
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

        Table table =
            new Table(container, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = 320;
        tableData.widthHint = 520;
        table.setLayoutData(tableData);

        viewer = new CheckboxTableViewer(table);
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.LEFT);
        column.getColumn().setText(columnHeader);
        column.getColumn().setWidth(480);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            @SuppressWarnings("unchecked")
            public String getText(Object element)
            {
                return labelProvider.apply((T)element);
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
        createButton(parent, IDialogConstants.OK_ID, okLabel, true);
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
    @SuppressWarnings("unchecked")
    protected void okPressed()
    {
        List<T> result = new ArrayList<>();
        for (Object checked : viewer.getCheckedElements())
        {
            result.add((T)checked);
        }
        selected = result;
        super.okPressed();
    }

    /** @return the checked items (empty until OK). */
    public List<T> getSelected()
    {
        return selected;
    }
}
