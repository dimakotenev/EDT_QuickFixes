package com.quickfixes.edt.handlers;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;

import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.model.ModuleType;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.metadata.mdclass.ScriptVariant;
import com.quickfixes.edt.Activator;

/**
 * Shared helpers for the Quick Fix command handlers: resolving the active BSL editor and its
 * {@code .bsl} file, reading basic module info, and applying an edit as a single undo step.
 */
public final class HandlerSupport
{
    private HandlerSupport()
    {
    }

    /** Module type + script variant of the active module. */
    public static final class ModuleInfo
    {
        /** The module type. */
        public final ModuleType moduleType;
        /** The project's script variant (RU/EN). */
        public final ScriptVariant scriptVariant;

        ModuleInfo(ModuleType moduleType, ScriptVariant scriptVariant)
        {
            this.moduleType = moduleType;
            this.scriptVariant = scriptVariant;
        }
    }

    /**
     * @param editor the active editor (may be {@code null})
     * @return the embedded/own {@link XtextEditor}, or {@code null} if the editor is not a BSL editor
     */
    public static XtextEditor xtextEditor(IEditorPart editor)
    {
        return editor == null ? null : editor.getAdapter(XtextEditor.class);
    }

    /**
     * Resolves the {@code .bsl} file backing the Xtext editor (via the editor input, then via the
     * parsed resource's platform URI — robust for embedded form/object module editors).
     *
     * @param xtextEditor the BSL editor
     * @return the file, or {@code null}
     */
    public static IFile moduleFile(XtextEditor xtextEditor)
    {
        IFile file = ResourceUtil.getFile(xtextEditor.getEditorInput());
        if (file != null)
        {
            return file;
        }
        try
        {
            URI uri = xtextEditor.getDocument().readOnly(resource -> resource.getURI());
            if (uri != null && uri.isPlatformResource())
            {
                return ResourcesPlugin.getWorkspace().getRoot()
                    .getFile(IPath.fromOSString(uri.toPlatformString(true)));
            }
        }
        catch (Exception e)
        {
            Activator.logError("Failed to resolve module file from resource URI", e); //$NON-NLS-1$
        }
        return null;
    }

    /**
     * Reads the module type and script variant from the document.
     *
     * @param document the editor document
     * @param projectManager the project manager (for the script variant; may be {@code null})
     * @return module info, or {@code null} if the resource is not a BSL module
     */
    public static ModuleInfo moduleInfo(IXtextDocument document, IV8ProjectManager projectManager)
    {
        return document.readOnly(resource -> {
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
            return new ModuleInfo(module.getModuleType(), scriptVariant);
        });
    }

    /**
     * Applies an edit to the document as a single undoable compound change.
     *
     * @param xtextEditor the editor (for the rewrite target)
     * @param document the document
     * @param edit the edit to apply
     * @throws Exception if the edit cannot be applied
     */
    public static void applyCompound(XtextEditor xtextEditor, IDocument document, TextEdit edit) throws Exception
    {
        IRewriteTarget rewriteTarget = xtextEditor.getAdapter(IRewriteTarget.class);
        if (rewriteTarget != null)
        {
            rewriteTarget.beginCompoundChange();
        }
        try
        {
            edit.apply(document);
        }
        finally
        {
            if (rewriteTarget != null)
            {
                rewriteTarget.endCompoundChange();
            }
        }
    }
}
