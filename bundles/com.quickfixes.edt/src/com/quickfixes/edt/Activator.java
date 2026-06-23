package com.quickfixes.edt;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator (singleton): owns the {@link EdtServices} lifecycle and small logging helpers.
 */
public class Activator extends AbstractUIPlugin
{
    /** Bundle symbolic name / plug-in id. */
    public static final String PLUGIN_ID = "com.quickfixes.edt"; //$NON-NLS-1$

    private static Activator plugin;

    private final EdtServices services = new EdtServices();

    @Override
    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;
        services.init(context);
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
        services.dispose();
        plugin = null;
        super.stop(context);
    }

    /**
     * @return the shared instance, or {@code null} if the bundle is not started
     */
    public static Activator getDefault()
    {
        return plugin;
    }

    /**
     * @return the EDT service accessors
     */
    public EdtServices getServices()
    {
        return services;
    }

    /**
     * Logs an error to the platform log.
     *
     * @param message human-readable message
     * @param t the throwable, may be {@code null}
     */
    public static void logError(String message, Throwable t)
    {
        Activator p = plugin;
        if (p != null)
        {
            p.getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message, t));
        }
    }

    /**
     * Logs an informational message to the platform log.
     *
     * @param message human-readable message
     */
    public static void logInfo(String message)
    {
        Activator p = plugin;
        if (p != null)
        {
            p.getLog().log(new Status(IStatus.INFO, PLUGIN_ID, message));
        }
    }
}
