package com.quickfixes.edt.core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;

import com._1c.g5.v8.dt.bsl.model.BslPackage;
import com._1c.g5.v8.dt.bsl.model.DynamicFeatureAccess;
import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.model.StaticFeatureAccess;
import com.quickfixes.edt.core.MarkerScanner.MarkerEntry;

/**
 * Fixes for outdated/redundant self-references.
 * <ul>
 * <li>{@code form-self-reference} ({@code ЭтаФорма}/{@code ThisForm}): if it is the source of a member
 * access ({@code ЭтаФорма.X}) remove the {@code ЭтаФорма.} prefix; if it stands alone replace it with
 * {@code ЭтотОбъект}/{@code ThisObject} — avoiding the {@code module-self-reference} a plain replace
 * would cause.</li>
 * <li>{@code module-self-reference} / {@code common-module-named-self-reference} /
 * {@code manager-module-named-self-reference}: the marked node is always a redundant access prefix —
 * delete it together with the following dot ({@code X.Member} &rarr; {@code Member}).</li>
 * </ul>
 */
public final class SelfReferenceFixer
{
    /** Check id of the form self-reference. */
    public static final String FORM_SELF_REFERENCE = "form-self-reference"; //$NON-NLS-1$
    /** Check ids handled by {@link #fixRedundant}. */
    public static final Set<String> REDUNDANT_CHECK_IDS = Set.of("module-self-reference", //$NON-NLS-1$
        "common-module-named-self-reference", "manager-module-named-self-reference"); //$NON-NLS-1$ //$NON-NLS-2$

    private static final Set<String> THIS_FORM_NAMES = Set.of("этаформа", "thisform"); //$NON-NLS-1$ //$NON-NLS-2$

    private SelfReferenceFixer()
    {
    }

    /** Computed edit plus outcome. */
    public static final class Plan
    {
        /** The combined edit (empty when {@link #count} is 0). */
        public final MultiTextEdit edit;
        /** The outcome. */
        public final OpResult result;
        /** Number of fixes. */
        public final int count;

        Plan(MultiTextEdit edit, OpResult result, int count)
        {
            this.edit = edit;
            this.result = result;
            this.count = count;
        }
    }

