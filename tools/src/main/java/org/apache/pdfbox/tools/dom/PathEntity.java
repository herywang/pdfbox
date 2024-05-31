/**
 * SubPath.java
 * (c) Radek Burget, 2015
 * <p>
 * Pdf2Dom is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Pdf2Dom is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with CSSBox. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Created on 9.9.2015, 15:27:27 by burgetr
 */
package org.apache.pdfbox.tools.dom;

/**
 * @author burgetr
 */
public class PathEntity {
    private float x1, y1, x2, y2;
    private float width, height;
    private boolean horizontal, vertical;


    public PathEntity(float x1, float y1, float x2, float y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.width = Math.abs(x2 - x1);
        this.height = Math.abs(y2 - y1);
        this.horizontal = (height < 0.5f);
        this.vertical = (width < 0.5f);
    }

    public float getX1() {
        return x1;
    }

    public void setX1(float x1) {
        this.x1 = x1;
    }

    public float getY1() {
        return y1;
    }

    public void setY1(float y1) {
        this.y1 = y1;
    }

    public float getX2() {
        return x2;
    }

    public void setX2(float x2) {
        this.x2 = x2;
    }

    public float getY2() {
        return y2;
    }

    public void setY2(float y2) {
        this.y2 = y2;
    }

    public float getLeft() {
        if (horizontal || vertical) {
            return Math.min(x1, x2);
        } else {
            return Math.abs((x2 + x1) / 2) - getWidth() / 2;
        }
    }

    public float getTop() {
        if(this.horizontal || this.vertical){
            return Math.min(y1, y2);
        }
        return Math.abs((y2 + y1) / 2) - getHeight() / 2;
    }

    public float getWidth() {
        if (this.vertical) {
            return 0;
        }
        if (this.horizontal) {
            return width;
        }
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    public float getHeight() {
        return this.vertical ? height : 0;
    }

    public String getBorderSide() {
        return vertical ? "border-right" : "border-bottom";
    }

    public float getLineStrokeWidth(float defaultLineWidth) {
        float lw = defaultLineWidth;
        if (lw < 0.5f) {
            lw = 0.5f;
        }
        return lw;
    }

    /**
     * 获取旋转角度
     */
    public double getAngleDegrees() {
        if (horizontal || vertical) {
            return 0;
        } else {
            return Math.toDegrees(Math.atan((y2 - y1) / (x2 - x1)));
        }
    }

}
