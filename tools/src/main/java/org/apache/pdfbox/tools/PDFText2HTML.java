/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.tools;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.text.BeginText;
import org.apache.pdfbox.contentstream.operator.text.EndText;
import org.apache.pdfbox.contentstream.operator.text.ShowText;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wrap stripped text in simple HTML, trying to form HTML paragraphs. Paragraphs
 * broken by pages, columns, or figures are not mended.
 *
 * @author John J Barton
 */
public class PDFText2HTML extends PDFTextStripper {
    private static final int INITIAL_PDF_TO_HTML_BYTES = 8192;

    private int currentArticleId = 0;
    private StringBuilder currentArticleStyle = new StringBuilder();

    private final FontState fontState = new FontState();
    private List<Line> lines = new ArrayList<>();
    private List<Line> finalLines = new ArrayList<>();
    private float startPointX = 0;
    private float startPointY = 0;
    private float endPointX = 0;
    private float endPointY = 0;

    /**
     * Constructor.
     *
     * @throws IOException If there is an error during initialization.
     */
    public PDFText2HTML() throws IOException {
        setLineSeparator(LINE_SEPARATOR);
        setParagraphStart("<p>");
        setLineSeparator("<br/>");
        setDropThreshold(4.5f);
        setParagraphEnd("</p>" + LINE_SEPARATOR);
        setPageEnd("</div>" + LINE_SEPARATOR);
        setArticleStart(LINE_SEPARATOR);
        setArticleEnd(LINE_SEPARATOR);

        addOperator(new BeginText(this));
        addOperator(new EndText(this));
        addOperator(new ShowText(this));
        addOperator(new Save(this));
    }

    /**
     * This is used to handle an operation.
     *
     * @param arguments The list of arguments.
     * @throws IOException If there is an error processing the operation.
     */
    @Override
    public void processOperator(Operator operator, List<COSBase> arguments) throws IOException {
        String operation = operator.getName();
        if (operation.equals("m")) {
            // line start point.
            startPointX = toFloat(arguments.get(0));
            startPointY = toFloat(arguments.get(1));
            endPointX = toFloat(arguments.get(0));
            endPointY = toFloat(arguments.get(1));

        } else if (operation.equals("l")) {
            if (arguments.size() == 2) {
                // line end point.
                endPointX = toFloat(arguments.get(0));
                endPointY = toFloat(arguments.get(1));
                lines.add(new Line(startPointX, startPointY, endPointX, endPointY));
            } else {
                System.out.println("无效");
            }
        }else if(operation.equals("S")){
            finalLines.addAll(lines);
            lines.clear();
        }else if(operation.equals("s")){
            finalLines.addAll(lines);
            lines.clear();
        }else if(operation.equals("B") || operation.equals("B*")){
            finalLines.addAll(lines);
            lines.clear();
        }else if(operation.equals("b") || operation.equals("b*")){
            finalLines.addAll(lines);
            lines.clear();
        }else if (operation.equals("f") || operation.equals("F") || operation.equals("f*")) {
            lines.clear();
        }else if(operation.equals("n")){
            lines.clear();
        }

        super.processOperator(operator, arguments);
    }

    private float toFloat(COSBase base) {
        if (base instanceof COSFloat) {
            return ((COSFloat) base).floatValue();
        }
        return 0f;
    }

