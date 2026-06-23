package com.quickfixes.edt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;

import com._1c.g5.v8.dt.bsl.model.BslPackage;
import com._1c.g5.v8.dt.bsl.model.FeatureAccess;
import com._1c.g5.v8.dt.bsl.model.FeatureEntry;
import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.model.StaticFeatureAccess;
import com._1c.g5.v8.dt.bsl.model.Variable;
import com._1c.g5.v8.dt.mcore.McorePackage;
import com.quickfixes.edt.core.MarkerScanner.MarkerEntry;

/**
 * Fixes the "variable name must start with a capital letter" case of {@code bsl-variable-name-invalid}:
 * capitalizes the first letter of each flagged variable and renames every reference within the
 * variable's scope (a module-level variable across the whole module; a local variable or parameter
 * within its method). The sub-case is detected without parsing the marker message — a variable whose
 * first character is a lowercase letter.
 */
public final class VariableNameFixer
{
    /** Symbolic id of the check this feature acts on. */
    public static final String CHECK_ID = "bsl-variable-name-invalid"; //$NON-NLS-1$

    private VariableNameFixer()
    {
    }

    /** The computed edit plus the outcome. */
    public static final class Plan
    {
        /** Combined rename edit (empty when {@link #fixCount} is 0). */
        public final MultiTextEdit edit;
        /** Outcome (old name → new name + occurrences). */
        public final OpResult result;
        /** Number of variables renamed. */
        public final int fixCount;

        Plan(MultiTextEdit edit, OpResult result, int fixCount)
        {
            this.edit = edit;
            this.result = result;
            this.fixCount = fixCount;
        }
    }

    /**
     * @param resource the parsed module resource
     * @param entries markers of {@link #CHECK_ID} in this module
     * @return the rename plan
     */
    public static Plan compute(XtextResource resource, List<MarkerEntry> entries)
    {
        OpResult result = new OpResult();
        MultiTextEdit root = new MultiTextEdit();
        Set<Variable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int fixCount = 0;

        for (MarkerEntry entry : entries)
        {
            Variable variable = toVariable(resolve(resource, entry));
            if (variable == null || !seen.add(variable))
            {
                continue;
            }
            String oldName = variable.getName();
            if (!startsWithLowerLetter(oldName))
            {
                continue;
            }
            String newName = Character.toUpperCase(oldName.charAt(0)) + oldName.substring(1);

            List<int[]> ranges = new ArrayList<>();
            int[] declRange = nameRange(variable, McorePackage.Literals.NAMED_ELEMENT__NAME);
            if (declRange != null)
            {
                ranges.add(declRange);
            }
            EObject scope = scopeOf(variable);
            if (scope != null)
            {
                for (Iterator<EObject> it = scope.eAllContents(); it.hasNext();)
                {
                    EObject obj = it.next();
                    if (obj instanceof StaticFeatureAccess && references((StaticFeatureAccess)obj, variable))
                    {
                        int[] range = nameRange(obj, BslPackage.Literals.FEATURE_ACCESS__NAME);
                        if (range != null)
                        {
                            ranges.add(range);
                        }
                    }
                }
            }
            if (ranges.isEmpty())
            {
                result.addFailure(oldName, "не найден текст имени"); //$NON-NLS-1$
                continue;
            }
            for (int[] range : ranges)
            {
                root.addChild(new ReplaceEdit(range[0], range[1], newName));
            }
            result.addSuccess(oldName, newName + " (вхождений: " + ranges.size() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
            fixCount++;
        }
        return new Plan(root, result, fixCount);
    }

    private static EObject resolve(XtextResource resource, MarkerEntry entry)
    {
        if (entry.uriFragment == null || entry.uriFragment.isEmpty())
        {
            return null;
        }
        try
        {
            return resource.getEObject(entry.uriFragment);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Variable toVariable(EObject obj)
    {
        if (obj instanceof Variable)
        {
            return (Variable)obj;
        }
        if (obj instanceof StaticFeatureAccess)
        {
            for (FeatureEntry entry : ((StaticFeatureAccess)obj).getFeatureEntries())
            {
                if (entry.getFeature() instanceof Variable)
                {
                    return (Variable)entry.getFeature();
                }
            }
        }
        return null;
    }

    private static boolean references(StaticFeatureAccess access, Variable variable)
    {
        for (FeatureEntry entry : access.getFeatureEntries())
        {
            if (entry.getFeature() == variable)
            {
                return true;
            }
        }
        return false;
    }

    private static EObject scopeOf(Variable variable)
    {
        EObject method = containerOfType(variable, Method.class);
        if (method != null)
        {
            return method;
        }
        return containerOfType(variable, Module.class);
    }

    private static EObject containerOfType(EObject object, Class<?> type)
    {
        for (EObject e = object == null ? null : object.eContainer(); e != null; e = e.eContainer())
        {
            if (type.isInstance(e))
            {
                return e;
            }
        }
        return null;
    }

    /** @return {@code {offset, length}} of the object's name node, or {@code null}. */
    private static int[] nameRange(EObject object, org.eclipse.emf.ecore.EStructuralFeature feature)
    {
        List<INode> nodes = NodeModelUtils.findNodesForFeature(object, feature);
        INode node = null;
        if (!nodes.isEmpty())
        {
            node = nodes.get(0);
        }
        else if (object instanceof FeatureAccess)
        {
            node = NodeModelUtils.findActualNodeFor(object);
        }
        if (node == null)
        {
            return null;
        }
        return new int[] { node.getOffset(), node.getLength() };
    }

    private static boolean startsWithLowerLetter(String name)
    {
        if (name == null || name.isEmpty())
        {
            return false;
        }
        char c = name.charAt(0);
        return Character.isLetter(c) && Character.isLowerCase(c);
    }
}
