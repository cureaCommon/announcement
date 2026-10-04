package com.curea.announcement.service;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HtmlSanitizer {

    private static final Map<HTML.Tag, String> BLOCK_OR_INLINE = Map.ofEntries(
            Map.entry(HTML.Tag.P, "p"),
            Map.entry(HTML.Tag.DIV, "p"),
            Map.entry(HTML.Tag.B, "strong"),
            Map.entry(HTML.Tag.STRONG, "strong"),
            Map.entry(HTML.Tag.I, "em"),
            Map.entry(HTML.Tag.EM, "em"),
            Map.entry(HTML.Tag.U, "u"),
            Map.entry(HTML.Tag.UL, "ul"),
            Map.entry(HTML.Tag.OL, "ol"),
            Map.entry(HTML.Tag.LI, "li"),
            Map.entry(HTML.Tag.H1, "h2"),
            Map.entry(HTML.Tag.H2, "h2"),
            Map.entry(HTML.Tag.H3, "h3"),
            Map.entry(HTML.Tag.H4, "h4"),
            Map.entry(HTML.Tag.BLOCKQUOTE, "blockquote"),
            Map.entry(HTML.Tag.A, "a")
    );

    private static final Set<HTML.Tag> DROP_CONTENT = Set.of(HTML.Tag.SCRIPT, HTML.Tag.STYLE);

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Collector collector = new Collector();
        try {
            new ParserDelegator().parse(new StringReader(html), collector, true);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return collector.result();
    }

    public static String plainText(String sanitizedHtml) {
        if (sanitizedHtml == null) {
            return "";
        }
        return sanitizedHtml.replaceAll("<[^>]+>", " ")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    private static final class Collector extends HTMLEditorKit.ParserCallback {

        private final StringBuilder out = new StringBuilder();
        private final Deque<String> open = new ArrayDeque<>();
        private int dropDepth;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
            if (DROP_CONTENT.contains(tag)) {
                dropDepth++;
                return;
            }
            if (dropDepth > 0) {
                return;
            }
            String name = BLOCK_OR_INLINE.get(tag);
            if (name == null) {
                return;
            }
            if ("a".equals(name)) {
                String href = safeHref(attributes.getAttribute(HTML.Attribute.HREF));
                if (href == null) {
                    open.push("");
                    return;
                }
                out.append("<a href=\"").append(escape(href)).append("\" target=\"_blank\" rel=\"noopener noreferrer\">");
                open.push("a");
                return;
            }
            out.append('<').append(name).append('>');
            open.push(name);
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int pos) {
            if (DROP_CONTENT.contains(tag)) {
                dropDepth = Math.max(0, dropDepth - 1);
                return;
            }
            if (dropDepth > 0) {
                return;
            }
            String name = BLOCK_OR_INLINE.get(tag);
            if (name == null || open.isEmpty()) {
                return;
            }
            if (!open.contains(name) && !("a".equals(name) && open.contains(""))) {
                return;
            }
            while (!open.isEmpty()) {
                String top = open.pop();
                if (!top.isEmpty()) {
                    out.append("</").append(top).append('>');
                }
                if (top.equals(name) || ("a".equals(name) && top.isEmpty())) {
                    break;
                }
            }
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
            if (dropDepth == 0 && tag == HTML.Tag.BR) {
                out.append("<br>");
            }
        }

        @Override
        public void handleText(char[] data, int pos) {
            if (dropDepth == 0) {
                out.append(escape(new String(data)));
            }
        }

        String result() {
            while (!open.isEmpty()) {
                String top = open.pop();
                if (!top.isEmpty()) {
                    out.append("</").append(top).append('>');
                }
            }
            return out.toString().replaceAll("<p></p>", "").trim();
        }

        private static String safeHref(Object value) {
            if (value == null) {
                return null;
            }
            String href = value.toString().trim();
            String lower = href.toLowerCase(Locale.ROOT);
            if (lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:")) {
                return href;
            }
            return null;
        }

        private static String escape(String text) {
            StringBuilder sb = new StringBuilder(text.length());
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '<' -> sb.append("&lt;");
                    case '>' -> sb.append("&gt;");
                    case '&' -> sb.append("&amp;");
                    case '"' -> sb.append("&quot;");
                    case '\'' -> sb.append("&#39;");
                    default -> sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
