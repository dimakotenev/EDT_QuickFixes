package com.quickfixes.edt.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import com._1c.g5.v8.dt.bsl.model.ModuleType;
import com._1c.g5.v8.dt.metadata.mdclass.ScriptVariant;

/**
 * Computes the edit that reorders the top-level regions into canonical order (whole {@code #Region}…
 * {@code #EndRegion} blocks). Non-standard regions keep their relative order and go after the standard
 * ones. To avoid losing anything, if there is any non-blank text between top-level regions the operation
 * is declined.
 */
public final class RegionReorderer
{
    private static final int NON_STANDARD_BASE = 100000;

    private RegionReorderer()
    {
    }

    /** Either an edit to apply (+ outcome) or an informational message when there is nothing/safe to do. */
    public static final class Plan
    {
        /** The edit, or {@code null} when nothing should be applied. */
        public final TextEdit edit;
        /** Outcome (new positions), populated when {@link #edit} is set. */
        public final OpResult result;
        /** Message shown when {@link #edit} is {@code null}. */
        public final String info;

        private Plan(TextEdit edit, OpResult result, String info)
        {
            this.edit = edit;
            this.result = result;
            this.info = info;
        }

        static Plan ofEdit(TextEdit edit, OpResult result)
        {
            return new Plan(edit, result, null);
        }

        static Plan ofInfo(String info)
        {
            return new Plan(null, new OpResult(), info);
        }
    }

    /**
     * @param document the editor document
     * @param moduleType the module type
     * @param scriptVariant the project's script variant
     * @return the reorder plan
     */
    public static Plan compute(IDocument document, ModuleType moduleType, ScriptVariant scriptVariant)
    {
        List<ModuleStructureSection> sections = ModuleStructure.sectionsFor(moduleType);
        List<String> lines = BslModuleText.readLines(document);
        final List<BslModuleText.Region> top = BslModuleText.topLevelRegions(BslModuleText.scanRegions(lines));
        if (top.size() < 2)
        {
            return Plan.ofInfo("В модуле меньше двух областей — переставлять нечего."); //$NON-NLS-1$
        }

        // Data-loss guard: gaps between top regions must be blank only.
        for (int k = 0; k + 1 < top.size(); k++)
        {
            for (int ln = top.get(k).endLine + 1; ln <= top.get(k + 1).startLine - 1; ln++)
            {
                if (!lines.get(ln - 1).trim().isEmpty())
                {
                    return Plan.ofInfo(
                        "Между областями есть текст — перестановка не выполнена, чтобы ничего не потерять."); //$NON-NLS-1$
                }
            }
        }

        // Sort key per region: canonical index, or NON_STANDARD_BASE+position to keep non-standard last.
        Map<BslModuleText.Region, Integer> sortKey = new IdentityHashMap<>();
        for (int p = 0; p < top.size(); p++)
        {
            int idx = ModuleStructure.indexOfRegion(sections, top.get(p).name, scriptVariant);
            sortKey.put(top.get(p), idx >= 0 ? idx : NON_STANDARD_BASE + p);
        }
        List<Integer> order = new ArrayList<>();
        for (int p = 0; p < top.size(); p++)
        {
            order.add(Integer.valueOf(p));
        }
        order.sort((a, b) -> {
            int ka = sortKey.get(top.get(a.intValue())).intValue();
            int kb = sortKey.get(top.get(b.intValue())).intValue();
            return ka != kb ? Integer.compare(ka, kb) : Integer.compare(a.intValue(), b.intValue());
        });

        boolean changed = false;
        for (int p = 0; p < order.size(); p++)
        {
            if (order.get(p).intValue() != p)
            {
                changed = true;
                break;
            }
        }
        if (!changed)
        {
            return Plan.ofInfo("Области уже в правильном порядке."); //$NON-NLS-1$
        }

        OpResult result = new OpResult();
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < order.size(); p++)
        {
            BslModuleText.Region region = top.get(order.get(p).intValue());
            for (int ln = region.startLine; ln <= region.endLine; ln++)
            {
                sb.append(lines.get(ln - 1)).append('\n');
            }
            if (p + 1 < order.size())
            {
                sb.append('\n');
            }
            result.addSuccess(region.name, "позиция " + (p + 1)); //$NON-NLS-1$
        }

        try
        {
            int firstLine = top.get(0).startLine;
            int lastLine = top.get(top.size() - 1).endLine;
            int replaceStart = document.getLineOffset(firstLine - 1);
            int replaceEnd = lastLine < document.getNumberOfLines() ? document.getLineOffset(lastLine)
                : document.getLength();
            TextEdit edit = new ReplaceEdit(replaceStart, replaceEnd - replaceStart, sb.toString());
            return Plan.ofEdit(edit, result);
        }
        catch (BadLocationException e)
        {
            return Plan.ofInfo("Не удалось вычислить диапазон областей."); //$NON-NLS-1$
        }
    }
}
