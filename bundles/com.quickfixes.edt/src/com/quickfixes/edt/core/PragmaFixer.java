package com.quickfixes.edt.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;

import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.metadata.mdclass.ScriptVariant;
import com.quickfixes.edt.core.MarkerScanner.MarkerEntry;

/**
 * Fixes {@code form-module-missing-pragma}: inserts {@code &НаСервере}/{@code &AtServer} on its own line
 * directly above each flagged method (below its doc-comment), keeping the method's indentation.
 */
public final class PragmaFixer
{
    /** Check id this fixer acts on. */
    public static final String CHECK_ID = "form-module-missing-pragma"; //$NON-NLS-1$

    private PragmaFixer()
    {
    }

    /** Computed edit plus outcome. */
    public static final class Plan
    {
        /** The combined insert edit (empty when {@link #count} is 0). */
        public final MultiTextEdit edit;
        /** The outcome. */
        public final OpResult result;
        /** Number of directives inserted. */
        public final int count;

        Plan(MultiTextEdit edit, OpResult result, int count)
        {
            this.edit = edit;
            this.result = result;
            this.count = count;
        }
    }

    /**
     * @param document the editor document
     * @param resource the parsed module resource
     * @param entries markers of {@link #CHECK_ID}
     * @param scriptVariant the project's script variant (RU/EN)
     * @return the plan
     */
    public static Plan addServerPragma(IDocument document, XtextResource resource, List<MarkerEntry> entries,
        ScriptVariant scriptVariant)
    {
        OpResult result = new OpResult();
        MultiTextEdit root = new MultiTextEdit();
        Module module = AstUtils.moduleOf(resource);
        String directive = scriptVariant == ScriptVariant.RUSSIAN ? "&НаСервере" : "&AtServer"; //$NON-NLS-1$ //$NON-NLS-2$
        List<String> lines = BslModuleText.readLines(document);
        Set<Integer> seenLines = new HashSet<>();
        int count = 0;

        for (MarkerEntry entry : entries)
        {
            Method method = AstUtils.containerOfType(AstUtils.resolve(resource, entry.uriFragment), Method.class);
            if (method == null)
            {
                method = AstUtils.methodAtLine(module, entry.line);
            }
            if (method == null)
            {
                continue;
            }
            INode node = NodeModelUtils.findActualNodeFor(method);
            if (node == null || !seenLines.add(Integer.valueOf(node.getStartLine())))
            {
                continue;
            }
            int startLine = node.getStartLine();
            String indent = leadingWhitespace(startLine - 1 < lines.size() ? lines.get(startLine - 1) : ""); //$NON-NLS-1$
            try
            {
                int offset = document.getLineOffset(startLine - 1);
                root.addChild(new InsertEdit(offset, indent + directive + "\n")); //$NON-NLS-1$
                result.addSuccess(method.getName(), directive);
                count++;
            }
            catch (BadLocationException e)
            {
                result.addFailure(method.getName(), "не удалось вставить директиву"); //$NON-NLS-1$
            }
        }
        return new Plan(root, result, count);
    }

    private static String leadingWhitespace(String line)
    {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i)))
        {
            i++;
        }
        return line.substring(0, i);
    }
}
