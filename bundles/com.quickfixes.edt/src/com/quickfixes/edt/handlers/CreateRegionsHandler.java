package com.quickfixes.edt.handlers;

import java.util.HashSet;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;

import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.model.ModuleType;
import com._1c.g5.v8.dt.bsl.resource.BslEventsService;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.metadata.mdclass.ScriptVariant;
import com.quickfixes.edt.Activator;
import com.quickfixes.edt.EdtServices;
import com.quickfixes.edt.core.FormTables;
import com.quickfixes.edt.core.RegionCreator;
import com.quickfixes.edt.core.RegionCreator.Candidate;
import com.quickfixes.edt.ui.CheckboxListDialog;
import com.quickfixes.edt.ui.ResultDialog;

/**
 * "Quick Fixes → Создать стандартные области": offers the missing standard regions in a checkbox list
 * (all checked by default), then creates only the chosen ones. For a form module it also offers a
 * per-table form-table-handlers region. Existing regions and methods are not touched.
 */
public class CreateRegionsHandler extends AbstractHandler
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
        EdtServices services = Activator.getDefault().getServices();
        final IV8ProjectManager projectManager = services.getV8ProjectManager();
        final BslEventsService eventsService = services.getBslEventsService();

        List<Candidate> candidates = document.readOnly(resource -> {
            if (resource.getContents().isEmpty() || !(resource.getContents().get(0) instanceof Module))
            {
                return null;
            }
            Module module = (Module)resource.getContents().get(0);
            ScriptVariant scriptVariant = ScriptVariant.ENGLISH;
            if (projectManager != null)
            {
                IV8Project project = projectManager.getProject(module);
                if (project != null && project.getScriptVariant() != null)
                {
                    scriptVariant = project.getScriptVariant();
                }
            }
            List<String> tables = module.getModuleType() == ModuleType.FORM_MODULE
                ? FormTables.collect(module, eventsService) : List.of();
            return RegionCreator.candidates(document, module.getModuleType(), scriptVariant, tables);
        });

        if (candidates == null)
        {
            MessageDialog.openInformation(shell, TITLE, "Не удалось прочитать модуль."); //$NON-NLS-1$
            return null;
        }
        if (candidates.isEmpty())
        {
            MessageDialog.openInformation(shell, TITLE, "Все стандартные области уже на месте."); //$NON-NLS-1$
            return null;
        }

        CheckboxListDialog<Candidate> dialog = new CheckboxListDialog<>(shell,
            "Создание стандартных областей", //$NON-NLS-1$
            "Отметьте области для создания (по умолчанию отмечены все).", //$NON-NLS-1$
            "Область", candidates, candidate -> candidate.name, "Создать"); //$NON-NLS-1$ //$NON-NLS-2$
        if (dialog.open() != Window.OK)
        {
            return null;
        }
        List<Candidate> selected = dialog.getSelected();
        if (selected.isEmpty())
        {
            MessageDialog.openInformation(shell, TITLE, "Ничего не выбрано."); //$NON-NLS-1$
            return null;
        }

        RegionCreator.Plan plan = RegionCreator.buildPlan(document, candidates, new HashSet<>(selected));
        if (plan.createCount == 0)
        {
            return null;
        }
        try
        {
            HandlerSupport.applyCompound(xtextEditor, document, plan.edit);
        }
        catch (Exception e)
        {
            Activator.logError("Failed to create regions", e); //$NON-NLS-1$
            MessageDialog.openError(shell, TITLE, "Не удалось создать области: " + e.getMessage()); //$NON-NLS-1$
            return null;
        }
        new ResultDialog(shell, "Создание стандартных областей", //$NON-NLS-1$
            "Создано областей: " + plan.createCount, "Область", "Статус", plan.result).open(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return null;
    }
}
