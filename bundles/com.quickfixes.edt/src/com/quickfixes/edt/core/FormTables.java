package com.quickfixes.edt.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.resource.BslEventsService;
import com._1c.g5.v8.dt.form.model.Form;
import com._1c.g5.v8.dt.form.model.Table;
import com.quickfixes.edt.Activator;

/**
 * Enumerates the names of all tables on a form module's form. Used to generate one
 * {@code ОбработчикиСобытийЭлементовТаблицыФормы&lt;TableName&gt;} region per table.
 *
 * <p>The form-model root ({@link Form}) is reached via {@link Module#getOwner()} when it is a form,
 * otherwise by walking up from any event-handler binding ({@link BslEventsService}). Tables are then
 * collected from the whole form tree.</p>
 */
public final class FormTables
{
    private FormTables()
    {
    }

    /**
     * @param module the form module
     * @param eventsService the BSL events service (used as a fallback to reach the form root)
     * @return table names in form order (deduplicated); empty if the form root cannot be resolved
     */
    public static List<String> collect(Module module, BslEventsService eventsService)
    {
        List<String> names = new ArrayList<>();
        Form form = findForm(module, eventsService);
        if (form == null)
        {
            return names;
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (Iterator<EObject> it = form.eAllContents(); it.hasNext();)
        {
            EObject obj = it.next();
            if (obj instanceof Table)
            {
                String name = ((Table)obj).getName();
                if (name != null && !name.isEmpty())
                {
                    ordered.add(name);
                }
            }
        }
        names.addAll(ordered);
        return names;
    }

    private static Form findForm(Module module, BslEventsService eventsService)
    {
        if (module == null)
        {
            return null;
        }
        if (module.getOwner() instanceof Form)
        {
            return (Form)module.getOwner();
        }
        if (eventsService != null)
        {
            try
            {
                for (List<EObject> bound : eventsService.getEventHandlersContainer(module).values())
                {
                    for (EObject obj : bound)
                    {
                        Form form = containerForm(obj);
                        if (form != null)
                        {
                            return form;
                        }
                    }
                }
            }
            catch (Exception e)
            {
                Activator.logError("Failed to resolve form for table enumeration", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    private static Form containerForm(EObject object)
    {
        for (EObject e = object; e != null; e = e.eContainer())
        {
            if (e instanceof Form)
            {
                return (Form)e;
            }
        }
        return null;
    }
}
