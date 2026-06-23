package com.quickfixes.edt.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;

import com._1c.g5.v8.dt.bsl.model.DeclareStatement;
import com._1c.g5.v8.dt.bsl.model.ExplicitVariable;
import com._1c.g5.v8.dt.bsl.model.ImplicitVariable;
import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.model.Statement;
import com.quickfixes.edt.core.MarkerScanner.MarkerEntry;

/**
 * Collects unused methods ({@code module-unused-method}) and unused local variables
 * ({@code module-unused-local-variable}) of the active module, each with its name, line and the
 * character range to delete. Resolution is driven by the marker's check id: the problem EObject is
 * resolved from the marker URI fragment (walking up to a {@link Method}/{@link ExplicitVariable}), and
 * if that fails it is found by line in the module model — so a fragment that does not resolve (common
 * for nested variables) still yields the element.
 */
public final class UnusedCollector
{
    /** Check id for unused methods. */
    public static final String CHECK_METHOD = "module-unused-method"; //$NON-NLS-1$
    /** Check id for unused local variables. */
    public static final String CHECK_LOCAL_VAR = "module-unused-local-variable"; //$NON-NLS-1$
    /** Both check ids. */
    public static final Set<String> CHECK_IDS = Set.of(CHECK_METHOD, CHECK_LOCAL_VAR);

    private UnusedCollector()
    {
    }

    /** A reported unused method or variable with its delete range. */
    public static final class UnusedItem
    {
        /** Localized kind label ("метод" / "переменная"). */
        public final String kind;
        /** Subject name. */
        public final String name;
        /** 1-based source line. */
        public final int line;
        /** Start offset of the text to delete. */
        public final int delOffset;
        /** Length of the text to delete. */
        public final int delLength;

        UnusedItem(String kind, String name, int line, int delOffset, int delLength)
        {
            this.kind = kind;
            this.name = name;
            this.line = line;
            this.delOffset = delOffset;
            this.delLength = delLength;
        }
    }

    /**
     * @param document the editor document
     * @param resource the parsed module resource
     * @param entries unused-* markers in this module
     * @return collected items (those whose range could be computed)
     */
    public static List<UnusedItem> collect(IDocument document, XtextResource resource, List<MarkerEntry> entries)
    {
        List<UnusedItem> items = new ArrayList<>();
        if (resource.getContents().isEmpty() || !(resource.getContents().get(0) instanceof Module))
        {
            return items;
        }
        Module module = (Module)resource.getContents().get(0);
        List<String> lines = BslModuleText.readLines(document);
        Set<Integer> seenOffsets = new HashSet<>();

        for (MarkerEntry entry : entries)
        {
            try
            {
                EObject obj = resolve(resource, entry.uriFragment);
                if (CHECK_METHOD.equals(entry.checkId))
                {
                    // Prefer the fragment object only if it carries a node; otherwise resolve by line
                    // against the editor model (which always has node information).
                    Method method = withNode(containerOfType(obj, Method.class));
                    if (method == null)
                    {
                        method = methodAtLine(module, entry.line);
                    }
                    if (method != null)
                    {
                        addMethod(document, lines, method, items, seenOffsets);
                    }
                }
                else if (CHECK_LOCAL_VAR.equals(entry.checkId))
                {
                    // A `Перем` declaration is an ExplicitVariable; an assignment without `Перем`
                    // (Ку = …) creates an ImplicitVariable whose unused warning is fixed by deleting
                    // the assignment statement.
                    ExplicitVariable explicit = variableAtLine(module, entry.line);
                    if (explicit == null)
                    {
                        explicit = withNode(containerOfType(obj, ExplicitVariable.class));
                    }
                    if (explicit != null)
                    {
                        addVariable(document, explicit, entry.line, items, seenOffsets);
                    }
                    else
                    {
                        ImplicitVariable implicit = containerOfType(obj, ImplicitVariable.class);
                        if (implicit != null)
                        {
                            addImplicitVariable(document, implicit, items, seenOffsets);
                        }
                    }
                }
            }
            catch (BadLocationException e)
            {
                // skip an item whose range cannot be computed
            }
        }
        return items;
    }

    private static void addMethod(IDocument document, List<String> lines, Method method, List<UnusedItem> items,
        Set<Integer> seenOffsets) throws BadLocationException
    {
        INode node = NodeModelUtils.findActualNodeFor(method);
        if (node == null)
        {
            return;
        }
        int[] range = BslModuleText.methodRange(document, lines, node.getStartLine(), node.getEndLine());
        if (seenOffsets.add(Integer.valueOf(range[0])))
        {
            items.add(new UnusedItem("метод", method.getName(), node.getStartLine(), range[0], //$NON-NLS-1$
                range[1] - range[0]));
        }
    }