    /**
     * Fix {@code form-self-reference}.
     *
     * @param document the editor document
     * @param resource the parsed module resource
     * @param entries markers of {@link #FORM_SELF_REFERENCE}
     * @return the plan
     */
    public static Plan fixFormSelfReference(IDocument document, XtextResource resource, List<MarkerEntry> entries)
    {
        OpResult result = new OpResult();
        MultiTextEdit root = new MultiTextEdit();
        Set<Integer> seen = new HashSet<>();
        int count = 0;
        for (MarkerEntry entry : entries)
        {
            StaticFeatureAccess selfRef = resolveFormRef(resource, entry);
            if (selfRef == null)
            {
                continue;
            }
            INode node = NodeModelUtils.findActualNodeFor(selfRef);
            if (node == null || !seen.add(Integer.valueOf(node.getOffset())))
            {
                continue;
            }
            String name = selfRef.getName();
            try
            {
                if (isAccessSource(selfRef))
                {
                    int start = node.getOffset();
                    int end = prefixEnd(document, selfRef, node);
                    root.addChild(new DeleteEdit(start, end - start));
                    result.addSuccess(name, "убрано «" + name + ".»"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                else
                {
                    String replacement = "thisform".equals(name.toLowerCase(Locale.ROOT)) //$NON-NLS-1$
                        ? "ThisObject" : "ЭтотОбъект"; //$NON-NLS-1$ //$NON-NLS-2$
                    root.addChild(new ReplaceEdit(node.getOffset(), node.getLength(), replacement));
                    result.addSuccess(name, "→ " + replacement); //$NON-NLS-1$
                }
                count++;
            }
            catch (BadLocationException e)
            {
                result.addFailure(name, "не удалось вычислить диапазон"); //$NON-NLS-1$
            }
        }
        return new Plan(root, result, count);
    }

    /**
     * Fix the redundant self-reference checks ({@link #REDUNDANT_CHECK_IDS}).
     *
     * @param document the editor document
     * @param resource the parsed module resource
     * @param entries the markers
     * @return the plan
     */
    public static Plan fixRedundant(IDocument document, XtextResource resource, List<MarkerEntry> entries)
    {
        OpResult result = new OpResult();
        MultiTextEdit root = new MultiTextEdit();
        Set<Integer> seen = new HashSet<>();
        int count = 0;
        for (MarkerEntry entry : entries)
        {
            EObject prefix = AstUtils.resolve(resource, entry.uriFragment);
            INode node = prefix != null ? NodeModelUtils.findActualNodeFor(prefix) : null;
            if (node == null || !seen.add(Integer.valueOf(node.getOffset())))
            {
                continue;
            }
            String text = node.getText() != null ? node.getText().trim() : "префикс"; //$NON-NLS-1$
            try
            {
                int start = node.getOffset();
                int end = prefixEnd(document, prefix, node);
                root.addChild(new DeleteEdit(start, end - start));
                result.addSuccess(text, "убран избыточный префикс"); //$NON-NLS-1$
                count++;
            }
            catch (BadLocationException e)
            {
                result.addFailure(text, "не удалось вычислить диапазон"); //$NON-NLS-1$
            }
        }
        return new Plan(root, result, count);
    }

    /** @return {@code true} if {@code prefix} is the source of a {@code prefix.Member} access. */
    private static boolean isAccessSource(EObject prefix)
    {
        EObject parent = prefix.eContainer();
        return parent instanceof DynamicFeatureAccess && ((DynamicFeatureAccess)parent).getSource() == prefix;
    }

    /**
     * End offset of the prefix-with-dot to delete: the start of the member name in the parent access
     * (removes the prefix, any whitespace and the dot); falls back to a dot scan.
     */
    private static int prefixEnd(IDocument document, EObject prefix, INode node) throws BadLocationException
    {
        EObject parent = prefix.eContainer();
        if (parent instanceof DynamicFeatureAccess)
        {
            List<INode> nameNodes =
                NodeModelUtils.findNodesForFeature(parent, BslPackage.Literals.FEATURE_ACCESS__NAME);
            if (!nameNodes.isEmpty())
            {
                return nameNodes.get(0).getOffset();
            }
        }
        return dotEnd(document, node);
    }

    private static int dotEnd(IDocument document, INode node) throws BadLocationException
    {
        int end = node.getOffset() + node.getLength();
        int length = document.getLength();
        int i = end;
        while (i < length && Character.isWhitespace(document.getChar(i)))
        {
            i++;
        }
        return i < length && document.getChar(i) == '.' ? i + 1 : end;
    }

    private static StaticFeatureAccess resolveFormRef(XtextResource resource, MarkerEntry entry)
    {
        EObject obj = AstUtils.resolve(resource, entry.uriFragment);
        if (obj instanceof StaticFeatureAccess && isThisForm((StaticFeatureAccess)obj))
        {
            return (StaticFeatureAccess)obj;
        }
        return findFormRefAtLine(AstUtils.moduleOf(resource), entry.line);
    }

    private static boolean isThisForm(StaticFeatureAccess selfRef)
    {
        String name = selfRef.getName();
        return name != null && THIS_FORM_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    private static StaticFeatureAccess findFormRefAtLine(Module module, int line)
    {
        if (module == null || line < 1)
        {
            return null;
        }
        for (Method method : module.allMethods())
        {
            for (Iterator<EObject> it = method.eAllContents(); it.hasNext();)
            {
                EObject obj = it.next();
                if (obj instanceof StaticFeatureAccess && isThisForm((StaticFeatureAccess)obj))
                {
                    INode node = NodeModelUtils.findActualNodeFor(obj);
                    if (node != null && line >= node.getStartLine() && line <= node.getEndLine())
                    {
                        return (StaticFeatureAccess)obj;
                    }
                }
            }
        }
        return null;
    }
}
