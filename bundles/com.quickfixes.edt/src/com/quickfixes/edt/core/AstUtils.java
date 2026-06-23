package com.quickfixes.edt.core;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;

import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.Module;

/** Small shared helpers for resolving marker EObjects and locating model elements. */
public final class AstUtils
{
    private AstUtils()
    {
    }

    /**
     * @param resource the module resource
     * @param fragment an EMF URI fragment (from a marker), may be {@code null}
     * @return the resolved object, or {@code null}
     */
    public static EObject resolve(XtextResource resource, String fragment)
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

    /**
     * @param object the starting object (may be {@code null})
     * @param type the wanted ancestor type
     * @param <T> the type
     * @return {@code object} or its nearest ancestor of {@code type}, or {@code null}
     */
    public static <T> T containerOfType(EObject object, Class<T> type)
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

    /** @return the {@link Module} root of the resource, or {@code null}. */
    public static Module moduleOf(XtextResource resource)
    {
        return !resource.getContents().isEmpty() && resource.getContents().get(0) instanceof Module
            ? (Module)resource.getContents().get(0) : null;
    }

    /** @return the method whose node range contains the 1-based {@code line}, or {@code null}. */
    public static Method methodAtLine(Module module, int line)
    {
        if (module == null || line < 1)
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
}
