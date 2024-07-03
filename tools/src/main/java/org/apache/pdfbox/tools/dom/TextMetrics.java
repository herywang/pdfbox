
package org.apache.pdfbox.tools.dom;

import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;

public class TextMetrics {

    /**
     * 段落开始文字的 x坐标
     */
    private float x;
    /**
     * 上一个文字对象信息
     */
    private TextPosition preText;
    private float baseline, width, height, pointSize, descent, ascent, fontSize, currentTextWidth;
    private PDFont font;
    private boolean isNewLine = false;

    public TextMetrics(TextPosition tp) {
        x = tp.getX();
        baseline = tp.getY();
        font = tp.getFont();
        width = tp.getWidth();
        height = tp.getHeight();
        pointSize = tp.getFontSizeInPt();
        fontSize = tp.getYScale();
        ascent = getAscent();
        descent = getDescent();
        this.isNewLine = false;
        this.preText = tp;
        this.currentTextWidth = tp.getWidth();
    }

    public void append(TextPosition tp) {
        if(!isNewLine){
            // 如果当前文字不是新一行的文字，则需要累加当前行的宽度, 计算方式：当前文字的宽度 + 当前文字距离上一个文字的字间距；
            // 当前文字距离上一个文字的字间距 = (当前文字的x坐标 - 上一个文字的x坐标 - 上一个文字的宽度)
            width += tp.getWidth() + (tp.getX() - preText.getX() - preText.getWidth());
        }

        height = Math.max(height, tp.getHeight());
        ascent = Math.max(ascent, getAscent(tp.getFont(), tp.getYScale()));
        descent = Math.min(descent, getDescent(tp.getFont(), tp.getYScale()));
        this.currentTextWidth = tp.getWidth();
        this.preText = tp;
    }

    public float getX() {
        return x;
    }

    public void setX(float x){
        this.x = x;
    }

    public float getTop() {
        if (ascent != 0) {
            return baseline - ascent;
        } else {
            return baseline - getBoundingBoxAscent();
        }
    }

    public boolean isNewLine() {
        return isNewLine;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public void setNewLine(boolean newLine) {
        isNewLine = newLine;
    }

    public void setWidth(float width){
        this.width = width;
    }

    public float getBottom() {
        if (descent != 0) {
            return baseline - descent;
        } else {
            return baseline - getBoundingBoxDescent();
        }
    }

    public float getBaseline() {
        return baseline;
    }

    public float getAscent() {
        return getAscent(font, fontSize);
    }

    public float getDescent() {
        final float descent = getDescent(font, fontSize);
        return descent > 0 ? -descent : descent; //positive descent is not allowed
    }

    public float getBoundingBoxDescent() {
        return getBoundingBoxDescent(font, fontSize);
    }

    public float getBoundingBoxAscent() {
        return getBoundingBoxAscent(font, fontSize);
    }

    public static float getBoundingBoxDescent(PDFont font, float fontSize) {
        try {
            BoundingBox bBox = font.getBoundingBox();
            float boxDescent = bBox.getLowerLeftY();
            return (boxDescent / 1000) * fontSize;
        } catch (IOException e) {
        }
        return 0.0f;
    }

    public static float getBoundingBoxAscent(PDFont font, float fontSize) {
        try {
            BoundingBox bBox = font.getBoundingBox();
            float boxAscent = bBox.getUpperRightY();
            return (boxAscent / 1000) * fontSize;
        } catch (IOException e) {
        }
        return 0.0f;
    }

    private static float getAscent(PDFont font, float fontSize) {
        try {
            return (font.getFontDescriptor().getAscent() / 1000) * fontSize;
        } catch (Exception e) {
        }
        return 0.0f;
    }

    private static float getDescent(PDFont font, float fontSize) {
        try {
            return (font.getFontDescriptor().getDescent() / 1000) * fontSize;
        } catch (Exception e) {
        }
        return 0.0f;
    }

    public float getWidth() {
        return width + this.currentTextWidth;
    }

    public float getHeight() {
        if(isNewLine){
            return this.height;
        }
        return getBottom() - getTop();
    }



    public float getPointSize() {
        return pointSize;
    }


}