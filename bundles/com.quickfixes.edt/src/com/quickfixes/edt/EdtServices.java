package com.quickfixes.edt;

import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import com._1c.g5.v8.dt.bsl.resource.BslEventsService;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.validation.marker.IMarkerManager;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;

/**
 * Holds the OSGi service trackers for the EDT platform services this plug-in consumes, plus typed
 * getters. Opened in {@link Activator#start} and closed in {@link Activator#stop}.
 */
public class EdtServices
{
    private ServiceTracker<IMarkerManager, IMarkerManager> markerManagerTracker;
    private ServiceTracker<ICheckRepository, ICheckRepository> checkRepositoryTracker;
    private ServiceTracker<IV8ProjectManager, IV8ProjectManager> v8ProjectManagerTracker;

    /**
     * Opens the service trackers.
     *
     * @param context the bundle context
     */
    public void init(BundleContext context)
    {
        markerManagerTracker = new ServiceTracker<>(context, IMarkerManager.class, null);
        markerManagerTracker.open();

        checkRepositoryTracker = new ServiceTracker<>(context, ICheckRepository.class, null);
        checkRepositoryTracker.open();

        v8ProjectManagerTracker = new ServiceTracker<>(context, IV8ProjectManager.class, null);
        v8ProjectManagerTracker.open();
    }

    /** Closes the service trackers. */
    public void dispose()
    {
        markerManagerTracker = close(markerManagerTracker);
        checkRepositoryTracker = close(checkRepositoryTracker);
        v8ProjectManagerTracker = close(v8ProjectManagerTracker);
    }

    private static <S, T> ServiceTracker<S, T> close(ServiceTracker<S, T> tracker)
    {
        if (tracker != null)
        {
            tracker.close();
        }
        return null;
    }

    /** @return the EDT marker manager, or {@code null} if unavailable */
    public IMarkerManager getMarkerManager()
    {
        return markerManagerTracker == null ? null : markerManagerTracker.getService();
    }

    /** @return the check repository (short UID -&gt; symbolic id), or {@code null} if unavailable */
    public ICheckRepository getCheckRepository()
    {
        return checkRepositoryTracker == null ? null : checkRepositoryTracker.getService();
    }

    /** @return the EDT project manager, or {@code null} if unavailable */
    public IV8ProjectManager getV8ProjectManager()
    {
        return v8ProjectManagerTracker == null ? null : v8ProjectManagerTracker.getService();
    }

    /**
     * Resolves the BSL {@link BslEventsService} from the Xtext injector bound to the {@code .bsl}
     * language. Used to map form methods to their event-handler bindings.
     *
     * @return the BSL events service, or {@code null} if it cannot be resolved
     */
    public BslEventsService getBslEventsService()
    {
        try
        {
            IResourceServiceProvider rsp = IResourceServiceProvider.Registry.INSTANCE
                .getResourceServiceProvider(URI.createURI("synthetic.bsl")); //$NON-NLS-1$
            return rsp == null ? null : rsp.get(BslEventsService.class);
        }
        catch (Exception e)
        {
            Activator.logError("Failed to resolve BslEventsService", e); //$NON-NLS-1$
            return null;
        }
    }
}
