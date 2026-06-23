package com.quickfixes.edt.core;

import java.util.Locale;

import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import com._1c.g5.v8.dt.metadata.mdclass.ScriptVariant;

/**
 * Fixes {@code module-accessibility-at-client}: wraps the whole module in
 * {@code #Если Сервер Или ТолстыйКлиентОбычноеПриложение Или ВнешнееСоединение Тогда … #КонецЕсли}
 * (RU/EN). Applied once per module; declines if the module already starts with a preprocessor
 * directive.
 */
public final class AccessibilityFixer
{
    /** Check id this fixer acts on. */
    public static final String CHECK_ID = "module-accessibility-at-client"; //$NON-NLS-1$

    private AccessibilityFixer()
    {
    }

    /** Either an edit to apply, or an informational message when nothing is applied. */
    public static final class Plan
    {
        /** The edit, or {@code null}. */
        public final TextEdit edit;
        /** Message shown when {@link #edit} is {@code null}. */
        public final String info;

        private Plan(TextEdit edit, String info)
        {
            this.edit = edit;
            this.info = info;
        }

        static Plan ofEdit(TextEdit edit)
        {
            return new Plan(edit, null);
        }

        static Plan ofInfo(String info)
        {
            return new Plan(null, info);
        }
    }

    /**
     * @param document the editor document
     * @param scriptVariant the project's script variant (RU/EN)
     * @return the wrap plan
     */
    public static Plan wrap(IDocument document, ScriptVariant scriptVariant)
    {
        String leading = document.get().stripLeading().toLowerCase(Locale.ROOT);
        if (leading.startsWith("#если") || leading.startsWith("#if")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            return Plan.ofInfo("Модуль уже начинается с препроцессорной директивы — обёртка не применена."); //$NON-NLS-1$
        }
        boolean ru = scriptVariant == ScriptVariant.RUSSIAN;
        String prefix = (ru ? "#Если Сервер Или ТолстыйКлиентОбычноеПриложение Или ВнешнееСоединение Тогда" //$NON-NLS-1$
            : "#If Server Or ThickClientOrdinaryApplication Or ExternalConnection Then") + "\n\n"; //$NON-NLS-1$ //$NON-NLS-2$
        String suffix = "\n" + (ru ? "#КонецЕсли" : "#EndIf") + "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        MultiTextEdit root = new MultiTextEdit();
        root.addChild(new InsertEdit(0, prefix));
        root.addChild(new InsertEdit(document.getLength(), suffix));
        return Plan.ofEdit(root);
    }
}