    private static void addVariable(IDocument document, ExplicitVariable variable, int markerLine,
        List<UnusedItem> items, Set<Integer> seenOffsets) throws BadLocationException
    {
        int[] range = variableRange(document, variable);
        if (range == null)
        {
            return;
        }
        INode node = NodeModelUtils.findActualNodeFor(variable);
        int line = node != null ? node.getStartLine() : markerLine;
        if (seenOffsets.add(Integer.valueOf(range[0])))
        {
            items.add(new UnusedItem("переменная", variable.getName(), line, range[0], range[1] - range[0])); //$NON-NLS-1$
        }
    }

    private static void addImplicitVariable(IDocument document, ImplicitVariable variable, List<UnusedItem> items,
        Set<Integer> seenOffsets) throws BadLocationException
    {
        // The implicit variable has no node of its own; delete its enclosing assignment statement.
        Statement statement = containerOfType(variable, Statement.class);
        if (statement == null)
        {
            return;
        }
        INode node = NodeModelUtils.findActualNodeFor(statement);
        if (node == null)
        {
            return;
        }
        int[] range = BslModuleText.lineRange(document, node.getStartLine(), node.getEndLine());
        String name = variable.getName() != null ? variable.getName() : "переменная"; //$NON-NLS-1$
        if (seenOffsets.add(Integer.valueOf(range[0])))
        {
            items.add(new UnusedItem("переменная", name, node.getStartLine(), range[0], range[1] - range[0])); //$NON-NLS-1$
        }
    }

    private static Method methodAtLine(Module module, int line)
    {
        if (line < 1)
        {
            return null;
        }
        for (Method method : module.allMethods())
        {
            INode node = NodeModelUtils.findActualNodeFor(method);
            if (node != null && line >= node.getStartLine() && line <= node.getEndLine())
            {
                return method;
            }
        }
        return null;
    }

    private static ExplicitVariable variableAtLine(Module module, int line)
    {
        if (line < 1)
        {
            return null;
        }
        // allDeclareStatements() returns every declaration, including those nested in #Region blocks
        // (eAllContents may miss region-nested content — same reason methods use allMethods()).
        ExplicitVariable byDeclareStatement = null;
        for (DeclareStatement declaration : module.allDeclareStatements())
        {
            INode declNode = NodeModelUtils.findActualNodeFor(declaration);
            boolean declOnLine =
                declNode != null && line >= declNode.getStartLine() && line <= declNode.getEndLine();
            for (ExplicitVariable variable : declaration.getVariables())
            {
                INode node = NodeModelUtils.findActualNodeFor(variable);
                if (node != null && line >= node.getStartLine() && line <= node.getEndLine())
                {
                    return variable;
                }
                if (byDeclareStatement == null && declOnLine)
                {
                    byDeclareStatement = variable;
                }
            }
        }
        return byDeclareStatement;
    }

    /** @return {@code object} if it has a node-model node, otherwise {@code null}. */
    private static <T extends EObject> T withNode(T object)
    {
        return object != null && NodeModelUtils.findActualNodeFor(object) != null ? object : null;
    }

    private static <T> T containerOfType(EObject object, Class<T> type)
    {
        for (EObject e = object; e != null; e = e.eContainer())
        {
            if (type.isInstance(e))
            {
                return type.cast(e);
            }
        }
        return null;
    }

    private static int[] variableRange(IDocument document, ExplicitVariable variable) throws BadLocationException
    {
        EObject container = variable.eContainer();
        INode varNode = NodeModelUtils.findActualNodeFor(variable);
        if (!(container instanceof DeclareStatement) || varNode == null)
        {
            return varNode == null ? null : new int[] { varNode.getOffset(), varNode.getOffset() + varNode.getLength() };
        }
        DeclareStatement declaration = (DeclareStatement)container;
        List<ExplicitVariable> vars = declaration.getVariables();
        if (vars.size() <= 1)
        {
            // Only variable in the statement: delete the whole declaration line(s).
            INode declNode = NodeModelUtils.findActualNodeFor(declaration);
            int startLine = declNode.getStartLine();
            int endLine = declNode.getEndLine();
            int delStart = document.getLineOffset(startLine - 1);
            int delEnd = endLine < document.getNumberOfLines() ? document.getLineOffset(endLine)
                : document.getLength();
            return new int[] { delStart, delEnd };
        }
        // Multi-declaration: delete just this variable plus one adjacent comma.
        int index = vars.indexOf(variable);
        if (index >= 0 && index < vars.size() - 1)
        {
            INode next = NodeModelUtils.findActualNodeFor(vars.get(index + 1));
            if (next != null)
            {
                return new int[] { varNode.getOffset(), next.getOffset() };
            }
        }
        else if (index > 0)
        {
            INode prev = NodeModelUtils.findActualNodeFor(vars.get(index - 1));
            if (prev != null)
            {
                return new int[] { prev.getOffset() + prev.getLength(), varNode.getOffset() + varNode.getLength() };
            }
        }
        return new int[] { varNode.getOffset(), varNode.getOffset() + varNode.getLength() };
    }

    private static EObject resolve(XtextResource resource, String fragment)
    {
        if (fragment == null || fragment.isEmpty())
        {
            return null;
        }
        try
        {
            return resource.getEObject(fragment);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
