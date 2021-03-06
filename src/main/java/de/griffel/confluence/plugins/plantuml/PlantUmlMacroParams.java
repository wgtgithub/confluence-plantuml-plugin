/*
 * Copyright (C) 2011 Michael Griffel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This distribution includes other third-party libraries.
 * These libraries and their corresponding licenses (where different
 * from the GNU General Public License) are enumerated below.
 *
 * PlantUML is a Open-Source tool in Java to draw UML Diagram.
 * The software is developed by Arnaud Roques at
 * http://plantuml.sourceforge.org.
 */
package de.griffel.confluence.plugins.plantuml;

import com.atlassian.renderer.RenderContext;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.core.DiagramType;

import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Supported PlantUML Macro parameters.
 */
public final class PlantUmlMacroParams {

   public enum Param {
      title, type, width, border, align, hspace, vspace, format, dropshadow, separation, exportName, debug;
   }

   public enum Alignment {
      none(""), left("float: left;"), center("display: block; text-align: center;"), right("float: right;");
      private final String cssStyle;

      private Alignment(String cssStyle) {
         this.cssStyle = cssStyle;
      }

      public String getCssStyle() {
         return cssStyle;
      }

      public static Alignment getDefault() {
         return none;
      }
   }

   @SuppressWarnings("rawtypes")
   private final Map params;

   @SuppressWarnings("rawtypes")
   public PlantUmlMacroParams(Map params) {
      this.params = params != null ? params : Collections.EMPTY_MAP;
   }

   public String getTitle() {
      return get(Param.title);
   }

   public int getBorder() {
      final String border = get(Param.border);
      return border != null ? Integer.valueOf(border) : 0;
   }

   public int getHspace() {
      final String hspace = get(Param.hspace);
      return hspace != null ? Integer.valueOf(hspace) : 0;
   }

   public int getVspace() {
      final String vspace = get(Param.vspace);
      return vspace != null ? Integer.valueOf(vspace) : 0;
   }

   public String getWidth() {
      final String width = get(Param.width);
      return width;
   }

   public String getImageStyle() {
      final StringBuilder sb = new StringBuilder();
      sb.append(" style=\"");
      if (getBorder() > 0) {
         sb.append("border:").append(getBorder()).append("px solid black;");
      }
      if (getHspace() > 0 || getVspace() > 0) {
         sb.append("margin:").append(getVspace()).append("px ").append(getHspace()).append("px;");
      }
      if (!Strings.isNullOrEmpty(getWidth())) {
         sb.append("width:").append(getWidth());
      }
      sb.append("\" ");
      return sb.toString();
   }

   public Alignment getAlignment() {
      final String align = get(Param.align);
      try {
         return align != null ? Alignment.valueOf(align) : Alignment.getDefault();
      } catch (IllegalArgumentException e) {
         return Alignment.getDefault();
      }
   }

   public DiagramType getDiagramType() {
      /* final */DiagramType result;
      try {
         final String type = get(Param.type);
         result = Iterators.find(Iterators.forArray(DiagramType.values()), new Predicate<DiagramType>() {
            public boolean apply(DiagramType diagramType) {
               return diagramType.name().equalsIgnoreCase(type);
            }
         });
      } catch (NoSuchElementException e) {
         result = DiagramType.UML; /* default type */
      }
      return result;
   }

   /**
    * Macro parameter FileFormat will be ignored if SVG is not supported.
    * @param renderContext
    * @return
    */
   public FileFormat getFileFormat(RenderContext renderContext) {
      final String format = get(Param.format);
      FileFormat result = FileFormat.PNG;
      try {
         if (isSvgSupported(renderContext)) {
             result = (format != null) ? FileFormat.valueOf(format) : FileFormat.PNG;
         }
      } catch (IllegalArgumentException e) {
         result = FileFormat.PNG;
      }
      return result;
   }

   public boolean getDropShadow() {
      final String dropShadow = get(Param.dropshadow);
      return dropShadow != null ? Boolean.valueOf(dropShadow) : true;
   }

   public boolean getSeparation() {
      final String separation = get(Param.separation);
      return separation != null ? Boolean.valueOf(separation) : true;
   }

   public String getExportName() {
      return get(Param.exportName);
   }

   public boolean isDebug() {
      final String debug = get(Param.debug);
      return debug != null ? Boolean.valueOf(debug) : false;
   }

   @Override
   public String toString() {
      return "PlantUmlMacroParams [_params=" + params + "]";
   }

   private String get(Param param) {
      return (String) params.get(param.name());
   }

   private boolean isSvgSupported(RenderContext renderContext) {
        if (RenderContext.DISPLAY.equals(renderContext.getOutputType())) {
            return true;
        } else if (RenderContext.EMAIL.equals(renderContext.getOutputType())) {
            return false;
        } else if (RenderContext.FEED.equals(renderContext.getOutputType())) {
            return true;
        } else if (RenderContext.HTML_EXPORT.equals(renderContext.getOutputType())) {
            return false;
        } else if (RenderContext.PDF.equals(renderContext.getOutputType())) {
            return false;
        } else if (RenderContext.PREVIEW.equals(renderContext.getOutputType())) {
            return true;
        } else if (RenderContext.WORD.equals(renderContext.getOutputType())) {
            return false;
        } else {
            return false; // future types
        }
    }
}
