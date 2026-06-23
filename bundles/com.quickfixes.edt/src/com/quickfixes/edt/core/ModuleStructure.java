package com.quickfixes.edt.core;

import static com.quickfixes.edt.core.ModuleStructureSection.EVENT_HANDLERS;
import static com.quickfixes.edt.core.ModuleStructureSection.FORM_COMMAND_EVENT_HANDLERS;
import static com.quickfixes.edt.core.ModuleStructureSection.FORM_EVENT_HANDLERS;
import static com.quickfixes.edt.core.ModuleStructureSection.FORM_HEADER_ITEMS_EVENT_HANDLERS;
import static com.quickfixes.edt.core.ModuleStructureSection.FORM_TABLE_ITEMS_EVENT_HANDLERS;
import static com.quickfixes.edt.core.ModuleStructureSection.INITIALIZE;
import static com.quickfixes.edt.core.ModuleStructureSection.INTERNAL;
import static com.quickfixes.edt.core.ModuleStructureSection.PRIVATE;
import static com.quickfixes.edt.core.ModuleStructureSection.PUBLIC;
import static com.quickfixes.edt.core.ModuleStructureSection.VARIABLES;

import java.util.List;
import java.util.Locale;

import com._1c.g5.v8.dt.bsl.model.ModuleType;
import com._1c.g5.v8.dt.metadata.mdclass.ScriptVariant;

/**
 * Canonical ordered set of standard regions for each module type, ported from the open
 * 1C:Code Style project ({@code com.e1c.v8codestyle.bsl.ModuleStructure}). Order matters — it is the
 * order regions must appear in (used to create missing regions and to reorder existing ones).
 */
public final class ModuleStructure
{
    private ModuleStructure()
    {
    }

    /**
     * @param moduleType the module type
     * @return the standard sections for that module type, in canonical order (empty if unknown)
     */
    public static List<ModuleStructureSection> sectionsFor(ModuleType moduleType)
    {
        if (moduleType == null)
        {
            return List.of();
        }
        switch (moduleType)
        {
        case COMMON_MODULE:
            return List.of(PUBLIC, INTERNAL, PRIVATE);
        case MANAGER_MODULE:
            return List.of(PUBLIC, EVENT_HANDLERS, INTERNAL, PRIVATE);
        case OBJECT_MODULE:
        case RECORDSET_MODULE:
        case VALUE_MANAGER_MODULE:
            return List.of(VARIABLES, PUBLIC, EVENT_HANDLERS, INTERNAL, PRIVATE, INITIALIZE);
        case FORM_MODULE:
            return List.of(VARIABLES, FORM_EVENT_HANDLERS, FORM_HEADER_ITEMS_EVENT_HANDLERS,
                FORM_TABLE_ITEMS_EVENT_HANDLERS, FORM_COMMAND_EVENT_HANDLERS, PRIVATE, INITIALIZE);
        case MANAGED_APP_MODULE:
        case ORDINARY_APP_MODULE:
            return List.of(VARIABLES, EVENT_HANDLERS, PRIVATE, INITIALIZE);
        case COMMAND_MODULE:
        case BOT_MODULE:
        case EXTERNAL_CONN_MODULE:
        case HTTP_SERVICE_MODULE:
        case INTEGRATION_SERVICE_MODULE:
        case SESSION_MODULE:
        case WEB_SERVICE_MODULE:
        case WEB_SOCKET_CLIENT_MODULE:
            return List.of(EVENT_HANDLERS, PRIVATE);
        default:
            return List.of();
        }
    }

    /**
     * Finds the canonical index of the section whose localized name matches a region name. Suffixed
     * sections (the form-table handlers region) match by prefix.
     *
     * @param sections the canonical sections (see {@link #sectionsFor})
     * @param regionName the actual region name
     * @param scriptVariant the project's script variant
     * @return the index in {@code sections}, or {@code -1} if the region is not a standard one
     */
    public static int indexOfRegion(List<ModuleStructureSection> sections, String regionName,
        ScriptVariant scriptVariant)
    {
        if (regionName == null)
        {
            return -1;
        }
        String lower = regionName.toLowerCase(Locale.ROOT);
        for (int i = 0; i < sections.size(); i++)
        {
            ModuleStructureSection section = sections.get(i);
            String name = section.getName(scriptVariant);
            if (section.isSuffixed())
            {
                if (lower.startsWith(name.toLowerCase(Locale.ROOT)))
                {
                    return i;
                }
            }
            else if (regionName.equalsIgnoreCase(name))
            {
                return i;
            }
        }
        return -1;
    }
}
