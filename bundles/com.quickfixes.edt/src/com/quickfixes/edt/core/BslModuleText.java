package com.quickfixes.edt.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

/**
 * Text-level helpers over a BSL module document: reading lines, scanning {@code #Region}/{@code #Область}
 * blocks (with nesting depth), locating the containing region of a line, and computing the character
 * range of a method (including a directly preceding doc-comment block).
 *
 * <p>Region boundaries are detected from the source text because the AST end line of a
 * {@code RegionPreprocessor} is not reliable. Shared by all region/method operations.</p>
 */
public final class BslModuleText
{
    private BslModuleText()
    {
    }

    /** A scanned {@code #Region}…{@code #EndRegion} block. Line numbers are 1-based. */
    public static final class Region
    {
        /** Region name (the identifier after {@code #Region}/{@code #Область}). */
        public final String name;
        /** 1-based line of the {@code #Region} directive. */
        public final int startLine;
        /** 1-based line of the matching {@code #EndRegion} directive. */
        public final int endLine;
        /** Nesting depth, 0 for a top-level region. */
        public final int depth;

        Region(String name, int startLine, int endLine, int depth)
        {
            this.name = name;
            this.startLine = startLine;
            this.endLine = endLine;
            this.depth = depth;
        }

        /** @return {@code true} if this is a top-level region. */
        public boolean isTopLevel()
        {
            return depth == 0;
        }
    }

    /**
     * Reads the document as a list of lines without their delimiters; index {@code i} is line {@code i+1}.
     *
     * @param document the document
     * @return the lines
     */
    public static List<String> readLines(IDocument document)
    {
        List<String> lines = new ArrayList<>();
        int count = document.getNumberOfLines();
        for (int i = 0; i < count; i++)
        {
            try
            {
                IRegion info = document.getLineInformation(i);
                lines.add(document.get(info.getOffset(), info.getLength()));
            }
            catch (BadLocationException e)
            {
                lines.add(""); //$NON-NLS-1$
            }
        }
        return lines;
    }

    /**
     * Scans {@code #Region}…{@code #EndRegion} blocks, tracking nesting depth.
     *
     * @param lines the module lines (see {@link #readLines})
     * @return the regions, in the order their {@code #EndRegion} is encountered
     */
    public static List<Region> scanRegions(List<String> lines)
    {
        List<Region> regions = new ArrayList<>();
        Deque<Open> stack = new ArrayDeque<>();
        for (int i = 0; i < lines.size(); i++)
        {
            int lineNumber = i + 1;
            String trimmedLower = lines.get(i).trim().toLowerCase(Locale.ROOT);
            if (isRegionStart(trimmedLower))
            {
                stack.push(new Open(parseRegionName(lines.get(i)), lineNumber, stack.size()));
            }
            else if (isRegionEnd(trimmedLower) && !stack.isEmpty())
            {
                Open open = stack.pop();
                regions.add(new Region(open.name, open.startLine, lineNumber, open.depth));
            }
        }
        return regions;
    }

    /** @return the top-level regions (depth 0), in start-line order. */
    public static List<Region> topLevelRegions(List<Region> regions)
    {
        List<Region> top = new ArrayList<>();
        for (Region region : regions)
        {
            if (region.isTopLevel())
            {
                top.add(region);
            }
        }
        top.sort((a, b) -> Integer.compare(a.startLine, b.startLine));
        return top;
    }

    /** @return the narrowest region whose {@code [startLine, endLine]} contains {@code line}, or {@code null}. */
    public static Region narrowestContaining(List<Region> regions, int line)
    {
        Region best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (Region region : regions)
        {
            if (line >= region.startLine && line <= region.endLine)
            {
                int span = region.endLine - region.startLine;
                if (span < bestSpan)
                {
                    bestSpan = span;
                    best = region;
                }
            }
        }
        return best;
    }

    /** @return the first region whose name equals {@code name} (case-insensitive), or {@code null}. */
    public static Region findByName(List<Region> regions, String name)
    {
        for (Region region : regions)
        {
            if (region.name.equalsIgnoreCase(name))
            {
                return region;
            }
        }
        return null;
    }

    /**
     * Computes the character range {@code [start, end)} to cut for a method: from the start of its first
     * line (including a directly preceding doc-comment block of {@code //} lines) through the line
     * terminator after its last line.
     *
     * @param document the document
     * @param lines the module lines
     * @param startLine 1-based first line of the method node
     * @param endLine 1-based last line of the method node
     * @return {@code [start, end)} character offsets
     * @throws BadLocationException on invalid line math
     */
    public static int[] methodRange(IDocument document, List<String> lines, int startLine, int endLine)
        throws BadLocationException
    {
        int firstLine0 = startLine - 1;
        int i = firstLine0 - 1;
        while (i >= 0 && lines.get(i).trim().startsWith("//")) //$NON-NLS-1$
        {
            i--;
        }
        int delStartLine0 = i + 1;
        int delStart = document.getLineOffset(delStartLine0);
        int delEnd;
        if (endLine < document.getNumberOfLines())
        {
            delEnd = document.getLineOffset(endLine);
        }
        else
        {
            delEnd = document.getLength();
        }
        return new int[] { delStart, delEnd };
    }

    /**
     * Character range {@code [start, end)} covering whole lines {@code startLine..endLine} (1-based),
     * including the trailing line terminator — without any doc-comment extension.
     *
     * @param document the document
     * @param startLine 1-based first line
     * @param endLine 1-based last line
     * @return {@code [start, end)} offsets
     * @throws BadLocationException on invalid line math
     */
    public static int[] lineRange(IDocument document, int startLine, int endLine) throws BadLocationException
    {
        int delStart = document.getLineOffset(startLine - 1);
        int delEnd =
            endLine < document.getNumberOfLines() ? document.getLineOffset(endLine) : document.getLength();
        return new int[] { delStart, delEnd };
    }

    /** @return {@code true} if the trimmed, lower-cased line opens a region. */
    public static boolean isRegionStart(String trimmedLower)
    {
        return trimmedLower.startsWith("#region") || trimmedLower.startsWith("#область"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** @return {@code true} if the trimmed, lower-cased line closes a region. */
    public static boolean isRegionEnd(String trimmedLower)
    {
        return trimmedLower.startsWith("#endregion") || trimmedLower.startsWith("#конецобласти"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** @return the region name from a {@code #Region X}/{@code #Область X} line, or {@code ""}. */
    public static String parseRegionName(String line)
    {
        String s = line.trim();
        if (s.startsWith("#")) //$NON-NLS-1$
        {
            s = s.substring(1).trim();
        }
        int afterKeyword = indexOfWhitespace(s);
        if (afterKeyword < 0)
        {
            return ""; //$NON-NLS-1$
        }
        String rest = s.substring(afterKeyword).trim();
        int afterName = indexOfWhitespace(rest);
        return afterName < 0 ? rest : rest.substring(0, afterName);
    }

    private static int indexOfWhitespace(String s)
    {
        for (int i = 0; i < s.length(); i++)
        {
            if (Character.isWhitespace(s.charAt(i)))
            {
                return i;
            }
        }
        return -1;
    }

    private static final class Open
    {
        final String name;
        final int startLine;
        final int depth;

        Open(String name, int startLine, int depth)
        {
            this.name = name;
            this.startLine = startLine;
            this.depth = depth;
        }
    }
}
