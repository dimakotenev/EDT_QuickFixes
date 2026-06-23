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

import com._1c.g5.v8.dt.metadata.mdclass.ScriptVariant;
import com._1c.g5.v8.dt.validation.marker.IMarkerManager;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;
import com.quickfixes.edt.Activator;
import com.quickfixes.edt.EdtServices;
import com.quickfixes.edt.core.AccessibilityFixer;
import com.quickfixes.edt.core.MarkerScanner;

/**
 * "Quick Fixes → Исправление устаревших конструкций → Исправление доступности с толстого клиента" —
 * wraps the module in the server-only preprocessor guard for {@code module-accessibility-at-client}.
 */
public class FixAccessibilityHandler extends AbstractHandler
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
        IXtextDocument document = xtextEditor.getDocument();
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

        List<MarkerScanner.MarkerEntry> entries =
            MarkerScanner.collect(markerManager, checkRepository, project, file, Set.of(AccessibilityFixer.CHECK_ID));
        if (entries.isEmpty())
        {
            MessageDialog.openInformation(shell, TITLE,
                "В текущем модуле нет ошибок проверки «module-accessibility-at-client»."); //$NON-NLS-1$
            return null;
        }

        HandlerSupport.ModuleInfo info = HandlerSupport.moduleInfo(document, services.getV8ProjectManager());
        ScriptVariant scriptVariant = info != null ? info.scriptVariant : ScriptVariant.ENGLISH;

        AccessibilityFixer.Plan plan = AccessibilityFixer.wrap(document, scriptVariant);
        if (plan.edit == null)
        {
            MessageDialog.openInformation(shell, TITLE, plan.info);
            return null;
        }
        try
        {
            HandlerSupport.applyCompound(xtextEditor, document, plan.edit);
        }
        catch (Exception e)
        {
            Activator.logError("Failed to wrap module for accessibility", e); //$NON-NLS-1$
            MessageDialog.openError(shell, TITLE, "Не удалось применить: " + e.getMessage()); //$NON-NLS-1$
            return null;
        }
        MessageDialog.openInformation(shell, TITLE,
            "Модуль обёрнут в «#Если Сервер Или … Тогда … #КонецЕсли»."); //$NON-NLS-1$
        return null;
    }
}
