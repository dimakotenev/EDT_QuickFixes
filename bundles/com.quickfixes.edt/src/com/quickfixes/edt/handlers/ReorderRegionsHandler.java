package com.quickfixes.edt.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;

import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com.quickfixes.edt.Activator;
import com.quickfixes.edt.core.RegionReorderer;
import com.quickfixes.edt.ui.ResultDialog;

/**
 * "Quick Fixes → Расставить области по порядку": reorders the top-level regions into canonical order
 * (addresses the {@code module-structure-top-region} check). Declines safely if there is text between
 * regions.
 */
public class ReorderRegionsHandler extends AbstractHandler
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
        IV8ProjectManager projectManager = Activator.getDefault().getServices().getV8ProjectManager();
        HandlerSupport.ModuleInfo info = HandlerSupport.moduleInfo(document, projectManager);
        if (info == null)
        {
            MessageDialog.openInformation(shell, TITLE, "Не удалось прочитать модуль."); //$NON-NLS-1$
            return null;
        }

        RegionReorderer.Plan plan = RegionReorderer.compute(document, info.moduleType, info.scriptVariant);
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
            Activator.logError("Failed to reorder regions", e); //$NON-NLS-1$
            MessageDialog.openError(shell, TITLE, "Не удалось переставить области: " + e.getMessage()); //$NON-NLS-1$
            return null;
        }
        new ResultDialog(shell, "Расстановка областей по порядку", //$NON-NLS-1$
            "Областей переставлено: " + plan.result.getSuccesses().size(), //$NON-NLS-1$
            "Область", "Новая позиция", plan.result).open(); //$NON-NLS-1$ //$NON-NLS-2$
        return null;
    }
}
