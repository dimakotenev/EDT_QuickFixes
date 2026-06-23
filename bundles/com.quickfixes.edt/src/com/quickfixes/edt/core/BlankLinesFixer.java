package com.quickfixes.edt.core;

import java.util.List;
import java.util.Set;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MultiTextEdit;

/**
 * Fixes {@code module-consecutive-blank-lines}: collapses each run of consecutive blank lines that the
 * check flagged down to a single blank line. Driven by the flagged lines (so runs the check considers
 * acceptable are left untouched).
 */
public final class BlankLinesFixer
{
    /** Check id this fixer acts on. */
    public static final String CHECK_ID = "module-consecutive-blank-lines"; //$NON-NLS-1$

    private BlankLinesFixer()
    {
    }

    /** Computed edit plus the number of removed lines. */
    public static final class Plan
    {
        /** The combined delete edit (empty when {@link #removed} is 0). */
        public final MultiTextEdit edit;
        /** Number of blank lines removed. */
        public final int removed;

        Plan(MultiTextEdit edit, int removed)
        {
            this.edit = edit;
            this.removed = removed;
        }
    }

    /**
     * @param document the editor document
     * @param flaggedLines 1-based lines flagged by the check
     * @return the plan
     */
    public static Plan collapse(IDocument document, Set<Integer> flaggedLines)
    {
        MultiTextEdit root = new MultiTextEdit();
        int removed = 0;
        if (flaggedLines.isEmpty())
        {
            return new Plan(root, 0);
        }
        List<String> lines = BslModuleText.readLines(document);
        int count = lines.size();
        int i = 0;
        while (i < count)
        {
            if (!isBlank(lines.get(i)))
            {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < count && isBlank(lines.get(j + 1)))
            {
                j++;
            }
            // Run of blank lines: 0-based [i, j]; keep the first, delete the rest if the run was flagged.
            if (j > i && isRunFlagged(flaggedLines, i + 1, j + 1))
            {
                try
                {
                    int delStart = document.getLineOffset(i + 1);
                    int delEnd = j + 1 < count ? document.getLineOffset(j + 1) : document.getLength();
                    root.addChild(new DeleteEdit(delStart, delEnd - delStart));
                    removed += j - i;
                }
                catch (BadLocationException e)
                {
                    // skip this run
                }
            }
            i = j + 1;
        }
        return new Plan(root, removed);
    }

    private static boolean isRunFlagged(Set<Integer> flaggedLines, int firstLine, int lastLine)
    {
        for (int line = firstLine; line <= lastLine; line++)
        {
            if (flaggedLines.contains(Integer.valueOf(line)))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String line)
    {
        return line.trim().isEmpty();
    }
}