    @Override
    protected void startDocument(PDDocument document) throws IOException {
        StringBuilder buf = new StringBuilder(INITIAL_PDF_TO_HTML_BYTES);
        buf.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"" + "\n"
                + "\"http://www.w3.org/TR/html4/loose.dtd\">\n");
        buf.append("<html><head>");
        buf.append("<title>").append(escape(getTitle())).append("</title>\n");
        buf.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
        buf.append("</head>\n");
        buf.append("<body>\n");
        super.writeString(buf.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endDocument(PDDocument document) throws IOException {
        super.writeString("</body><style>" + currentArticleStyle.toString() + "</style></html>");
    }

    /**
     * Start a new page. Default implementation is to do nothing. Subclasses may provide additional information.
     *
     * @param page The page we are about to process.
     * @throws IOException If there is any error writing to the stream.
     */
    @Override
    protected void startPage(PDPage page) throws IOException {
        setPageStart(
                "<div id=article-" + currentArticleId + " style=\"page-break-before:always; page-break-after:always;border:1px solid black;position:relative;\">");
    }

    /**
     * End a page. Default implementation is to do nothing. Subclasses may provide additional information.
     *
     * @param page The page we are about to process.
     * @throws IOException If there is any error writing to the stream.
     */
    @Override
    protected void endPage(PDPage page) throws IOException {
        this.lines.clear();
        this.finalLines.clear();
        super.endPage(page);
    }

    /**
     * Write something (if defined) at the end of a page.
     *
     * @throws IOException if something went wrong
     */
    protected void writePageEnd() throws IOException {
        String lineElements = this.finalLines.stream().map(Line::getDomElement).collect(Collectors.joining("\n"));
        String currentPageEnd = lineElements + "</div>";
        setPageEnd(currentPageEnd);
        super.writePageEnd();
        float paddingLeft = getMinPaddingLeft();
        float paddingRight = getMinPaddingRight();

        String style = "#article-" + this.currentArticleId + "{" +
                "padding-left:" + 0 + "pt;" +
                "padding-right:" + 0 + "pt;" +
                "}\n";

        this.currentArticleId++;
        currentArticleStyle.append(style);
    }

    /**
     * This method will attempt to guess the title of the document using
     * either the document properties or the first lines of text.
     *
     * @return returns the title.
     */
    protected String getTitle() {
        String titleGuess = document.getDocumentInformation().getTitle();
        if (titleGuess != null && titleGuess.length() > 0) {
            return titleGuess;
        } else {
            Iterator<List<TextPosition>> textIter = getCharactersByArticle().iterator();
            float lastFontSize = -1.0f;

            StringBuilder titleText = new StringBuilder();
            while (textIter.hasNext()) {
                for (TextPosition position : textIter.next()) {
                    float currentFontSize = position.getFontSize();
                    //If we're past 64 chars we will assume that we're past the title
                    //64 is arbitrary
                    if (Float.compare(currentFontSize, lastFontSize) != 0 || titleText.length() > 64) {
                        if (titleText.length() > 0) {
                            return titleText.toString();
                        }
                        lastFontSize = currentFontSize;
                    }
                    if (currentFontSize > 13.0f) { // most body text is 12pt
                        titleText.append(position.getUnicode());
                    }
                }
            }
        }
        return "";
    }


    /**
     * Write out the article separator (div tag) with proper text direction
     * information.
     *
     * @param isLTR true if direction of text is left to right
     * @throws IOException If there is an error writing to the stream.
     */
    @Override
    protected void startArticle(boolean isLTR) throws IOException {
        if (isLTR) {
            super.writeString("<div dir=\"LTR\">");
        } else {
            super.writeString("<div dir=\"RTL\">");
        }
    }

    /**
     * Write out the article separator.
     *
     * @throws IOException If there is an error writing to the stream.
     */
    @Override
    protected void endArticle() throws IOException {
        super.endArticle();
        super.writeString("</div>");
    }

    /**
     * Write a string to the output stream, maintain font state, and escape some HTML characters.
     * The font state is only preserved per word.
     *
     * @param text          The text to write to the stream.
     * @param textPositions the corresponding text positions
     * @throws IOException If there is an error writing to the stream.
     */
    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        super.writeString(fontState.push(text, textPositions));
    }

    /**
     * Write a string to the output stream and escape some HTML characters.
     *
     * @param chars String to be written to the stream
     * @throws IOException If there is an error writing to the stream.
     */
    @Override
    protected void writeString(String chars) throws IOException {
        super.writeString(escape(chars));
    }

    /**
     * Writes the paragraph end "&lt;/p&gt;" to the output. Furthermore, it will also clear the font state.
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected void writeParagraphEnd() throws IOException {
        // do not escape HTML
        super.writeString(fontState.clear());

        super.writeParagraphEnd();
    }

    /**
     * Escape some HTML characters.
     *
     * @param chars String to be escaped
     * @return returns escaped String.
     */
    private static String escape(String chars) {
        StringBuilder builder = new StringBuilder(chars.length());
        for (int i = 0; i < chars.length(); i++) {
            appendEscaped(builder, chars.charAt(i));
        }
        return builder.toString();
    }

    private static void appendEscaped(StringBuilder builder, char character) {
        // write non-ASCII as named entities
        if ((character < 32) || (character > 126)) {
            builder.append("&#").append((int) character).append(';');
        } else {
            switch (character) {
                case 34:
                    builder.append("&quot;");
                    break;
                case 38:
                    builder.append("&amp;");
                    break;
                case 60:
                    builder.append("&lt;");
                    break;
                case 62:
                    builder.append("&gt;");
                    break;
                default:
                    builder.append(character);
            }
        }
    }

    /**
     * A helper class to maintain the current font state. It's public methods will emit opening and
     * closing tags as needed, and in the correct order.
     *
     * @author Axel Dörfler
     */
    private static class FontState {
        private final List<String> stateList = new ArrayList<>();
        private final Set<String> stateSet = new HashSet<>();

