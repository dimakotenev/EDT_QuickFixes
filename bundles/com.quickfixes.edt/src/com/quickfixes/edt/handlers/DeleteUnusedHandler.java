package com.quickfixes.edt.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;

import com._1c.g5.v8.dt.validation.marker.IMarkerManager;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;
import com.quickfixes.edt.Activator;
import com.quickfixes.edt.EdtServices;
import com.quickfixes.edt.core.MarkerScanner;
import com.quickfixes.edt.core.OpResult;
import com.quickfixes.edt.core.UnusedCollector;
import com.quickfixes.edt.core.UnusedCollector.UnusedItem;
import com.quickfixes.edt.ui.ResultDialog;
import com.quickfixes.edt.ui.UnusedSelectionDialog;

/**
 * "Quick Fixes → Удалить неиспользуемые методы и переменные…": shows a checkbox report of unused
 * methods ({@code module-unused-method}) and local variables ({@code module-unused-local-variable}),
 * then deletes the checked ones.
 */
public class DeleteUnusedHandler extends AbstractHandler
{
    private static final String TITLE = "Quick Fixes"; //$NON-NLS-1$

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        Shell shell = HandlerUtil.getActiveShell(event);
        XtextEditor xtextEditor = HandlerSupport.xtextEditor(HandlerUtil.getActiveEditor(event));
        if (xtextEditor == null)
        {
            MessageDialog.openInformation(shell, TITLE, "Активный редактор — не модуль BSL."); //$NON-NLS-1$
            return null;
        }
        final IXtextDocument document = xtextEditor.getDocument();
        IFile file = HandlerSupport.moduleFile(xtextEditor);
        if (file == null || !file.exists())
        {
            MessageDialog.openInformation(shell, TITLE, "Не удалось определить файл модуля."); //$NON-NLS-1$
            return null;
        }
        IProject project = file.getProject();
        EdtServices services = Activator.getDefault().getServices();
        IMarkerManager markerManager = services.getMarkerManager();
        ICheckRepository checkRepository = services.getCheckRepository();
        if (markerManager == null || checkRepository == null)
        {
            MessageDialog.openError(shell, TITLE, "Сервисы EDT недоступны."); //$NON-NLS-1$
            return null;
        }

        final List<MarkerScanner.MarkerEntry> entries =
            MarkerScanner.collect(markerManager, checkRepository, project, file, UnusedCollector.CHECK_IDS);
        if (entries.isEmpty())
        {
            MessageDialog.openInformation(shell, TITLE,
                "В текущем модуле нет неиспользуемых методов и переменных."); //$NON-NLS-1$
            return null;
        }

        List<UnusedItem> items = document.readOnly(resource -> UnusedCollector.collect(document, resource, entries));
        if (items.isEmpty())
        {
            MessageDialog.openInformation(shell, TITLE,
                "Не удалось определить элементы для удаления."); //$NON-NLS-1$
            return null;
        }

        UnusedSelectionDialog dialog = new UnusedSelectionDialog(shell, items);
        if (dialog.open() != Window.OK)
        {
            return null;
        }
        List<UnusedItem> selected = dialog.getSelected();
        if (selected.isEmpty())
        {
            MessageDialog.openInformation(shell, TITLE, "Ничего не выбрано."); //$NON-NLS-1$
            return null;
        }

        MultiTextEdit edit = new MultiTextEdit();
        OpResult result = new OpResult();
        int count = 0;
        for (UnusedItem item : selected)
        {
            try
            {
                edit.addChild(new DeleteEdit(item.delOffset, item.delLength));
                result.addSuccess(item.kind + " «" + item.name + "»", "удалено"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                count++;
            }
            catch (MalformedTreeException ex)
            {
                result.addFailure(item.kind + " «" + item.name + "»", //$NON-NLS-1$ //$NON-NLS-2$
                    "пересечение диапазонов — пропущено"); //$NON-NLS-1$
            }
        }
        if (count > 0)
        {
            try
            {
                HandlerSupport.applyCompound(xtextEditor, document, edit);
            }
            catch (Exception e)
            {
                Activator.logError("Failed to delete unused code", e); //$NON-NLS-1$
                MessageDialog.openError(shell, TITLE, "Не удалось удалить: " + e.getMessage()); //$NON-NLS-1$
                return null;
            }
        }
        new ResultDialog(shell, "Удаление неиспользуемого", "Удалено: " + count, //$NON-NLS-1$ //$NON-NLS-2$
            "Элемент", "Статус", result).open(); //$NON-NLS-1$ //$NON-NLS-2$
        return null;
    }
}
