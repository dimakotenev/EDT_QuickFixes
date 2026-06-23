package com.quickfixes.edt.core;

import com._1c.g5.v8.dt.metadata.mdclass.ScriptVariant;

/**
 * Standard 1C module-structure regions and their canonical Russian/English names.
 *
 * <p>Ported from the open <a href="https://github.com/1C-Company/v8-code-style">1C:Code Style</a>
 * project ({@code com.e1c.v8codestyle.bsl.ModuleStructureSection}) so this plug-in does not depend
 * on that extension being installed. The name is selected by the project's {@link ScriptVariant}
 * ({@code ENGLISH}=0, {@code RUSSIAN}=1).</p>
 */
public enum ModuleStructureSection
{
    VARIABLES("Variables", "ОписаниеПеременных"), //$NON-NLS-1$ //$NON-NLS-2$
    PUBLIC("Public", "ПрограммныйИнтерфейс"), //$NON-NLS-1$ //$NON-NLS-2$
    EVENT_HANDLERS("EventHandlers", "ОбработчикиСобытий"), //$NON-NLS-1$ //$NON-NLS-2$
    INTERNAL("Internal", "СлужебныйПрограммныйИнтерфейс"), //$NON-NLS-1$ //$NON-NLS-2$
    FORM_EVENT_HANDLERS("FormEventHandlers", "ОбработчикиСобытийФормы"), //$NON-NLS-1$ //$NON-NLS-2$
    FORM_HEADER_ITEMS_EVENT_HANDLERS("FormHeaderItemsEventHandlers", //$NON-NLS-1$
        "ОбработчикиСобытийЭлементовШапкиФормы"), //$NON-NLS-1$
    FORM_TABLE_ITEMS_EVENT_HANDLERS("FormTableItemsEventHandlers", //$NON-NLS-1$
        "ОбработчикиСобытийЭлементовТаблицыФормы", true), //$NON-NLS-1$
    FORM_COMMAND_EVENT_HANDLERS("FormCommandsEventHandlers", "ОбработчикиКомандФормы"), //$NON-NLS-1$ //$NON-NLS-2$
    PRIVATE("Private", "СлужебныеПроцедурыИФункции"), //$NON-NLS-1$ //$NON-NLS-2$
    INITIALIZE("Initialize", "Инициализация"), //$NON-NLS-1$ //$NON-NLS-2$
    DEPRECATED_REGION("Deprecated", "УстаревшиеПроцедурыИФункции"); //$NON-NLS-1$ //$NON-NLS-2$

    private final String[] names; // [0]=English, [1]=Russian
    private final boolean suffixed;

    ModuleStructureSection(String englishName, String russianName)
    {
        this(englishName, russianName, false);
    }

    ModuleStructureSection(String englishName, String russianName, boolean suffixed)
    {
        this.names = new String[] { englishName, russianName };
        this.suffixed = suffixed;
    }

    /**
     * @param scriptVariant the project's script variant
     * @return the region name in the project's language
     */
    public String getName(ScriptVariant scriptVariant)
    {
        int index = scriptVariant == null ? 0 : scriptVariant.getValue();
        if (index < 0 || index >= names.length)
        {
            index = 0;
        }
        return names[index];
    }

    /**
     * @return {@code true} when the real region name is this base name plus a suffix
     *         (the form-table handlers region is suffixed with the table name)
     */
    public boolean isSuffixed()
    {
        return suffixed;
    }
}