        /**
         * Pushes new {@link TextPosition TextPositions} into the font state. The state is only
         * preserved correctly for each letter if the number of letters in <code>text</code> matches
         * the number of {@link TextPosition} objects. Otherwise, it's done once for the complete
         * array (just by looking at its first entry).
         *
         * @return A string that contains the text including tag changes caused by its font state.
         */
        public String push(String text, List<TextPosition> textPositions) {
            StringBuilder buffer = new StringBuilder();

            if (text.length() == textPositions.size()) {
                // There is a 1:1 mapping, and we can use the TextPositions directly
                for (int i = 0; i < text.length(); i++) {
                    push(buffer, text.charAt(i), textPositions.get(i));
                }
            } else if (!text.isEmpty()) {
                // The normalized text does not match the number of TextPositions, so we'll just
                // have a look at its first entry.
                // TODO change PDFTextStripper.normalize() such that it maintains the 1:1 relation
                if (textPositions.isEmpty()) {
                    return text;
                }
                push(buffer, text.charAt(0), textPositions.get(0));
                buffer.append(escape(text.substring(1)));
            }
            return buffer.toString();
        }

        /**
         * Closes all open states.
         *
         * @return A string that contains the closing tags of all currently open states.
         */
        public String clear() {
            StringBuilder buffer = new StringBuilder();
            closeUntil(buffer, null);
            stateList.clear();
            stateSet.clear();
            return buffer.toString();
        }

        protected String push(StringBuilder buffer, char character, TextPosition textPosition) {
            boolean bold = false;
            boolean italics = false;

            PDFontDescriptor descriptor = textPosition.getFont().getFontDescriptor();
            if (descriptor != null) {
                bold = isBold(descriptor);
                italics = isItalic(descriptor);
            }

            buffer.append(bold ? open("b") : close("b"));
            buffer.append(italics ? open("i") : close("i"));
            appendEscaped(buffer, character);

            return buffer.toString();
        }

        private String open(String tag) {
            if (stateSet.contains(tag)) {
                return "";
            }
            stateList.add(tag);
            stateSet.add(tag);

            return openTag(tag);
        }

        private String close(String tag) {
            if (!stateSet.contains(tag)) {
                return "";
            }
            // Close all tags until (but including) the one we should close
            StringBuilder tagsBuilder = new StringBuilder();
            int index = closeUntil(tagsBuilder, tag);

            // Remove from state
            stateList.remove(index);
            stateSet.remove(tag);

            // Now open the states that were closed but should remain open again
            for (; index < stateList.size(); index++) {
                tagsBuilder.append(openTag(stateList.get(index)));
            }
            return tagsBuilder.toString();
        }

        private int closeUntil(StringBuilder tagsBuilder, String endTag) {
            for (int i = stateList.size(); i-- > 0; ) {
                String tag = stateList.get(i);
                tagsBuilder.append(closeTag(tag));
                if (endTag != null && tag.equals(endTag)) {
                    return i;
                }
            }
            return -1;
        }

        private String openTag(String tag) {
            return "<" + tag + ">";
        }

        private String closeTag(String tag) {
            return "</" + tag + ">";
        }

        private boolean isBold(PDFontDescriptor descriptor) {
            if (descriptor.isForceBold()) {
                return true;
            }
            return descriptor.getFontName().contains("Bold");
        }

        private boolean isItalic(PDFontDescriptor descriptor) {
            if (descriptor.isItalic()) {
                return true;
            }
            return descriptor.getFontName().contains("Italic");
        }
    }

    private static class Line {
        private float startX;
        private float startY;
        private float endX;
        private float endY;
        private float width;
        private float height;
        private boolean isHorizon;
        private boolean isVertical;

        public Line(float startX, float startY, float endX, float endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.width = Math.abs(endX - startX);
            this.height = Math.abs(endY - startY);
            this.isHorizon = this.height < 0.5f;
            this.isVertical = this.width < 0.5f;
        }

        public String getDomElement() {
            return "<div style=\"position:absolute; left:" + getLeft() + "pt; top:" + getTop() +
                    "pt; width:" + getWidth() + "pt;height:" + getHeight() + "pt;" +
                    "background-color:black;transform:rotate(" + getAngleDegrees() + "deg);\"></div>";
        }

        public double getAngleDegrees() {
            if (isHorizon || isVertical)
                return 0;
            else
                return Math.toDegrees(Math.atan((this.endY - this.startY) / (endX - startX)));
        }

        public double getLeft()
        {
            if (isHorizon || isVertical)
                return Math.min(startX, endX);
            else
                return Math.abs((endX + startX) / 2) - getWidth() / 2f;
        }

        public double getTop()
        {
            if (isHorizon || isVertical)
                return Math.min(startY, endY);
            else
                // after rotation top left will be center of line so find the midpoint and correct for the line to border transform
                return Math.abs((startY + endY) / 2) - (getWidth() + getHeight()) / 2;
        }

        public double getWidth(){
            if(isHorizon){
                return this.width;
            }else if(isVertical){
                return 1;
            }else{
                return Math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY));
            }
        }

        public double getHeight(){
            if(isVertical){
                return height;
            }
            return 1;
        }

        public float getStartX() {
            return startX;
        }

        public float getStartY() {
            return startY;
        }

        public float getEndX() {
            return endX;
        }

        public float getEndY() {
            return endY;
        }
    }
}
