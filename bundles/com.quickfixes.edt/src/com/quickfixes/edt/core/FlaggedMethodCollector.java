package com.quickfixes.edt.core;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.dt.validation.marker.IMarkerManager;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;

/**
 * Collects the 1-based lines of methods that need relocating: those flagged by
 * {@code module-structure-method-in-regions} (a method in the wrong/missing region) and by
 * {@code module-structure-form-event-regions} (a form event handler in the wrong region). The target
 * region for each is computed by {@link TargetRegionResolver}. Thin wrapper over {@link MarkerScanner}.
 */
public final class FlaggedMethodCollector
{
    /** Generic "method outside its standard region" check. */
    public static final String CHECK_ID = "module-structure-method-in-regions"; //$NON-NLS-1$
    /** Form event-handler region check. */
    public static final String FORM_EVENT_CHECK_ID = "module-structure-form-event-regions"; //$NON-NLS-1$

    private static final Set<String> CHECK_IDS = Set.of(CHECK_ID, FORM_EVENT_CHECK_ID);

    private FlaggedMethodCollector()
    {
    }

    /**
     * @param markerManager the EDT marker manager
     * @param checkRepository the check repository
     * @param project the project owning the active module
     * @param file the active module's {@code .bsl} file
     * @return 1-based flagged lines (may be empty)
     */
    public static Set<Integer> collectFlaggedLines(IMarkerManager markerManager,
        ICheckRepository checkRepository, IProject project, IFile file)
    {
        Set<Integer> lines = new HashSet<>();
        for (MarkerScanner.MarkerEntry entry : MarkerScanner.collect(markerManager, checkRepository, project, file,
            CHECK_IDS))
        {
            if (entry.line >= 1)
            {
                lines.add(Integer.valueOf(entry.line));
            }
        }
        return lines;
    }
}
