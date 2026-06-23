package com.quickfixes.edt.handlers;

import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;

import com._1c.g5.v8.dt.validation.marker.IMarkerManager;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;
import com.quickfixes.edt.Activator;
import com.quickfixes.edt.EdtServices;
import com.quickfixes.edt.core.MarkerScanner;
import com.quickfixes.edt.core.SelfReferenceFixer;
import com.quickfixes.edt.ui.ResultDialog;

/**
 * "Quick Fixes → Исправление устаревших конструкций → Исправление ошибки ЭтаФорма" — fixes
 * {@code form-self-reference} (smart: drop the {@code ЭтаФорма.} prefix or replace a standalone
 * {@code ЭтаФорма} with {@code ЭтотОбъект}).
 */
public class FixFormSelfReferenceHandler extends AbstractHandler
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

        final List<MarkerScanner.MarkerEntry> entries = MarkerScanner.collect(markerManager, checkRepository,
            project, file, Set.of(SelfReferenceFixer.FORM_SELF_REFERENCE));
        if (entries.isEmpty())
        {
            MessageDialog.openInformation(shell, TITLE,
                "В текущем модуле нет ошибок проверки «form-self-reference»."); //$NON-NLS-1$
            return null;
        }

        SelfReferenceFixer.Plan plan =
            document.readOnly(resource -> SelfReferenceFixer.fixFormSelfReference(document, resource, entries));
        if (plan.count == 0)
        {
            MessageDialog.openInformation(shell, TITLE, "Не удалось применить исправления."); //$NON-NLS-1$
            return null;
        }
        try
        {
            HandlerSupport.applyCompound(xtextEditor, document, plan.edit);
        }
        catch (Exception e)
        {
            Activator.logError("Failed to fix form self-reference", e); //$NON-NLS-1$
            MessageDialog.openError(shell, TITLE, "Не удалось применить: " + e.getMessage()); //$NON-NLS-1$
            return null;
        }
        new ResultDialog(shell, "Исправление ошибки «ЭтаФорма»", "Исправлено: " + plan.count, //$NON-NLS-1$ //$NON-NLS-2$
            "Конструкция", "Действие", plan.result).open(); //$NON-NLS-1$ //$NON-NLS-2$
        return null;
    }
}
