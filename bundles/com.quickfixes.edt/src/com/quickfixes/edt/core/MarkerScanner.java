package com.quickfixes.edt.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.common.util.URI;

import com._1c.g5.v8.dt.validation.marker.IExtraInfoMap;
import com._1c.g5.v8.dt.validation.marker.IMarkerManager;
import com._1c.g5.v8.dt.validation.marker.Marker;
import com._1c.g5.v8.dt.validation.marker.StandardExtraInfo;
import com.e1c.g5.v8.dt.check.settings.CheckUid;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;

/**
 * Collects EDT validation markers for the active module file, filtered by a set of symbolic check ids.
 * Reads only {@link Marker#getCheckId()} and {@link Marker#getExtraInfo()} (plain values), so no BM read
 * transaction is required. The symbolic id is resolved from the marker's short UID via
 * {@link ICheckRepository}.
 */
public final class MarkerScanner
{
    /** One marker located in the active module. */
    public static final class MarkerEntry
    {
        /** Symbolic check id (e.g. {@code module-unused-method}). */
        public final String checkId;
        /** 1-based source line, or {@code -1}. */
        public final int line;
        /** EMF URI fragment of the problem object (e.g. {@code /0/@methods.3}), or {@code null}. */
        public final String uriFragment;

        MarkerEntry(String checkId, int line, String uriFragment)
        {
            this.checkId = checkId;
            this.line = line;
            this.uriFragment = uriFragment;
        }
    }

    private MarkerScanner()
    {
    }

    /**
     * @param markerManager the marker manager
     * @param checkRepository short-UID-&gt;symbolic-id resolver
     * @param project the project owning the module
     * @param file the active {@code .bsl} file
     * @param checkIds the symbolic check ids to keep
     * @return matching markers in this module (may be empty)
     */
    public static List<MarkerEntry> collect(IMarkerManager markerManager, ICheckRepository checkRepository,
        IProject project, IFile file, Set<String> checkIds)
    {
        List<MarkerEntry> entries = new ArrayList<>();
        if (markerManager == null || project == null || file == null || checkIds == null || checkIds.isEmpty())
        {
            return entries;
        }
        final String filePath = normalize(file.getFullPath().toString());
        markerManager.markers().forEach(marker -> {
            if (!project.equals(marker.getProject()))
            {
                return;
            }
            String checkId = resolveSymbolicCheckId(marker, checkRepository);
            if (checkId == null || !checkIds.contains(checkId))
            {
                return;
            }
            IExtraInfoMap extraInfo = marker.getExtraInfo();
            if (extraInfo == null)
            {
                return;
            }
            String uriToProblem = extraInfo.get(StandardExtraInfo.TEXT_URI_TO_PROBLEM);
            if (!isSameFile(uriToProblem, filePath))
            {
                return;
            }
            Integer line = extraInfo.get(StandardExtraInfo.TEXT_LINE);
            entries.add(new MarkerEntry(checkId, line != null ? line.intValue() : -1, fragmentOf(uriToProblem)));
        });
        return entries;
    }

    /**
     * @return the set of 1-based lines flagged by a single check id in the module.
     */
    public static Set<Integer> linesForCheck(IMarkerManager markerManager, ICheckRepository checkRepository,
        IProject project, IFile file, String checkId)
    {
        Set<Integer> lines = new HashSet<>();
        for (MarkerEntry entry : collect(markerManager, checkRepository, project, file, Set.of(checkId)))
        {
            if (entry.line >= 1)
            {
                lines.add(Integer.valueOf(entry.line));
            }
        }
        return lines;
    }

    static String resolveSymbolicCheckId(Marker marker, ICheckRepository checkRepository)
    {
        try
        {
            String shortUid = marker.getCheckId();
            if (checkRepository == null || shortUid == null || shortUid.isEmpty() || marker.getProject() == null)
            {
                return null;
            }
            CheckUid uid = checkRepository.getUidForShortUid(shortUid, marker.getProject());
            return uid != null ? uid.getCheckId() : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String fragmentOf(String uriToProblem)
    {
        if (uriToProblem == null || uriToProblem.isEmpty())
        {
            return null;
        }
        try
        {
            return URI.createURI(uriToProblem).fragment();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static boolean isSameFile(String uriToProblem, String filePath)
    {
        if (uriToProblem == null || uriToProblem.isEmpty())
        {
            return false;
        }
        try
        {
            URI uri = URI.createURI(uriToProblem).trimFragment();
            String platformString = uri.isPlatformResource() ? uri.toPlatformString(true) : null;
            return platformString != null && normalize(platformString).equals(filePath);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static String normalize(String path)
    {
        if (path == null)
        {
            return null;
        }
        return path.startsWith("/") ? path.substring(1) : path; //$NON-NLS-1$
    }
}
